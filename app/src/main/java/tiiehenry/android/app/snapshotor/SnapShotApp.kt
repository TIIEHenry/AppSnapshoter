package tiiehenry.android.app.snapshotor

import android.app.Application
import android.content.Context
import android.os.Environment
import com.tencent.mmkv.MMKV
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.provider.appmanager.AppManagerProviderImpl
import tiiehenry.android.snapshotor.provider.datasyncer.DataSyncerProviderImpl
import tiiehenry.android.snapshotor.provider.filesystem.FileSystemProviderImpl
import tiiehenry.android.snapshotor.sync.IDataSyncer
import java.io.File

class SnapShotApp : Application() {

    lateinit var  defaultRootPath: String

    lateinit var mmkv: MMKV
        private set

    lateinit var shotViewModel: SnapShotViewModel
        private set

    val fileSystem: IFileSystem by lazy {
        val context = SnapShotApp.getContext()
        FileSystemProviderImpl(context, context).provide()
    }
    val appManager: IAppManager by lazy {
        val context = SnapShotApp.getContext()
        AppManagerProviderImpl(context, context).provide()
    }
    val dataSyncer: IDataSyncer by lazy {
        val context = SnapShotApp.getContext()
        DataSyncerProviderImpl(context, context).provide()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        defaultRootPath= File(Environment.getExternalStorageDirectory(), "SnapShotApp").absolutePath
        // 初始化MMKV
        MMKV.initialize(this)
        mmkv = MMKV.defaultMMKV()

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
