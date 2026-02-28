package tiiehenry.android.app.snapshotor.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.databinding.DialogLoadingBinding

/**
 * 简单的Loading对话框
 */
class LoadingDialog(context: Context) : AlertDialog(context, R.style.LoadingDialogStyle) {
    
    private val binding: DialogLoadingBinding
    
    init {
        binding = DialogLoadingBinding.inflate(LayoutInflater.from(context))
        setView(binding.root)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }
    
    fun setMessage(message: String) {
        binding.messageText.text = message
    }
    
    fun setProgress(progress: Int) {
        binding.progressBar.progress = progress
    }
    
    fun setCurrentItem(item: String) {
        binding.currentItemText.text = item
        binding.currentItemText.visibility = if (item.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    override fun show() {
        try {
            super.show()
        } catch (e: WindowManager.BadTokenException) {
            // 忽略窗口token异常
        }
    }

    fun post(runnable: Runnable) {
        binding.root.post(runnable)
    }
    
    override fun dismiss() {
        try {
            super.dismiss()
        } catch (e: Exception) {
            // 忽略dismiss异常
        }
    }
}