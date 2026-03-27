package tiiehenry.android.app.snapshot.main.launch.exception

import tiiehenry.android.app.snapshot.archieve.bean.MetaDataItem
import java.io.FileNotFoundException

class MissingDataFileException(val dataItem: MetaDataItem, val path: String) : FileNotFoundException(path) {
}