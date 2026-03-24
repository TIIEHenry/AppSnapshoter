package tiiehenry.android.app.snapshot.main.launch

import tiiehenry.android.app.snapshot.data.MetaDataItem

class RestoreFailedException(val dataItem: MetaDataItem, val state: String?) :
    Exception("$state") {
}