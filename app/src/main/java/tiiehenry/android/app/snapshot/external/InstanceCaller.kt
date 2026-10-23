package tiiehenry.android.app.snapshot.external

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import java.security.MessageDigest

object InstanceCaller {
    fun fromUid(context: Context, uid: Int): CallerId? {
        val name = context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return null
        val cert = certSha256(context.packageManager, name)
        if (cert.isEmpty()) return null
        return CallerId(name, cert)
    }

    fun fromBinder(context: Context): CallerId? = fromUid(context, Binder.getCallingUid())

    fun certSha256(packageManager: PackageManager, packageName: String): String {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                val info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val bytes = signatures?.firstOrNull()?.toByteArray() ?: return ""
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
