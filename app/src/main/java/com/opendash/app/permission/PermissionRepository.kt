package com.opendash.app.permission

/**
 * UI-facing facade over PermissionManager. Returns a flat list of rows the
 * Settings permission screen can render without distinguishing runtime
 * vs. special grants.
 */
class PermissionRepository(
    private val manager: PermissionManager
) {

    data class Row(
        val id: String,
        val title: String,
        val rationale: String,
        val unlocks: List<String>,
        val granted: Boolean,
        val kind: Kind
    ) {
        enum class Kind { RUNTIME, SPECIAL }
    }

    fun rows(): List<Row> {
        val state = manager.snapshot()
        val runtime = state.entries.map { e ->
            Row(
                id = e.entry.permission,
                title = e.entry.title,
                rationale = e.entry.rationale,
                unlocks = e.entry.unlocks,
                granted = e.granted,
                kind = Row.Kind.RUNTIME
            )
        }
        val special = state.specialGrants.map { s ->
            Row(
                id = s.grant.id,
                title = s.grant.title,
                rationale = s.grant.rationale,
                unlocks = s.grant.unlocks,
                granted = s.granted,
                kind = Row.Kind.SPECIAL
            )
        }
        return runtime + special
    }

    fun ungrantedCount(): Int = rows().count { !it.granted }
}
