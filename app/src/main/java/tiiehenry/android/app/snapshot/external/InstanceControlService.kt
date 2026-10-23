package tiiehenry.android.app.snapshot.external

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tiiehenry.android.snapshot.instance.IInstanceControl

class InstanceControlService : Service() {
    override fun onBind(intent: Intent?): IBinder = InstanceControlBinder(this)
}

class InstanceAppFunctionService : Service() {
    override fun onBind(intent: Intent?): IBinder = InstanceControlBinder(this)
}

class InstanceControlBinder(private val context: android.content.Context) : IInstanceControl.Stub() {
    override fun call(requestJson: String): String {
        val caller = InstanceCaller.fromBinder(context)
        return InstanceGateway(context).call(caller, requestJson)
    }
}
