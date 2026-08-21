package tiiehenry.android.app.snapshot.group

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程级占用：全局批任务 + 按 packageDir 的单应用快照/恢复/移动。
 * SSOT；[tiiehenry.android.app.snapshot.SnapshotViewModel.isBatchRunning] 仅为 UI 门面。
 */
class PackageOpGuard {
    private val globalBatch = AtomicBoolean(false)
    private val packageOps = mutableSetOf<String>()
    private val lock = Any()

    fun tryBeginGlobalBatch(): Boolean {
        synchronized(lock) {
            if (globalBatch.get()) return false
            // 允许在无 package 占用时开批；若已有单应用占用则拒绝
            if (packageOps.isNotEmpty()) return false
            globalBatch.set(true)
            return true
        }
    }

    fun endGlobalBatch() {
        globalBatch.set(false)
    }

    fun tryBeginPackageOp(packageDir: String): Boolean {
        val key = normalize(packageDir)
        synchronized(lock) {
            if (globalBatch.get() || key in packageOps) return false
            packageOps.add(key)
            return true
        }
    }

    fun endPackageOp(packageDir: String) {
        val key = normalize(packageDir)
        synchronized(lock) {
            packageOps.remove(key)
        }
    }

    fun isGlobalBatchRunning(): Boolean = globalBatch.get()

    fun isBusy(packageDir: String? = null): Boolean {
        synchronized(lock) {
            if (globalBatch.get()) return true
            if (packageDir == null) return packageOps.isNotEmpty()
            return normalize(packageDir) in packageOps
        }
    }

    private fun normalize(path: String): String =
        path.trimEnd('/').lowercase()
}
