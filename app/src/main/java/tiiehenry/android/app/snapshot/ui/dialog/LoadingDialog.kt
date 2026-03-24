package tiiehenry.android.app.snapshot.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.DialogLoadingBinding

/**
 * 简单的Loading对话框
 */
class LoadingDialog(context: Context) : AlertDialog(context, R.style.LoadingDialogStyle) {

    private val binding: DialogLoadingBinding =
        DialogLoadingBinding.inflate(LayoutInflater.from(context))

    init {
        setView(binding.root)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    fun setMessage(message: String) {
        binding.messageText.text = message
    }


    fun setStatus(message: String) {
        binding.statusText.text = message
    }

    fun setProgress(progress: Int) {
        binding.progressBar.progress = progress
    }

    fun setCurrentItem(item: String) {
        binding.currentItemText.text = item
        binding.currentItemText.visibility =
            if (item.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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

    fun setException(e: Exception) {
        setMessage("出现错误")
        setStatus((e.javaClass.simpleName + "[" + e.message + "]"))
        binding.statusText.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("错误信息")
                .setMessage(e.stackTraceToString())
                .setPositiveButton("确定") { dialog, which -> dialog.dismiss() }
                .show()
        }
        setCancelable(true)
    }
}