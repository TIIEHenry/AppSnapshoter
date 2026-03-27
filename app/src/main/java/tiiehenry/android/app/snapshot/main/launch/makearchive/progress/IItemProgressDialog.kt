package tiiehenry.android.app.snapshot.main.launch.makearchive.progress

interface IItemProgressDialog {
    fun setItemMessage(message: String)
    fun setItemStatus(message: String)
    fun setItemProgress(progress: Int)
    fun setCurrentItem(item: String)
    fun showItem()
    fun post(runnable: Runnable)
    fun dismissItem()
    fun setItemException(e: Exception)
}