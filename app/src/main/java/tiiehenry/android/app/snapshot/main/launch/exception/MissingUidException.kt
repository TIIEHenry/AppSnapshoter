package tiiehenry.android.app.snapshot.main.launch.exception

import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem

class MissingUidException(val dataItem: MetaDataItem, val packageName: String) :
    Exception(packageName) {
}