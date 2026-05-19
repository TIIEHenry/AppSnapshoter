package tiiehenry.android.app.snapshot

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.Shell
import tiiehenry.android.app.snapshot.utils.AppShell
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.provider.Providers
import tiiehenry.android.snapshot.provider.appmanager.ProvidersImpl

import java.io.File

class SnapshotApp : Application() {

    lateinit var globalRootPath: String

    lateinit var mmkv: MMKV
        private set

    lateinit var shotViewModel: SnapshotViewModel
        private set

    private lateinit var _providers: Providers

    val fileSystem: IFileSystem get() = _providers.fileSystem
    val appManager: IAppManager get() = _providers.appManager
    val packageManager: tiiehenry.android.snapshot.app.IPackageManager get() = _providers.packageManager
    val permissionManager: tiiehenry.android.snapshot.app.IPermissionManager get() = _providers.permissionManager

    override fun onCreate() {
        super.onCreate()
        instance = this

        globalRootPath =
            File(Environment.getExternalStorageDirectory(), "Android/snapshot").absolutePath
        // 初始化MMKV
        MMKV.initialize(this)
        mmkv = MMKV.defaultMMKV()

        AppShell.initMainShell(this)

        // 初始化全局ViewModel
        shotViewModel = SnapshotViewModel()

        // 初始化 Providers（统一管理 Service 和 Provider）
        _providers = ProvidersImpl(this)

        try {
            val isRoot = Shell.getShell().isRoot
            Log.i("SnapShotApp", "isRoot $isRoot")
            if (isRoot) {
                _providers.bindRootService()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取 Providers 实例
     */
    fun getProviders(): Providers = _providers

    companion object {
        private lateinit var instance: SnapshotApp

        @JvmStatic
        fun getInstance(): SnapshotApp {
            return instance
        }

        @JvmStatic
        fun getContext(): Context {
            return instance.applicationContext
        }

        @JvmStatic
        fun getViewModel(): SnapshotViewModel {
            return instance.shotViewModel
        }
    }
}
