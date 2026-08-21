package tiiehenry.android.app.snapshot.main.launch

import android.content.Context
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.repository.PathRegistrationResult

fun PathRegistrationResult.userMessage(context: Context): String? = when (this) {
    is PathRegistrationResult.Ok -> null
    PathRegistrationResult.OccupiedByGroup ->
        context.getString(R.string.error_path_occupied_by_group)
    PathRegistrationResult.OccupiedBySet ->
        context.getString(R.string.error_path_occupied_by_set)
    is PathRegistrationResult.Error ->
        context.getString(R.string.error_path_registration_failed, message)
}
