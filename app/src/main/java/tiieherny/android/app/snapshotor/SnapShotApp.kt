package tiieherny.android.app.snapshotor

import android.app.Application
import android.content.Context
import com.tencent.mmkv.MMKV

class SnapShotApp : Application() {

    lateinit var mmkv: MMKV
        private set
    
    lateinit var shotViewModel: SnapShotViewModel
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

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
