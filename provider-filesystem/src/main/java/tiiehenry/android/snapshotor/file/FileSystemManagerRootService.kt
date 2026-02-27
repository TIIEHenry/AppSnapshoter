package tiiehenry.android.snapshotor.file

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager

class FileSystemManagerRootService : RootService() {
    override fun onBind(intent: Intent): IBinder {
        return FileSystemManager.getService()
    }
}