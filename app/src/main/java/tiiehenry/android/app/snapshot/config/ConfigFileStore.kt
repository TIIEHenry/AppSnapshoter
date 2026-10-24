package tiiehenry.android.app.snapshot.config

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** 读结果：文件不存在是缺失；已存在但读不出或解析不出是失败。 */
internal data class ConfigRead<T>(
    val value: T?,
    val failed: Boolean,
)

/**
 * 配置文件读写。写入先落同目录临时文件，再替换原文件。
 * 任一步失败都删掉临时文件，原文件字节保持不动，并把失败抛给调用方。
 */
internal object ConfigFileStore {
    var afterTempOpened: ((File) -> Unit)? = null

    private val defaultWriteTemp: (File, String) -> Unit = { file, content ->
        file.outputStream().use { output ->
            afterTempOpened?.invoke(file)
            output.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    private val defaultReplace: (File, File) -> Unit = { temp, target ->
        val source = temp.toPath()
        val destination = target.toPath()
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    var writeTemp: (File, String) -> Unit = defaultWriteTemp
    var replaceTemp: (File, File) -> Unit = defaultReplace

    fun resetHooks() {
        afterTempOpened = null
        writeTemp = defaultWriteTemp
        replaceTemp = defaultReplace
    }

    fun <T> read(file: File, parser: (String) -> T?): ConfigRead<T> {
        if (!file.exists()) return ConfigRead(value = null, failed = false)
        if (!file.isFile) return ConfigRead(value = null, failed = true)
        return try {
            val parsed = parser(file.readText())
            if (parsed == null) {
                ConfigRead(value = null, failed = true)
            } else {
                ConfigRead(value = parsed, failed = false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ConfigRead(value = null, failed = true)
        }
    }

    fun write(file: File, content: String) {
        val parent = file.absoluteFile.parentFile
            ?: throw IOException("Config file has no parent: $file")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Failed to create directory: ${parent.path}")
        }
        val temp = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
        try {
            writeTemp(temp, content)
            replaceTemp(temp, file)
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }
}
