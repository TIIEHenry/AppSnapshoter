package tiiehenry.android.app.snapshot.main.launch

import tiiehenry.android.app.snapshot.data.MetaDataItem

class MissingUidException(val dataItem: MetaDataItem, val packageName: String) :
    Exception(packageName) {
}