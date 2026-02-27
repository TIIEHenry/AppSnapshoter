package tiiehenry.android.app.snapshotor

import android.app.Application
import android.content.Context
import android.os.Environment
import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshotor.utils.ShellHelper
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem

import tiiehenry.android.snapshotor.sync.IDataSyncer
import java.io.File

class SnapShotApp : Application() {

    lateinit var defaultRootPath: String

    lateinit var mmkv: MMKV
        private set

    lateinit var shotViewModel: SnapShotViewModel
        private set

    lateinit var fileSystem: IFileSystem
    lateinit var appManager: IAppManager
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

    }

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
