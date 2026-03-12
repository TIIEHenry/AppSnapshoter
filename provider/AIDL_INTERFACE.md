# IAppManageRootService AIDL 接口说明

## 概述
IAppManageRootService 接口已转换为 AIDL 格式，用于跨进程通信的根服务管理。

## 文件结构
```
src/main/aidl/
├── tiiehenry/android/snapshot/provider/appmanager/
│   ├── parcelables/
│   │   ├── BytesParcelable.aidl
│   │   ├── FilePathParcelable.aidl
│   │   └── StatFsParcelable.aidl
│   └── service/
│       └── IAppManageRootService.aidl
```

## 主要接口方法

### 应用管理相关
- `getInstalledAppInfos()`: 获取已安装应用信息
- `getInstalledAppStorages()`: 获取应用存储信息
- `getUsers()`: 获取系统用户列表

### 文件系统操作
- `readStatFs(String path)`: 读取文件系统统计信息
- `listFilePaths(String path, boolean listFiles, boolean listDirs)`: 列出文件路径
- `readText(String path)`: 读取文本文件
- `writeText(String path, ParcelFileDescriptor pfd)`: 写入文本文件
- `calculateTreeSize(String path)`: 计算目录树大小
- `mkdirs(String path)`: 创建目录
- `exists(String path)`: 检查路径是否存在
- `deleteRecursively(String path)`: 递归删除
- `copyRecursively(String source, String target, boolean overwrite)`: 递归复制

### 系统操作
- `testConnection()`: 测试连接
- `callTarCli(String stdOut, String stdErr, String[] argv)`: 调用 tar 命令
- `getPackageSourceDir(String packageName, int userId)`: 获取包源目录
- `compress(int level, String inputPath, String outputPath, ICallback callback)`: 压缩文件

### 网络相关（保留但未实现）
- `getPrivilegedConfiguredNetworks()`: 获取特权网络配置
- `addNetworks(List<BytesParcelable> configs)`: 添加网络配置

## 使用示例

### 服务端实现
```kotlin
class AppManageRootServiceImpl : IAppManageRootService.Stub() {
    override fun testConnection() {
        // 实现连接测试逻辑
    }
    
    override fun getInstalledAppInfos(): ParcelFileDescriptor {
        // 实现获取应用信息逻辑
    }
    
    // ... 其他方法实现
}
```

### 客户端调用
```kotlin
val service = IAppManageRootService.Stub.asInterface(binder)
val appInfos = service.getInstalledAppInfos()
```

## 注意事项
1. 所有 Parcelable 对象已转换为 AIDL parcelable 声明
2. 接口方法参数和返回值类型已适配 AIDL 语法
3. 保留了原有的回调接口 ICallback
4. 需要通过 AIDL 编译器生成实际的 Stub 和 Proxy 类