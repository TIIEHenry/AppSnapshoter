package tiiehenry.android.app.snapshot.main.launch.exception

import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem

class InstallFailedException(val dataItem: MetaDataItem, val path: String) : Exception(path) {
}