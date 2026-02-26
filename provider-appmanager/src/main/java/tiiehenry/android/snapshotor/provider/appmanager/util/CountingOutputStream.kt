package tiiehenry.android.snapshotor.provider.appmanager.util

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 *数功能的输出流，用于监控写入进度
 */
class CountingOutputStream(
    private val source: OutputStream,
    private val onProgress: ((bytesWritten: Long, speed: Long) -> Unit)? = null
) : FilterOutputStream(source) {
    
    private var bytesWritten: Long = 0
    private var lastTime: Long = System.currentTimeMillis()
    private var lastBytes: Long = 0
    
    @Throws(IOException::class)
    override fun write(b: Int) {
        out.write(b)
        bytesWritten++
        notifyProgress()
    }
    
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        bytesWritten += len.toLong()
        notifyProgress()
    }
    
    @Throws(IOException::class)
    override fun close() {
        super.close()
        notifyProgress(true) // 最终通知
    }
    
    private fun notifyProgress(force: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (force || currentTime - lastTime >= 1000) { //更新一次
            val deltaTime = currentTime - lastTime
            val deltaBytes = bytesWritten - lastBytes
            val speed = if (deltaTime > 0) deltaBytes * 1000 / deltaTime else 0
            onProgress?.invoke(bytesWritten, speed)
            lastTime = currentTime
            lastBytes = bytesWritten
        }
    }
    
    fun getBytesWritten(): Long = bytesWritten
}