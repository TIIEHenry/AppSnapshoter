package tiiehenry.android.app.snapshot.external

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.ActivityInstanceConfirmBinding
import tiiehenry.android.app.snapshot.instance.InstanceStore
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.ItemProgressDialog

class InstanceConfirmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityInstanceConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val id = intent.getStringExtra(InstanceConfirmations.EXTRA_ID).orEmpty()
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
            InstanceConfirmations.take(id)
            InstanceGateway.complete(id, "error", getString(R.string.instance_confirm_cancelled), ExternalAccess.CANCELLED)
            InstanceConfirmations.dismiss(this, id)
            finish()
        }
        binding.btnConfirm.setOnClickListener {
            val job = InstanceConfirmations.take(id) ?: return@setOnClickListener
            binding.btnConfirm.isEnabled = false
            binding.btnCancel.isEnabled = false
            val progress = ItemProgressDialog(this)
            progress.setItemMessage(getString(R.string.instance_progress_restore, job.instanceName))
            progress.showItem()
            lifecycleScope.launch {
                try {
                    val message = InstanceStore(this@InstanceConfirmActivity, job.card, progress).restore(job.instanceId)
                    InstanceGateway.complete(id, "done", message)
                } catch (e: Exception) {
                    InstanceGateway.complete(id, "error", e.message ?: "", ExternalAccess.FAILED)
                } finally {
                    progress.dismissItem()
                    InstanceConfirmations.dismiss(this@InstanceConfirmActivity, id)
                    finish()
                }
            }
        }
    }
}
