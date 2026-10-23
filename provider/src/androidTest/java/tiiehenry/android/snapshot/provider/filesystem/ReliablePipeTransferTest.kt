package tiiehenry.android.snapshot.provider.filesystem

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tiiehenry.android.compress.zstd.TarJNI
import tiiehenry.android.snapshot.provider.service.handler.FileSystemHandler
import tiiehenry.android.snapshot.provider.service.ISnapShotRootService
import android.content.Context
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Only touches temporary files in the test application's cache; no root binding is used. */
@RunWith(AndroidJUnit4::class)
class ReliablePipeTransferTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test(timeout = 8_000)
    fun producerFailureReleasesNativeTarReaderAndAllLocalDescriptors() {
        val target = File(context.cacheDir, "pipe-producer-${System.nanoTime()}")
        val reading = CountDownLatch(1)
        var inputFd: FileDescriptor? = null
        var outputFd: FileDescriptor? = null
        try {
            val error = runCatching {
                ReliablePipeTransfer().use { pipe ->
                    runBlocking {
                        pipe.transfer(
                            produce = { output ->
                                outputFd = (output as FileOutputStream).fd
                                check(reading.await(2, TimeUnit.SECONDS))
                                output.write(ByteArray(128) { 65 }) // incomplete tar header
                                Thread.sleep(100) // allow native tar to block awaiting its remainder
                                throw IOException("source read failed")
                            },
                            consume = { input ->
                                inputFd = input.fileDescriptor
                                reading.countDown()
                                if (!FileSystemHandler(context).extractTarFromFd(input, target.path)) {
                                    throw IOException("tar rejected incomplete input")
                                }
                            }
                        )
                    }
                }
            }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(outputFd!!.valid())
            assertFalse(inputFd!!.valid())
        } finally {
            target.deleteRecursively()
        }
    }

    @Test(timeout = 8_000)
    fun nativeTarEarlyExitBreaksAProducerWritingBeyondPipeCapacity() {
        val directory = File(context.cacheDir, "pipe-consumer-${System.nanoTime()}").apply { mkdirs() }
        val started = CountDownLatch(1)
        var inputFd: FileDescriptor? = null
        var outputFd: FileDescriptor? = null
        try {
            val error = runCatching {
                ReliablePipeTransfer().use { pipe ->
                    runBlocking {
                        pipe.transfer(
                            produce = { output ->
                                outputFd = (output as FileOutputStream).fd
                                started.countDown()
                                val block = ByteArray(128 * 1024)
                                repeat(128) { output.write(block) }
                            },
                            consume = { input ->
                                inputFd = input.fileDescriptor
                                check(started.await(2, TimeUnit.SECONDS))
                                val code = TarJNI.callCliWithInputFd(
                                    input.fd, File(directory, "stdout").path,
                                    File(directory, "stderr").path,
                                    arrayOf("tar", "--not-a-valid-tar-option")
                                )
                                check(code != 0)
                                throw IOException("tar exited before reading")
                            }
                        )
                    }
                }
            }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(inputFd!!.valid())
            assertFalse(outputFd!!.valid())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(timeout = 8_000)
    fun descriptorTarRoundTripDrainsPaddingAndClosesDescriptors() {
        val directory = File(context.cacheDir, "pipe-success-${System.nanoTime()}").apply { mkdirs() }
        val source = File(directory, "source").apply { mkdirs() }
        val payload = "restored through anonymous pipe"
        File(source, "payload.txt").writeText(payload)
        val archive = File(directory, "archive.tar")
        val restored = File(directory, "restored")
        var inputFd: FileDescriptor? = null
        var outputFd: FileDescriptor? = null
        try {
            assertEquals(0, TarJNI.callCli(null, File(directory, "stdout").path,
                File(directory, "stderr").path,
                arrayOf("tar", "-cpf", archive.path, "-C", source.path, ".")))
            ReliablePipeTransfer().use { pipe ->
                runBlocking {
                    pipe.transfer(
                        produce = { output ->
                            outputFd = (output as FileOutputStream).fd
                            archive.inputStream().use { it.copyTo(output) }
                            // More than pipe capacity after the tar end marker
                            // verifies the native consumer drains remaining bytes.
                            repeat(16) { output.write(ByteArray(128 * 1024)) }
                        },
                        consume = { input ->
                            inputFd = input.fileDescriptor
                            check(FileSystemHandler(context).extractTarFromFd(input, restored.path))
                        }
                    )
                }
            }
            assertEquals(payload, File(restored, "payload.txt").readText())
            assertFalse(inputFd!!.valid())
            assertFalse(outputFd!!.valid())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(timeout = 8_000)
    fun invalidInputDescriptorFailsWithoutForking() {
        val directory = File(context.cacheDir, "pipe-invalid-${System.nanoTime()}").apply { mkdirs() }
        try {
            assertEquals(-1, TarJNI.callCliWithInputFd(-1, File(directory, "stdout").path,
                File(directory, "stderr").path, arrayOf("tar", "-tf", "-")))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(timeout = 8_000)
    fun ordinaryFileArchiveRestoresOnlyItsBasename() {
        val directory = File(context.cacheDir, "archive-file-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(directory, "source").apply { mkdirs() }
            val original = File(source, "-payload.txt").apply { writeText("exact file contents") }
            File(source, "sibling.txt").writeText("must not be archived")
            val archive = File(directory, "archive.tar")
            val root = Proxy.newProxyInstance(
                ISnapShotRootService::class.java.classLoader,
                arrayOf(ISnapShotRootService::class.java)
            ) { _, method, args ->
                check(method.name == "callTarCli") { method.name }
                @Suppress("UNCHECKED_CAST")
                TarJNI.callCli(args[0] as String?, args[1] as String, args[2] as String,
                    args[3] as Array<String>)
            } as ISnapShotRootService
            val fs = FileSystemImpl({ root }, { FileSystemManager.getLocal() }, context)
            fs.createTarArchive(original.path, archive.path, emptyList(), emptyList(),
                File(directory, "stderr").path, File(directory, "stdout").path)
            val restored = File(directory, "restored")
            assertTrue(FileSystemHandler(context).extractTar(archive.path, restored.path))
            assertEquals("exact file contents", File(restored, original.name).readText())
            assertEquals(listOf(original.name), restored.list()!!.toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(timeout = 8_000)
    fun clearDirectoryDeletesContentsAndDoesNotFollowSymlinks() {
        val directory = File(context.cacheDir, "clean-dir-${System.nanoTime()}").apply { mkdirs() }
        try {
            val target = File(directory, "target").apply { mkdirs() }
            val nested = File(target, "nested").apply { mkdirs() }
            File(nested, "old.txt").writeText("old data")
            val external = File(directory, "outside").apply { mkdirs() }
            val outsideFile = File(external, "preserve.txt").apply { writeText("keep") }
            android.system.Os.symlink(external.path, File(target, "link").path)
            android.system.Os.symlink(external.path, File(nested, "nested-link").path)
            val fs = FileSystemImpl({ error("Root Binder is unused") },
                { FileSystemManager.getLocal() }, context)
            assertTrue(fs.cleanDir(target.path))
            assertTrue(target.isDirectory)
            assertTrue(target.list()!!.isEmpty())
            assertEquals("keep", outsideFile.readText())
            assertFalse(fs.cleanDir(File(directory, "absent").path))
        } finally {
            directory.deleteRecursively()
        }
    }


    @Test(timeout = 12_000)
    fun remoteDescriptorRoundTripClosesBothProcessesCopies() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val directory = File(testContext.cacheDir, "remote-success-${System.nanoTime()}").apply { mkdirs() }
        val source = File(directory, "source").apply { mkdirs() }
        val payload = "tar restored in another ordinary process"
        File(source, "payload.txt").writeText(payload)
        val archive = File(directory, "archive.tar")
        val restored = File(directory, "restored")
        var inputFd: FileDescriptor? = null
        var outputFd: FileDescriptor? = null
        try {
            assertEquals(0, TarJNI.callCli(null, File(directory, "stdout").path,
                File(directory, "stderr").path,
                arrayOf("tar", "-cpf", archive.path, "-C", source.path, ".")))
            withRemoteTar(testContext) { remote ->
                assertNotEquals(Process.myPid(), remotePid(remote))
                ReliablePipeTransfer().use { pipe ->
                    runBlocking {
                        pipe.transfer(
                            produce = { output ->
                                outputFd = (output as FileOutputStream).fd
                                archive.inputStream().use { it.copyTo(output) }
                                repeat(16) { output.write(ByteArray(128 * 1024)) }
                            },
                            consume = { input ->
                                inputFd = input.fileDescriptor
                                check(extractRemote(remote, input, restored))
                            }
                        )
                    }
                }
            }
            assertEquals(payload, File(restored, "payload.txt").readText())
            assertFalse(inputFd!!.valid())
            assertFalse(outputFd!!.valid())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(timeout = 12_000)
    fun remoteProducerFailureReturnsAfterTransferredDescriptorIsClosed() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val directory = File(testContext.cacheDir, "remote-failure-${System.nanoTime()}").apply { mkdirs() }
        val received = CountDownLatch(1)
        var inputFd: FileDescriptor? = null
        var outputFd: FileDescriptor? = null
        var remoteReturned = false
        try {
            withRemoteTar(testContext) { remote ->
                assertNotEquals(Process.myPid(), remotePid(remote))
                val error = runCatching {
                    ReliablePipeTransfer().use { pipe ->
                        runBlocking {
                            pipe.transfer(
                                produce = { output ->
                                    outputFd = (output as FileOutputStream).fd
                                    check(received.await(3, TimeUnit.SECONDS))
                                    output.write(ByteArray(128) { 65 })
                                    Thread.sleep(100)
                                    throw IOException("source failed after remote received its FD")
                                },
                                consume = { input ->
                                    inputFd = input.fileDescriptor
                                    val success = extractRemote(remote, input, File(directory, "restored")) {
                                        received.countDown()
                                    }
                                    remoteReturned = true
                                    check(!success)
                                }
                            )
                        }
                    }
                }.exceptionOrNull()
                assertNotNull(error)
            }
            assertEquals(0L, received.count)
            assertTrue("Remote native extraction must return before the transfer finishes", remoteReturned)
            assertFalse(inputFd!!.valid())
            assertFalse(outputFd!!.valid())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun <T> withRemoteTar(testContext: Context, action: (IBinder) -> T): T {
        val connected = CompletableFuture<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                connected.complete(service)
            }
            override fun onServiceDisconnected(name: ComponentName) {
                connected.completeExceptionally(IOException("Test tar service disconnected"))
            }
        }
        check(testContext.bindService(Intent(testContext, TarPipeTestService::class.java),
            connection, Context.BIND_AUTO_CREATE))
        return try {
            action(connected.get(3, TimeUnit.SECONDS))
        } finally {
            testContext.unbindService(connection)
        }
    }

    private fun remotePid(remote: IBinder): Int {
        val request = Parcel.obtain()
        val response = Parcel.obtain()
        return try {
            request.writeInterfaceToken(TarPipeTestService.TOKEN)
            check(remote.transact(TarPipeTestService.PID, request, response, 0))
            response.readException()
            response.readInt()
        } finally {
            request.recycle()
            response.recycle()
        }
    }

    private fun extractRemote(
        remote: IBinder,
        input: ParcelFileDescriptor,
        target: File,
        received: () -> Unit = {}
    ): Boolean {
        val notification = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != TarPipeTestService.READY) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(TarPipeTestService.TOKEN)
                received()
                return true
            }
        }
        val request = Parcel.obtain()
        val response = Parcel.obtain()
        return try {
            request.writeInterfaceToken(TarPipeTestService.TOKEN)
            request.writeParcelable(input, 0)
            request.writeString(target.path)
            request.writeStrongBinder(notification)
            check(remote.transact(TarPipeTestService.EXTRACT, request, response, 0))
            response.readException()
            val success = response.readInt() != 0
            check(response.readInt() == 1) { "Remote process retained its input descriptor" }
            success
        } finally {
            request.recycle()
            response.recycle()
        }
    }

}
