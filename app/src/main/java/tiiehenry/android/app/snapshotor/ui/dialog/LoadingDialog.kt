package tiiehenry.android.app.snapshotor.ui.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import tiiehenry.android.app.snapshotor.R

/**
 * 简单的Loading对话框
 */
class LoadingDialog(context: Context) : Dialog(context, R.style.LoadingDialogStyle) {
    
    private val progressBar: ProgressBar
    private val messageText: TextView
    
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        setContentView(view)
        
        progressBar = view.findViewById(R.id.progressBar)
        messageText = view.findViewById(R.id.messageText)
        
        // 设置对话框属性
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.3f)
        }
        
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }
    
    fun setMessage(message: String) {
        messageText.text = message
    }
    
    fun setProgress(progress: Int) {
        progressBar.progress = progress
    }
    
    override fun show() {
        try {
            super.show()
        } catch (e: WindowManager.BadTokenException) {
            // 忽略窗口token异常
        }
    }
    
    override fun dismiss() {
        try {
            super.dismiss()
        } catch (e: Exception) {
            // 忽略dismiss异常
        }
    }
}