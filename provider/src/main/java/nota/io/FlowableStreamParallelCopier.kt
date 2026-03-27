package nota.io

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream

class FlowableStreamParallelCopier(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
) : StreamParallelCopier(inputStream, outputStream) {

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

    override fun onBytesWritten(len: Int) {
        super.onBytesWritten(len)
        bytesWritten += len
        notifyProgress()
    }

    override fun startAndWait() {
        super.startAndWait()
        notifyProgressComplete()
    }
}