package nota.io

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch

class StreamParallelTransformer(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val dataItemCount: Int = 5
) {
    private var finished = false
    private var exception: Exception? = null
    private var countDownLatch: CountDownLatch? = null
    private var writeThread: Thread? = null
    private var readThread: Thread? = null
    private var canceled = false
    private var arrayBlockingQueue: ArrayBlockingQueue<DataItem>? = null

    private val _progressFlow = MutableStateFlow(Progress(0, 0))
    val progressFlow: StateFlow<Progress> = _progressFlow.asStateFlow()

    data class Progress(val bytesWritten: Long, val speed: Long)

    private var bytesWritten: Long = 0
    private var lastTime: Long = System.currentTimeMillis()
    private var lastBytes: Long = 0

    private fun notifyProgress() {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastTime
        if (deltaTime >= 1000) {
            val deltaBytes = bytesWritten - lastBytes
            val speed = deltaBytes * 1000 / deltaTime
            val progress = Progress(bytesWritten, speed)
            _progressFlow.value = progress
            lastTime = currentTime
            lastBytes = bytesWritten
        }
    }

    private fun notifyProgressComplete() {
        val progress = Progress(bytesWritten, 0)
        _progressFlow.value = progress
    }

    private fun startWrite(queue: ArrayBlockingQueue<DataItem>) {
        writeThread = Thread {
            try {
                while (!finished) {
                    val dataItem = queue.take()
                    if (dataItem.len == -1) {
                        break
                    }
                    if (canceled) {
                        throw InterruptedException()
                    }
                    outputStream.write(dataItem.array, 0, dataItem.len)
                    bytesWritten += dataItem.len
                    notifyProgress()
                }
            } catch (e: Exception) {
                onException(e)
            } finally {
                countDownLatch?.countDown()
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun startRead(queue: ArrayBlockingQueue<DataItem>, dataItems: Array<DataItem>) {
        readThread = Thread {
            try {
                var i = 0
                while (!finished) {
                    if (canceled) {
                        throw InterruptedException()
                    }
                    val read = inputStream.read(dataItems[i].array)
                    dataItems[i].len = read
                    if (read == -1) {
                        break
                    }
                    queue.put(dataItems[i])
                    i = (i + 1) % dataItems.size
                }
                if (!finished) {
                    queue.put(DataItem(null, -1))
                }
            } catch (e: Exception) {
                onException(e)
            } finally {
                countDownLatch?.countDown()
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Throws(Exception::class)
    fun startAndWait() {
        arrayBlockingQueue = ArrayBlockingQueue(dataItemCount - 2)
        val dataItems = Array(dataItemCount) { DataItem(ByteArray(128 * 1024), 0) }
        finished = false
        canceled = false
        countDownLatch = CountDownLatch(2)
        synchronized(this) {
            startWrite(arrayBlockingQueue!!)
            startRead(arrayBlockingQueue!!, dataItems)
        }
        countDownLatch!!.await()
        finished = true
        notifyProgressComplete()
        exception?.let { throw it }
    }

    private fun onException(e: Exception) {
        synchronized(this) {
            if (!finished) {
                exception = e
                finished = true
                writeThread?.interrupt()
                writeThread = null
                readThread?.interrupt()
                readThread = null
            } else {
                e.printStackTrace()
            }
        }
    }

    fun cancel() {
        canceled = true
        arrayBlockingQueue?.clear()
        writeThread?.interrupt()
        writeThread = null
        readThread?.interrupt()
        readThread = null
    }

    protected class DataItem(var array: ByteArray?, var len: Int)
}
