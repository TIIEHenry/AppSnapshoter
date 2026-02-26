package tiiehenry.android.snapshotor.provider.appmanager.util

import android.util.Log

/**
 *志工具类，提供统一的日志输出功能
 */
object LogHelper {
    private const val TAG_PREFIX = "AppSnapshoter_"
    
    fun v(tag: String, method: String, message: String) {
        Log.v("$TAG_PREFIX$tag", "[$method] $message")
    }
    
    fun v(tag: String, method: String, message: String, throwable: Throwable) {
        Log.v("$TAG_PREFIX$tag", "[$method] $message", throwable)
    }
    
    fun d(tag: String, method: String, message: String) {
        Log.d("$TAG_PREFIX$tag", "[$method] $message")
    }
    
    fun d(tag: String, method: String, message: String, throwable: Throwable) {
        Log.d("$TAG_PREFIX$tag", "[$method] $message", throwable)
    }
    
    fun i(tag: String, method: String, message: String) {
        Log.i("$TAG_PREFIX$tag", "[$method] $message")
    }
    
    fun i(tag: String, method: String, message: String, throwable: Throwable) {
        Log.i("$TAG_PREFIX$tag", "[$method] $message", throwable)
    }
    
    fun w(tag: String, method: String, message: String) {
        Log.w("$TAG_PREFIX$tag", "[$method] $message")
    }
    
    fun w(tag: String, method: String, message: String, throwable: Throwable) {
        Log.w("$TAG_PREFIX$tag", "[$method] $message", throwable)
    }
    
    fun e(tag: String, method: String, message: String) {
        Log.e("$TAG_PREFIX$tag", "[$method] $message")
    }
    
    fun e(tag: String, method: String, message: String, throwable: Throwable) {
        Log.e("$TAG_PREFIX$tag", "[$method] $message", throwable)
    }
}