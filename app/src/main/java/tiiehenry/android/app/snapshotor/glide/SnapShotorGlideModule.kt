package tiiehenry.android.app.snapshotor.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import tiiehenry.android.app.snapshotor.app.AppInfo

@GlideModule
class SnapShotorGlideModule : AppGlideModule() {
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.prepend(AppInfo::class.java, Bitmap::class.java, AppInfoModelLoader.Factory())
    }
    
    override fun isManifestParsingEnabled(): Boolean = false
}
