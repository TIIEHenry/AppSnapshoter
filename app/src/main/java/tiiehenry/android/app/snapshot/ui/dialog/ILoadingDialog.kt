package tiiehenry.android.app.snapshot.ui.dialog

interface ILoadingDialog {
    fun setItemMessage(message: String)
    fun setItemStatus(message: String)
    fun setItemProgress(progress: Int)
    fun setCurrentItem(item: String)
    fun showItem()
    fun post(runnable: Runnable)
    fun dismissItem()
    fun setItemException(e: Exception)
}