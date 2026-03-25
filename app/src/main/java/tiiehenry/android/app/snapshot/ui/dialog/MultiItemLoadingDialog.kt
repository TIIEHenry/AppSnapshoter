package tiiehenry.android.app.snapshot.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import tiiehenry.android.app.snapshot.databinding.DialogMultiItemLoadingBinding

class MultiItemLoadingDialog(
    context: Context,
    private val binding
    : DialogMultiItemLoadingBinding = DialogMultiItemLoadingBinding.inflate(
        LayoutInflater.from(context)
    )
) : AbstractLoadingDialog(
    context,
    binding.singleLoadingDialog,
    binding.root
) {
    private var cancelListener: (() -> Unit)? = null
    private var failListener: (() -> Unit)? = null
    private var successListener: (() -> Unit)? = null
    private var successLongClickListener: (() -> Unit)? = null

    init {
        binding.btnCancel.setOnClickListener {
            cancelListener?.invoke()
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
        cancelListener = listener
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
        binding.btnCancel.text = "关闭"
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        binding.btnCancel.setOnClickListener {
            onClose()
        }
    }

    fun setFinishButtonAsForceCancel(onClose: () -> Unit) {
        binding.btnCancel.text = "强行停止"
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