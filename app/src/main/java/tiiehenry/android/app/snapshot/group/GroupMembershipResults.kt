package tiiehenry.android.app.snapshot.group

sealed class AddAppItemResult {
    data object AlreadyHere : AddAppItemResult()
    data object Added : AddAppItemResult()
    data class Conflict(val ownerGroupId: String) : AddAppItemResult()
    data object CorruptMultiOwner : AddAppItemResult()
    data object Busy : AddAppItemResult()
    data class Error(val message: String) : AddAppItemResult()
}

data class AddAppsResult(val items: Map<String, AddAppItemResult>) {
    val conflicts: Map<String, String>
        get() = items.mapNotNull { (pkg, r) ->
            (r as? AddAppItemResult.Conflict)?.let { pkg to it.ownerGroupId }
        }.toMap()

    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

sealed class MoveAppResult {
    data object Moved : MoveAppResult()
    data object AlreadyAtTarget : MoveAppResult()
    data object Locked : MoveAppResult()
    data object TargetNonEmpty : MoveAppResult()
    data object CorruptMultiOwner : MoveAppResult()
    data object Busy : MoveAppResult()
    data class Error(val message: String) : MoveAppResult()
}

sealed class SetMembershipModeResult {
    data object Ok : SetMembershipModeResult()
    data class Conflict(val packageNames: List<String>) : SetMembershipModeResult()
    data class Error(val message: String) : SetMembershipModeResult()
}
