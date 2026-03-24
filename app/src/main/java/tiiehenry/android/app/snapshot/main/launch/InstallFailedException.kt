package tiiehenry.android.app.snapshot.main.launch

import tiiehenry.android.app.snapshot.data.MetaDataItem

class InstallFailedException(val dataItem: MetaDataItem, val path: String) : Exception(path) {
}