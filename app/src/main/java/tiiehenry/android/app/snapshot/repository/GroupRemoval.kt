package tiiehenry.android.app.snapshot.repository

/** Delete the captured directory before removing the registration that stores its path. */
internal fun removeRegisteredGroup(
    path: String,
    deleteFiles: Boolean,
    delete: (String) -> Boolean,
    unregister: () -> Unit,
): Boolean {
    if (deleteFiles && !delete(path)) return false
    unregister()
    return true
}
