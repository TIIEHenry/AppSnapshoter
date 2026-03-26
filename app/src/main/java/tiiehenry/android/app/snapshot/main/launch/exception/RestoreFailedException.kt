package tiiehenry.android.app.snapshot.main.launch.exception

import tiiehenry.android.app.snapshot.data.bean.MetaDataItem

class RestoreFailedException(val dataItem: MetaDataItem, val state: String?) :
    Exception("$state") {
}