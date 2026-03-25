package tiiehenry.android.app.snapshot.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.WindowManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.DialogLoadingBinding

/**
 * 简单的Loading对话框
 */
open class AbstractLoadingDialog(
    context: Context,
    private val itemBinding: DialogLoadingBinding,
    val root:View=itemBinding.root
) : AlertDialog(context, R.style.LoadingDialogStyle), ILoadingDialog {

    init {
        setView(root)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    override fun setItemMessage(message: String) {
        itemBinding.messageText.text = message
    }


    override fun setItemStatus(message: String) {
        itemBinding.statusText.text = message
    }

    override fun setItemProgress(progress: Int) {
        itemBinding.progressBar.progress = progress
    }

    override fun setCurrentItem(item: String) {
        itemBinding.currentItemText.text = item
        itemBinding.currentItemText.visibility =
            if (item.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun showItem() {
        try {
            super.show()
        } catch (e: WindowManager.BadTokenException) {
            // 忽略窗口token异常
        }
    }

    override fun post(runnable: Runnable) {
        itemBinding.root.post(runnable)
    }

    override fun dismissItem() {
        try {
            super.dismiss()
        } catch (e: Exception) {
            // 忽略dismiss异常
        }
    }

    override fun setItemException(e: Exception) {
        setItemMessage("出现错误")
        setItemStatus((e.javaClass.simpleName + "[" + e.message + "]"))
        itemBinding.statusText.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("错误信息")
                .setMessage(e.stackTraceToString())
                .setPositiveButton("确定") { dialog, which -> dialog.dismiss() }
                .show()
        }
        setCancelable(true)
    }
}