package tiiehenry.android.app.snapshot.group

import android.util.Log

/** Android unit-test stubs throw from Log. Device Log does not. */
internal object GroupLog {
    fun d(tag: String, message: String) = safe { Log.d(tag, message) }
    fun i(tag: String, message: String) = safe { Log.i(tag, message) }
    fun w(tag: String, message: String) = safe { Log.w(tag, message) }
    fun e(tag: String, message: String) = safe { Log.e(tag, message) }
    fun e(tag: String, message: String, error: Throwable) = safe { Log.e(tag, message, error) }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            val message = e.message.orEmpty()
            if ("Stub!" !in message && "not mocked" !in message) throw e
        }
    }
}
