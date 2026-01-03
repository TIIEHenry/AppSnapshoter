package tiieherny.android.app.snapshotor.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import tiieherny.android.app.snapshotor.app.AppInfo

class AppInfoModelLoader : ModelLoader<AppInfo, Bitmap> {
    
    override fun buildLoadData(
        model: AppInfo,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(
            ObjectKey(model.packageName),
            AppInfoDataFetcher(model)
        )
    }
    
    override fun handles(model: AppInfo): Boolean = true
    
    class Factory : ModelLoaderFactory<AppInfo, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AppInfo, Bitmap> {
            return AppInfoModelLoader()
        }
        
        override fun teardown() {
            // No cleanup needed
        }
    }
}
