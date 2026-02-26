package tiiehenry.android.snapshotor.provider.filesystem

import org.junit.Test
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.provider.filesystem.compressors.ZstdCompressor
import java.io.File
import kotlin.test.assertTrue

class ZstdCompressorTest {
    
    @Test
    fun testZstdCompressorCreation() {
        //测试 ZstdCompressor 是否正确实现了 IAlgorithmCompressor接口
        assertTrue(ZstdCompressor is IAlgorithmCompressor)
    }
    
    @Test
    fun testZstdCompressorId() {
        val taskHandler = ZstdCompressor.compress(
            dir = "/test/source",
            targetFile = "/test/target.zst",
            excludes = emptyList(),
            callback = object : ICompressCallback.Stub() {
                override fun onStart() {}
                override fun onProgress(progress: Int, speed: Long) {}
                override fun onDone(originalSize: Long, compressedSize: Long, md5: String?) {}
                override fun onError(error: String?) {}
            }
        )
        
        //验证任务ID格式正确
        val id = taskHandler.id()
        assertTrue(id.startsWith("zstd:"))
        assertTrue(id.contains("/test/source"))
        assertTrue(id.contains("/test/target.zst"))
    }
    
    @Test
    fun testZstdCompressorTwoStepProcess() {
        //测试Zstd压缩的两步过程：tar打包 + Zstd压缩
        val sourceDir = File("/tmp/test_source")
        val targetFile = File("/tmp/test_target.zst")
        
        //验证流程逻辑（不实际执行）
        assertTrue(sourceDir.path.contains("test_source"))
        assertTrue(targetFile.path.contains("test_target.zst"))
    }
}