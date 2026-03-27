package tiiehenry.android.app.snapshot.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import tiiehenry.android.app.snapshot.app.AppInfo

@GlideModule
class SnapShotGlideModule : AppGlideModule() {
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.prepend(AppInfo::class.java, Bitmap::class.java, AppInfoModelLoader.Factory())
    }
    
    override fun isManifestParsingEnabled(): Boolean = false
}
