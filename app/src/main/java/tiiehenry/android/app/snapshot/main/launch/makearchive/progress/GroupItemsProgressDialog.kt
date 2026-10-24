package tiiehenry.android.app.snapshot.main.launch.makearchive.progress

import android.content.Context
import android.view.LayoutInflater
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.DialogMultiItemLoadingBinding
import java.util.concurrent.atomic.AtomicBoolean

class GroupItemsProgressDialog(
    context: Context,
    private val binding
    : DialogMultiItemLoadingBinding = DialogMultiItemLoadingBinding.inflate(
        LayoutInflater.from(context)
    )
) : AbstractProgressDialog(
    context,
    binding.singleLoadingDialog,
    binding.root
) {
    private val cancelPolicy = GroupItemsCancelPolicy()
    private var failListener: (() -> Unit)? = null
    private var successListener: (() -> Unit)? = null
    private var successLongClickListener: (() -> Unit)? = null

    init {
        binding.btnCancel.setOnClickListener {
            cancelPolicy.invokeStopListener()
        }
        binding.btnFail.setOnClickListener {
            failListener?.invoke()
        }
        binding.btnSuccess.setOnClickListener {
            successListener?.invoke()
        }
        binding.btnSuccess.setOnLongClickListener {
            successLongClickListener?.invoke()
            true
        }
    }

    fun setOnCancelListener(listener: () -> Unit) {
        cancelPolicy.setStopListener(listener)
    }

    override fun cancel() {
        when (groupItemsSystemCancelEffect(cancelPolicy.isCloseOnly())) {
            GroupItemsSystemCancelEffect.Dismiss -> super.cancel()
            GroupItemsSystemCancelEffect.Stop -> cancelPolicy.invokeStopListener()
        }
    }

    fun setOnFailListener(listener: () -> Unit) {
        failListener = listener
    }

    fun setOnSuccessListener(listener: () -> Unit) {
        successListener = listener
    }

    fun setOnSuccessLongClickListener(listener: () -> Unit) {
        successLongClickListener = listener
    }

    fun setFinishButtonAsClose(onClose: () -> Unit) {
        cancelPolicy.markClosedFinish()
        binding.btnCancel.setIconResource(R.drawable.close_circle_outline)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        binding.btnCancel.setOnClickListener {
            onClose()
        }
    }

    fun setFinishButtonAsForceCancel(onClose: () -> Unit) {
        binding.btnCancel.setIconResource(R.drawable.stop_circle_outline)
        binding.btnCancel.setOnClickListener {
            onClose()
        }
    }

    fun getMaxProgress(): Int {
        return binding.progressBarTotal.max
    }

    fun setLabel(label: String) {
        binding.tvCurrentLabel.text = label
    }

    fun setPackageName(packageName: String) {
        binding.tvPackage.text = packageName
    }

    fun setProgress(progress: Int) {
        binding.progressBarTotal.progress = progress
        binding.tvProgress.text = "${progress}/${binding.progressBarTotal.max}"
    }

    fun setTotalProgress(progress: Int) {
        binding.progressBarTotal.max = progress
    }

    override fun dismissItem() {

    }

    override fun showItem() {

    }

}

internal class GroupItemsCancelPolicy {
    private var stopListener: (() -> Unit)? = null
    private var closeOnly: Boolean = false

    fun setStopListener(listener: () -> Unit) {
        stopListener = listener
    }

    fun invokeStopListener() {
        stopListener?.invoke()
    }

    fun markClosedFinish() {
        closeOnly = true
    }

    fun isCloseOnly(): Boolean = closeOnly
}

internal enum class GroupItemsSystemCancelEffect {
    Stop,
    Dismiss
}

internal fun groupItemsSystemCancelEffect(closeOnly: Boolean): GroupItemsSystemCancelEffect =
    if (closeOnly) GroupItemsSystemCancelEffect.Dismiss else GroupItemsSystemCancelEffect.Stop

internal fun shouldStartNextBatchSnapshot(isCanceled: AtomicBoolean): Boolean =
    !isCanceled.get()