package tiiehenry.android.snapshot.provider.service.handler

/** Stored when AppOps has no entry for this op. Must not be written back as mode 0. */
internal const val APP_OP_MODE_UNREAD = Int.MIN_VALUE

internal fun backedUpOpMode(opModes: Map<Int, Int>?, op: Int): Int =
    opModes?.get(op) ?: APP_OP_MODE_UNREAD

/**
 * @param runtimeResult 0 fails the entry. -1 is "not a changeable permission type"
 * and does not fail it. Any other value continues.
 * An unread op mode does not call [setOpsMode].
 */
internal fun applyAppPermission(
    runtimeResult: Int,
    op: Int,
    mode: Int,
    opNone: Int,
    setOpsMode: (Int) -> Boolean
): Boolean {
    if (runtimeResult == 0) return false
    if (op != opNone && mode != APP_OP_MODE_UNREAD) {
        if (!setOpsMode(mode)) return false
    }
    return true
}
