package tiiehenry.android.app.snapshot.external

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONObject

class InstanceProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val packageName = uri.getQueryParameter("packageName").orEmpty()
        val userId = uri.getQueryParameter("userId")?.toIntOrNull() ?: 0
        val groupId = uri.getQueryParameter("groupId").orEmpty()
        val request = JSONObject()
            .put("method", "listInstances")
            .put("packageName", packageName)
            .put("userId", userId)
            .put("groupId", groupId)
            .toString()
        val caller = context?.let { InstanceCaller.fromBinder(it) }
        val json = InstanceGateway(context!!).call(caller, request)
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(json)) }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.snapshot.instance"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
