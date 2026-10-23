package tiiehenry.android.app.snapshot.repository

/** Delete the captured directory before removing the registration that stores its path. */
internal fun removeRegisteredGroup(
    path: String,
    deleteFiles: Boolean,
    exists: (String) -> Boolean,
    delete: (String) -> Boolean,
    unregister: () -> Unit,
): Boolean {
    if (deleteFiles && exists(path) && !delete(path)) return false
    unregister()
    return true
}
