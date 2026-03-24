package tiiehenry.android.app.snapshot.main.launch

import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.MetaDataItem
import java.io.FileNotFoundException

class MissingDataFileException(val dataItem: MetaDataItem, val path: String) : FileNotFoundException(path) {
}