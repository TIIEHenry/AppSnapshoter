# IServiceClient 竞态条件修复

## 问题描述

`checkRootPermission` 检查时间过长，日志显示服务连接成功后仍然超时：

```
16:07:31.910  onServiceConnected: ComponentInfo{...SnapShotRootService}
16:07:46.914  Timeout waiting for service binding: SnapShotRootServiceClient@ad6fb61
```

从 `onServiceConnected` 被调用到超时，中间间隔约 15 秒（`BIND_TIMEOUT` 的值）。

## 根本原因

`IServiceClient` 中存在竞态条件：

### 问题代码流程

1. **主线程**调用 `fetchRemote()`：
   - 创建新的 `clientFuture`
   - 调用 `RootService.bind()` 开始异步绑定

2. **IO 线程**调用 `waitFetch()`：
   - 执行 `clientFuture.get(15, SECONDS)` 阻塞等待

3. **RootService 回调线程**执行 `onServiceConnected()`：
   - 执行 `future.complete(i)` 完成 future
   - 设置 `client = i`

4. **问题发生在 `waitFetch` 的 `finally` 块**：
   ```java
   finally {
       clientFuture = new CompletableFuture<>();  // 重置 future
   }
   ```

### 竞态条件场景

```
时间线：
T0: fetchRemote() 创建 clientFuture_1
T1: waitFetch() 开始等待 clientFuture_1.get()
T2: onServiceConnected() 被调用
T3: waitFetch() 超时（或即将返回）
T4: finally 块执行 clientFuture = new CompletableFuture<>()
T5: onServiceConnected() 执行 future.complete(i)
    - 但此时 future 变量可能已经被重置
    - complete() 完成的是旧的 clientFuture_1
    - 而 waitFetch 已经返回了 null
```

### 核心问题

`RootService.bind()` 的回调可能在非主线程执行，导致时序不可预测。当 `onServiceConnected` 执行 `future.complete(i)` 时，`waitFetch` 可能已经超时并重置了 `clientFuture`。

## 修复方案

### 1. 移除 finally 块中的 future 重置

```java
// 修改前
finally {
    clientFuture = new CompletableFuture<>();
}

// 修改后
finally {
    // 不要在这里重置 future，因为 onServiceConnected 可能还在使用它
    // 如果服务已经连接成功，client 不为 null，后续调用可以通过 isConnected() 检测到
    // clientFuture = new CompletableFuture<>();
}
```

### 2. 超时后检查 client 是否已连接

即使 `clientFuture.get()` 超时，`onServiceConnected` 可能已经设置了 `client`，此时应该返回成功：

```java
// 修改前
catch (TimeoutException e) {
    Log.e(getLogTag(), "Timeout waiting for service binding: " + this);
    if (!isConnected()) {
        // unbindService(context);
    }
}

// 修改后
catch (TimeoutException e) {
    Log.w(getLogTag(), "Timeout waiting for service binding, checking if connected: " + this);
    // 超时后检查服务是否已经连接成功
    // onServiceConnected 可能在超时后但返回前完成了
    I cachedClient = client;
    if (cachedClient != null && cachedClient.asBinder().isBinderAlive() && cachedClient.asBinder().pingBinder()) {
        Log.i(getLogTag(), "Service connected after timeout, returning client");
        return cachedClient;
    }
    Log.e(getLogTag(), "Service not connected after timeout");
}
```

## 修复效果

1. **避免竞态条件**：不再在 `finally` 块中重置 `clientFuture`，确保 `onServiceConnected` 完成的是正确的 future 对象

2. **容错处理**：即使 `CompletableFuture` 的时序出现问题，只要 `onServiceConnected` 已经成功设置了 `client`，`waitFetch` 也能正确返回服务实例

3. **更好的日志**：将超时日志级别从 `ERROR` 改为 `WARN`，并在检测到连接成功后输出 `INFO` 级别日志

## 相关文件

- `api/src/main/java/tiiehenry/android/snapshot/app/IServiceClient.java` - 核心修复文件
- `provider-appmanager/src/main/java/tiiehenry/android/snapshot/provider/appmanager/root/SnapShotRootServiceClient.java` - RootService 客户端实现
