package tiiehenry.android.snapshotor.provider.filesystem

import android.app.Application

class App : Application() {
    companion object {
        lateinit var application: Application
    }

    override fun onCreate() {
        super.onCreate()
        application = this
    }
}