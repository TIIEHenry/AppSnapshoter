package tiiehenry.android.app.snapshotor

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.Shell
import tiiehenry.android.app.snapshotor.utils.ShellHelper
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.provider.Providers
import tiiehenry.android.snapshotor.provider.appmanager.ProvidersImpl

import tiiehenry.android.snapshotor.sync.IDataSyncer
import java.io.File

class SnapShotApp : Application() {

    lateinit var defaultRootPath: String

    lateinit var mmkv: MMKV
        private set

    lateinit var shotViewModel: SnapShotViewModel
        private set

    private lateinit var _providers: Providers

    val fileSystem: IFileSystem get() = _providers.fileSystem
    val appManager: IAppManager get() = _providers.appManager

    lateinit var dataSyncer: IDataSyncer

    override fun onCreate() {
        super.onCreate()
        instance = this

        defaultRootPath =
            File(Environment.getExternalStorageDirectory(), "SnapShotApp").absolutePath
        // 初始化MMKV
        MMKV.initialize(this)
        mmkv = MMKV.defaultMMKV()

        ShellHelper.initMainShell(this)

        // 初始化全局ViewModel
        shotViewModel = SnapShotViewModel()

        // 初始化 Providers（统一管理 Service 和 Provider）
        _providers = ProvidersImpl(this)

        val isRoot = Shell.getShell().isRoot
        Log.i("SnapShotApp", "isRoot $isRoot")
        if (isRoot) {
            _providers.bindRootService()
        }
    }

    /**
     * 获取 Providers 实例
     */
    fun getProviders(): Providers = _providers

    companion object {
        private lateinit var instance: SnapShotApp

        @JvmStatic
        fun getInstance(): SnapShotApp {
            return instance
        }

        @JvmStatic
        fun getContext(): Context {
            return instance.applicationContext
        }

        @JvmStatic
        fun getViewModel(): SnapShotViewModel {
            return instance.shotViewModel
        }
    }
}
