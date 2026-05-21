package tiiehenry.android.app.snapshot.archive.restore

import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem

interface DataItemCallback {

    fun onStart(dataItem: MetaDataItem)

    fun onProgress(dataItem: MetaDataItem, bytesWritten: Long, bytesPerS: Long)

    fun onDone(dataItem: MetaDataItem, originSize: Long, targetSize: Long, md5: String)

    fun onError(dataItem: MetaDataItem, e: Exception)
}