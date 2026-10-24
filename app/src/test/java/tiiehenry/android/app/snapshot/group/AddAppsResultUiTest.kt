package tiiehenry.android.app.snapshot.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddAppsResultUiTest {

    @Test
    fun conflictsWithErrorStillNotifyAndOpenMoveDialog() {
        val plan = AddAppsResultUi.planHandle(
            AddAppsResult(
                linkedMapOf(
                    "com.here" to AddAppItemResult.AlreadyHere,
                    "com.bad" to AddAppItemResult.Error("first"),
                    "com.move" to AddAppItemResult.Conflict("owner"),
                    "com.worse" to AddAppItemResult.Error("second"),
                )
            )
        )
        assertEquals(AddAppsResultUi.Notice.Failed("first"), plan.notice)
        assertTrue(plan.openMoveDialog)
    }

    @Test
    fun conflictsWithBusyStillNotifyAndOpenMoveDialog() {
        val plan = AddAppsResultUi.planHandle(
            AddAppsResult(
                linkedMapOf(
                    "com.here" to AddAppItemResult.AlreadyHere,
                    "com.move" to AddAppItemResult.Conflict("owner"),
                    "com.busy" to AddAppItemResult.Busy,
                )
            )
        )
        assertEquals(AddAppsResultUi.Notice.Busy, plan.notice)
        assertTrue(plan.openMoveDialog)
    }

    @Test
    fun conflictsWithCorruptMultiOwnerStillNotifyAndOpenMoveDialog() {
        val plan = AddAppsResultUi.planHandle(
            AddAppsResult(
                linkedMapOf(
                    "com.bad" to AddAppItemResult.Error("disk"),
                    "com.busy" to AddAppItemResult.Busy,
                    "com.move" to AddAppItemResult.Conflict("owner"),
                    "com.corrupt" to AddAppItemResult.CorruptMultiOwner,
                )
            )
        )
        assertEquals(AddAppsResultUi.Notice.Corrupt, plan.notice)
        assertTrue(plan.openMoveDialog)
    }

    @Test
    fun conflictsDoNotNotifyAlreadyHere() {
        val plan = AddAppsResultUi.planHandle(
            AddAppsResult(
                linkedMapOf(
                    "com.here" to AddAppItemResult.AlreadyHere,
                    "com.move" to AddAppItemResult.Conflict("owner"),
                )
            )
        )
        assertNull(plan.notice)
        assertTrue(plan.openMoveDialog)
    }

    @Test
    fun alreadyHereWithoutConflictsNotifiesAndDoesNotOpenDialog() {
        val plan = AddAppsResultUi.planHandle(
            AddAppsResult(linkedMapOf("com.here" to AddAppItemResult.AlreadyHere))
        )
        assertEquals(AddAppsResultUi.Notice.AlreadyHere, plan.notice)
        assertFalse(plan.openMoveDialog)
    }

    @Test
    fun movedThenBusyStopsLaterPackagesAndRefreshesOnce() {
        val run = runMoves(linkedMapOf("a" to "src", "b" to "src", "c" to "src")) { pkg ->
            if (pkg == "b") MoveAppResult.Busy else MoveAppResult.Moved
        }
        assertEquals(listOf("a", "b"), run.attempted)
        assertEquals(1, run.refreshes)
        assertEquals(listOf("b" to MoveAppResult.Busy), run.failures)
    }

    @Test
    fun busyFirstDoesNotRefreshMembership() {
        val run = runMoves(linkedMapOf("a" to "src", "b" to "src", "c" to "src")) { pkg ->
            if (pkg == "a") MoveAppResult.Busy else MoveAppResult.Moved
        }
        assertEquals(listOf("a"), run.attempted)
        assertEquals(0, run.refreshes)
        assertEquals(listOf("a" to MoveAppResult.Busy), run.failures)
    }

    @Test
    fun alreadyAtTargetThenBusyDoesNotRefresh() {
        val run = runMoves(linkedMapOf("a" to "src", "b" to "src", "c" to "src")) { pkg ->
            if (pkg == "a") MoveAppResult.AlreadyAtTarget else MoveAppResult.Busy
        }
        assertEquals(listOf("a", "b"), run.attempted)
        assertEquals(0, run.refreshes)
    }

    @Test
    fun everyPackageMovedRefreshesOnce() {
        val run = runMoves(linkedMapOf("a" to "src", "b" to "src", "c" to "src")) {
            MoveAppResult.Moved
        }
        assertEquals(listOf("a", "b", "c"), run.attempted)
        assertEquals(1, run.refreshes)
        assertTrue(run.failures.isEmpty())
    }

    @Test
    fun lockedStillMovesLaterPackagesAndRefreshesWhenFinished() {
        val run = runMoves(linkedMapOf("a" to "src", "b" to "src")) { pkg ->
            if (pkg == "a") MoveAppResult.Locked else MoveAppResult.Moved
        }
        assertEquals(listOf("a", "b"), run.attempted)
        assertEquals(1, run.refreshes)
        assertEquals(MoveAppResult.Locked, run.failures.single().second)
    }

    private fun runMoves(
        conflicts: Map<String, String>,
        resultOf: (String) -> MoveAppResult,
    ): MoveRun {
        val attempted = mutableListOf<String>()
        val failures = mutableListOf<Pair<String, MoveAppResult>>()
        var refreshes = 0
        AddAppsResultUi.driveMoves(
            conflicts = conflicts,
            move = { pkg, _, done ->
                attempted += pkg
                done(resultOf(pkg))
            },
            onFailure = { pkg, result -> failures += pkg to result },
            onMembershipChanged = { refreshes++ },
        )
        return MoveRun(attempted, failures, refreshes)
    }

    private data class MoveRun(
        val attempted: List<String>,
        val failures: List<Pair<String, MoveAppResult>>,
        val refreshes: Int,
    )
}
