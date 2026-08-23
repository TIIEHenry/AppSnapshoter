package tiiehenry.android.app.snapshot.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import tiiehenry.android.app.snapshot.R

object AppDetailsLauncher {
    fun open(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            } catch (ex: Exception) {
                Toast.makeText(context, R.string.archive_cannot_open_app_details, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}
