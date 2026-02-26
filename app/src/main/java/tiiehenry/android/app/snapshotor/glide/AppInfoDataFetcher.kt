package tiiehenry.android.app.snapshotor.glide

import android.graphics.Bitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import tiiehenry.android.app.snapshotor.app.AppInfo

class AppInfoDataFetcher(private val appInfo: AppInfo) : DataFetcher<Bitmap> {
    
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            // 直接使用 AppInfo 的 icon 属性，它已经是 Bitmap 类型
            callback.onDataReady(appInfo.icon)
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }
    
    override fun cleanup() {
        // Bitmap 的清理由 AppInfo 自己管理，这里不需要额外处理
    }
    
    override fun cancel() {
        // 由于是同步加载，不需要取消操作
    }
    
    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java
    
    override fun getDataSource(): DataSource = DataSource.LOCAL
}
