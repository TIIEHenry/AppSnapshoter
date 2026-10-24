package tiiehenry.android.app.snapshot.external

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.ActivityInstanceConfirmBinding
import tiiehenry.android.app.snapshot.instance.InstanceError
import tiiehenry.android.app.snapshot.instance.InstanceException
import tiiehenry.android.app.snapshot.instance.InstanceStore
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.ItemProgressDialog

class InstanceConfirmActivity : AppCompatActivity() {
    private var confirming = false
    private var pendingId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityInstanceConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val id = intent.getStringExtra(InstanceConfirmations.EXTRA_ID).orEmpty()
        pendingId = id
        val pending = InstanceConfirmations.peek(id)
        if (pending == null) {
            finish()
            return
        }
        val bound = if (pending.externalId.isEmpty()) pending.account else pending.externalId
        binding.confirmMessage.text = getString(
            R.string.instance_confirm_message,
            pending.packageName,
            pending.instanceName,
            bound.ifEmpty { pending.instanceId },
        )
        binding.btnCancel.setOnClickListener {
            cancelPending(id)
            finish()
        }
        binding.btnConfirm.setOnClickListener {
            val job = InstanceConfirmations.take(id) ?: return@setOnClickListener
            // A failed re-check must fail closed, not crash the confirm flow and
            // leave the caller polling an operation that can never finish.
            if (!runCatching { stillAllowed(job) }.getOrDefault(false)) {
                InstanceGateway.complete(id, "error", getString(R.string.instance_confirm_cancelled), ExternalAccess.CANCELLED)
                InstanceConfirmations.dismiss(this, id)
                finish()
                return@setOnClickListener
            }
            confirming = true
            binding.btnConfirm.isEnabled = false
            binding.btnCancel.isEnabled = false
            val progress = ItemProgressDialog(this)
            progress.setItemMessage(getString(R.string.instance_progress_restore, job.instanceName))
            progress.showItem()
            // The restore must survive Activity recreation; cancelling it midway can
            // leave the target app with partially restored data.
            val appContext = applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val message = InstanceStore(appContext, job.card, progress)
                        .restore(job.instanceId, job.externalId)
                    InstanceGateway.complete(id, "done", message)
                } catch (e: CancellationException) {
                    // This scope is not tied to the Activity, so cancellation is
                    // unexpected; do not leave the caller polling a dead operation.
                    InstanceGateway.complete(id, "error", "", ExternalAccess.CANCELLED)
                    throw e
                } catch (e: InstanceException) {
                    val code = when (e.code) {
                        InstanceError.BUSY -> ExternalAccess.BUSY
                        InstanceError.NOT_FOUND -> ExternalAccess.INSTANCE_NOT_FOUND
                        else -> ExternalAccess.FAILED
                    }
                    InstanceGateway.complete(id, "error", e.message ?: "", code)
                } catch (e: Exception) {
                    InstanceGateway.complete(
                        id, "error", appContext.getString(R.string.instance_restore_failed), ExternalAccess.FAILED,
                    )
                } finally {
                    InstanceConfirmations.dismiss(appContext, id)
                    runOnUiThread {
                        runCatching { progress.dismissItem() }
                            .onFailure { Log.w("InstanceConfirm", "dialog dismiss failed", it) }
                        if (!isFinishing && !isDestroyed) finish()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (confirming) return
                cancelPending(pendingId)
                finish()
            }
        })
    }

    private fun cancelPending(id: String) {
        InstanceConfirmations.cancel(
            id, "error", getString(R.string.instance_confirm_cancelled), ExternalAccess.CANCELLED,
        )
        InstanceConfirmations.dismiss(this, id)
    }

    override fun onDestroy() {
        if (!confirming && !isChangingConfigurations) {
            cancelPending(pendingId)
        }
        super.onDestroy()
    }

    private fun stillAllowed(job: InstanceConfirmations.Pending): Boolean {
        val state = ExternalAccessStore(this).read()
        // The approval is bound to a certificate. Re-read it at confirm time so
        // an app that changed its signing key cannot reuse an old grant.
        val current = InstanceCaller.certSha256(packageManager, job.caller.packageName)
        return ExternalAccess.isCallerValid(state, job.caller, job.packageName, current)
    }
}
