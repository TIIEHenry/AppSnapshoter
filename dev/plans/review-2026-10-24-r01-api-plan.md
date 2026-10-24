---
title: "r01 api 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "修正 IServiceClient 的解绑对象、死亡登记和等待结果，避免交出已失效的客户端。"
---

# r01 api 修改方案

## r01-1
- 判定：hold
- 问题类：释放时解绑的不是当初交给 bindService 的那一对 Context 与 ServiceConnection。
- 不变量：每次成功的 bindService 都用 doBind 返回的那个 ServiceConnection，以及当时传入的同一个 Context 实例解绑；client 已经为空时，只要这对记录还在，释放仍会解绑。
- 要动的符号或文件：`api/src/main/java/tiiehenry/android/snapshot/app/IServiceClient.java` 的 `fetchRemote`、`bindRemote`、`doBind`、`releaseClient`、`unbindService`。`fetchRemote` 继续用 `applicationContext` 调用 `bindRemote`。把 `doBind` 返回的 wrapper 和这个 Context 存到实例字段。`releaseClient` 用这对记录解绑，然后清除记录；尚无绑定记录且 client 为空时直接返回。再次绑定前若记录还在，先解绑旧的那对。`unbindService` 改为解绑保存的 wrapper。
- 明确不改什么：保持 `getFlag` 的 `BIND_AUTO_CREATE`、`BIND_ALLOW_ACTIVITY_STARTS`、`BIND_IMPORTANT` 组合。保持 `releaseClient` 与 `fetchRemote` 的公开签名。保持 `asInterface` 和 `getIntent`。
- 本地验证：应新增断言：`bindService` 收到的 Context 与 ServiceConnection 被保存；`releaseClient` 用同一对解绑一次；死亡回调已把 client 置空后 `releaseClient` 仍解绑；解绑后再次 `fetchRemote` 只留下一份新绑定，旧 wrapper 不再收到断连或死亡回调。

## r01-2
- 判定：hold
- 问题类：同一次连接把死亡回调登记了两次，回调也不核对是哪一个 binder。
- 不变量：一次成功连接只 `linkToDeath` 一次、只 `onConnected` 一次；`binderDied` 只清除登记时捕获的那个 binder，之后写入的新 client 保持原样。
- 要动的符号或文件：同一文件的 `fetchRemote`、`bindRemote` 内部 wrapper、字段 `connection`、`deathRecipient`。连接状态只由登记给 `bindService` 的 wrapper 写入：`asInterface`、设置 client、`onConnected`、`linkToDeath`、`complete`。字段 `connection` 的 `onServiceConnected` 去掉这五步。`linkToDeath` 使用登记时捕获该 `IBinder` 的接收者；`binderDied` 仅当 `client.asBinder()` 与捕获对象相同才 `unlinkToDeath` 并置空。`fetchRemote` 在未连接且 client 非空时，先对当时的接收者 `unlinkToDeath`，再把 client 置空。字段 `connection` 的 `onServiceDisconnected` 只保留一次 `onDisconnected` 观察者通知。
- 明确不改什么：保持 `IServiceRemoteObserver.onBinderDied` 返回 true 则保留、返回 false 则移除。保持 `bindRemote(Context, BindCallback)` 在连接成功后回调调用方。死亡处理仍留在 `IServiceClient` 内。
- 本地验证：应新增断言：一次 `onServiceConnected` 只 `linkToDeath` 一次且只 `onConnected` 一次；观察者返回 true 时，一次进程死亡只通知一次；旧 binder 的 `binderDied` 晚于新的 `onServiceConnected` 时，新 client 仍在并保留自己的死亡登记；`fetchRemote` 丢掉死 client 之前已经 `unlinkToDeath`。

## r01-3
- 判定：hold
- 问题类：断开后 clientFuture 仍是完成态，waitFetch 会立刻交出上一轮已经不可用的接口。
- 不变量：清空 client 时，当前 clientFuture 不再以旧实例作为完成结果；`waitFetch` 在调用 `onFetched` 和返回非空之前，该 binder 仍然 `isBinderAlive` 且 `pingBinder`。
- 要动的符号或文件：同一文件的 `deathRecipient.binderDied`、wrapper 的 `onServiceDisconnected`、`releaseClient`、`waitFetch`、字段 `clientFuture`。这三处清空 client 时，把当前 future `cancel` 或 `completeExceptionally`，再换成新的未完成 `CompletableFuture`。作废只发生一次，由 wrapper 与 `releaseClient` 负责，字段 `connection` 不改 `clientFuture`。`waitFetch` 在 `get` 得到实例后先做存活检查，失败则不调用 `onFetched`，也不返回该实例。
- 明确不改什么：保持 `onFetched` 的空实现和可覆盖性。保持 `fetchRemote` 在 `isConnected` 为 true 时返回 `completedFuture(client)` 的快路径。保持 `BIND_TIMEOUT` 的数值。
- 本地验证：应新增断言：成功连接后触发 `binderDied` 或 `onServiceDisconnected`，随后 `waitFetch` 不返回旧实例，也不对旧实例调用 `onFetched`；`releaseClient` 之后同样不能从旧 future 取出已释放的实例。

## r01-4
- 判定：hold
- 问题类：超时边界上已经连上时跳过 onFetched，等待失败时仍返回已判死的 client。
- 不变量：`waitFetch` 的每个非空返回都先经过 `onFetched`；超时且 ping 失败、取消、以及 `get` 的其它异常都返回 null。
- 要动的符号或文件：同一文件的 `waitFetch`。`get` 正常返回时维持先 `onFetched` 再返回。`TimeoutException` 分支里 `cachedClient` 仍能 `pingBinder` 时，同样先 `onFetched` 再返回该实例。ping 失败、`CancellationException` 和其它异常改为返回 null，不再使用方法末尾的 `return client`。
- 明确不改什么：保持超时时间为 `BIND_TIMEOUT` 秒。保持 `waitFetch` 拒绝主线程、`fetchRemote` 拒绝工作线程。
- 本地验证：应新增断言：超时后 binder 仍存活时 `onFetched` 被调用一次且返回该实例；超时后 ping 失败、future 被取消、`get` 抛出其它异常时返回值都是 null。

## r01-5
- 判定：hold
- 问题类：waitFetch 占着实例锁阻塞 get，主线程进不了 fetchRemote。
- 不变量：阻塞在 `clientFuture.get` 期间不持有 `fetchRemote` 使用的实例监视器；工作线程先进入 `waitFetch` 时，主线程仍能在超时前执行 `bindService` 并 complete 同一个尚未完成的 future。
- 要动的符号或文件：同一文件的 `waitFetch` 与 `fetchRemote`。`waitFetch` 在短临界区里读取 `clientFuture` 引用，在临界区外调用 `get(BIND_TIMEOUT, SECONDS)`。未完成的那个 future 继续由 `fetchRemote` 完成，等待期间不把它换成另一个对象。
- 明确不改什么：保持 `waitFetch` 只能在工作线程调用。保持 `clientLock` 保护 client 读写。保持线程池字段 `executor` 的现有配置。
- 本地验证：应新增断言：工作线程先调用 `waitFetch` 且 future 尚未完成时，主线程的 `fetchRemote` 能在超时内执行到 `bindService`；连接回调 complete 后 `waitFetch` 返回该 client。
