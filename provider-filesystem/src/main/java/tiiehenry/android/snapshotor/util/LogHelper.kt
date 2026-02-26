package tiiehenry.android.snapshotor.util

import android.util.Log

object LogHelper {
    fun v(tag: String, method: String, msg: String) {
        Log.v(tag, "$method: $msg")
    }

    fun d(tag: String, method: String, msg: String) {
        Log.d(tag, "$method: $msg")
    }

    fun i(tag: String, method: String, msg: String) {
        Log.i(tag, "$method: $msg")
    }

    fun w(tag: String, method: String, msg: String) {
        Log.w(tag, "$method: $msg")
    }

    fun e(tag: String, method: String, msg: String) {
        Log.e(tag, "$method: $msg")
    }

    fun e(tag: String, method: String, msg: String, tr: Throwable) {
        Log.e(tag, "$method: $msg", tr)
    }
}