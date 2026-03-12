package tiiehenry.android.snapshot.provider;

import tiiehenry.android.snapshot.app.IAppManager;
import tiiehenry.android.snapshot.file.IFileSystem;

/**
 * 统一的 Provider 管理接口
 * 负责 SnapShotRootServiceClient 的初始化，并提供 IAppManager 和 IFileSystem 的访问
 */
public interface Providers {
    /**
     * 初始化所有 Provider
     * 在主线程调用，启动 Service 的异步绑定
     */
    void bindRootService();

    /**
     * 获取 IAppManager 实例
     * 必须在非主线程调用，会阻塞等待 Service 连接
     *
     * @return IAppManager 实例
     * @throws Exception 如果 Service 未初始化或连接失败
     */
    IAppManager getAppManager() throws Exception;

    /**
     * 获取 IFileSystem 实例
     * 必须在非主线程调用，会阻塞等待 Service 连接
     *
     * @return IFileSystem 实例
     * @throws Exception 如果 Service 未初始化或连接失败
     */
    IFileSystem getFileSystem() throws Exception;

    /**
     * 等待 RootService 连接完成
     * 必须在非主线程调用，会阻塞等待 Service 连接
     *
     * @return 是否连接成功
     */
    boolean waitForConnection();

}
