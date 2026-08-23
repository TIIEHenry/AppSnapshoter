package tiiehenry.android.app.snapshot.group

sealed class UninstallAppResult {
    data object Success : UninstallAppResult()
    data object Busy : UninstallAppResult()
    data object Failed : UninstallAppResult()
}
