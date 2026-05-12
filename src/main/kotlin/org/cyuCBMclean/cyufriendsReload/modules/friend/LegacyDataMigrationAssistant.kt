package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileRepository
import java.sql.Connection

enum class LegacyMigrationScope(
    val id: String,
    val displayName: String,
    val legacyTables: List<String>
) {
    FRIENDS("friends", "好友关系", listOf("friends")),
    REQUESTS("requests", "好友申请", listOf("friend_requests")),
    BLOCKS("blocks", "黑名单", listOf("blacklist")),
    SETTINGS("settings", "传送与提醒设置", listOf("tp_permissions", "individual_friend_settings", "friend_notify_settings", "friend_last_online")),
    PROFILE("profile", "个人资料", listOf("profile")),
    CHAT("chat", "离线私聊", listOf("offline_messages")),
    SOCIAL("social", "动态与留言墙", listOf("friend_status_updates", "wall_messages"));

    companion object {
        fun fromId(raw: String?): LegacyMigrationScope? {
            return entries.firstOrNull {
                it.id.equals(raw, ignoreCase = true) ||
                    it.displayName.equals(raw, ignoreCase = true)
            }
        }
    }
}

data class LegacyMigrationInspectEntry(
    val scope: LegacyMigrationScope,
    val availableTables: List<String>,
    val rowCount: Int
) {
    val available: Boolean
        get() = availableTables.isNotEmpty()
}

data class LegacyMigrationScopeResult(
    val scope: LegacyMigrationScope,
    val inserted: Int,
    val updated: Int,
    val skipped: Int
)

data class LegacyMigrationResult(
    val scopes: List<LegacyMigrationScopeResult>
) {
    val inserted: Int
        get() = scopes.sumOf(LegacyMigrationScopeResult::inserted)
    val updated: Int
        get() = scopes.sumOf(LegacyMigrationScopeResult::updated)
    val skipped: Int
        get() = scopes.sumOf(LegacyMigrationScopeResult::skipped)
}

/**
 * 旧版数据迁移用完就该退场，别让新逻辑依赖它
 */
class LegacyDataMigrationAssistant(
    private val plugin: CyufriendsReload,
    private val db: DatabaseManager
) {

    private val currentFriendsTable = "cyu_friends"
    private val currentRequestsTable = "cyu_friend_requests"
    private val currentBlocksTable = "cyu_friend_blocks"
    private val currentPreferencesTable = "cyu_friend_preferences"
    private val currentPersonalPreferencesTable = "cyu_friend_personal_preferences"
    private val currentProfilesTable = "cyu_player_profiles"
    private val currentChatTable = "cyu_chat_messages"
    private val currentStatusTable = "cyu_social_status"
    private val currentWallTable = "cyu_social_wall"

    fun inspectSync(): List<LegacyMigrationInspectEntry> = db.executeSync {
        LegacyMigrationScope.entries.map { scope ->
            val tables = scope.legacyTables.filter { hasTable(this, it) }
            val rowCount = tables.sumOf { tableRowCount(this, it) }
            LegacyMigrationInspectEntry(scope, tables, rowCount)
        }
    }

    fun resolveScopes(raw: String?): Set<LegacyMigrationScope> {
        return when (raw?.trim()?.lowercase()) {
            null, "", "active" -> activeScopes()
            "all" -> LegacyMigrationScope.entries.toSet()
            else -> LegacyMigrationScope.fromId(raw)?.let(::setOf) ?: emptySet()
        }
    }

    fun importSync(scopes: Set<LegacyMigrationScope>): LegacyMigrationResult {
        val orderedScopes = scopes.sortedBy { it.ordinal }
        if (orderedScopes.isEmpty()) return LegacyMigrationResult(emptyList())

        return db.executeSync {
            val originalAutoCommit = autoCommit
                autoCommit = false
            try {
                val results = orderedScopes.map { scope -> importScope(this, scope) }
                commit()
                LegacyMigrationResult(results)
            } catch (exception: Exception) {
                rollback()
                throw exception
            } finally {
                autoCommit = originalAutoCommit
            }
        }.also {
            invalidateRuntimeCaches()
        }
    }

    private fun importScope(connection: Connection, scope: LegacyMigrationScope): LegacyMigrationScopeResult {
        return when (scope) {
            LegacyMigrationScope.FRIENDS -> connection.importFriends()
            LegacyMigrationScope.REQUESTS -> connection.importRequests()
            LegacyMigrationScope.BLOCKS -> connection.importBlocks()
            LegacyMigrationScope.SETTINGS -> connection.importSettings()
            LegacyMigrationScope.PROFILE -> connection.importProfiles()
            LegacyMigrationScope.CHAT -> connection.importChatMessages()
            LegacyMigrationScope.SOCIAL -> connection.importSocialContent()
        }
    }

    private fun Connection.importFriends(): LegacyMigrationScopeResult {
        if (!hasTable(this, "friends")) return LegacyMigrationScopeResult(LegacyMigrationScope.FRIENDS, 0, 0, 0)

        data class ExistingFriendRow(
            val noteName: String?,
            val groupName: String?,
            val createdAt: Long,
            val lastInteractionAt: Long
        )

        var inserted = 0
        var updated = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        query("SELECT player_uid, friend_uid, added_at, note, group_name FROM friends") { rs ->
            while (rs.next()) {
                val ownerUid = uidString(rs.getString("player_uid")) ?: continue
                val friendUid = uidString(rs.getString("friend_uid")) ?: continue
                if (ownerUid == friendUid) {
                    skipped++
                    continue
                }

                val addedAt = rs.getLong("added_at").takeIf { it > 0L } ?: now
                val legacyNote = normalizeLegacyNote(rs.getString("note"))
                val legacyGroup = normalizeLegacyGroup(rs.getString("group_name"))
                val existing = query(
                    "SELECT note_name, group_name, created_at, last_interaction_at FROM $currentFriendsTable WHERE user_uid = ? AND friend_uid = ?",
                    ownerUid,
                    friendUid
                ) { current ->
                    if (!current.next()) return@query null
                    ExistingFriendRow(
                        noteName = current.getString("note_name"),
                        groupName = current.getString("group_name"),
                        createdAt = current.getLong("created_at"),
                        lastInteractionAt = current.getLong("last_interaction_at")
                    )
                }

                if (existing == null) {
                    update(
                        "INSERT INTO $currentFriendsTable (user_uid, friend_uid, note_name, note_detail, group_name, tag_name, pinned, created_at, last_interaction_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        ownerUid,
                        friendUid,
                        legacyNote,
                        null,
                        legacyGroup ?: FriendDefaults.DEFAULT_GROUP_NAME,
                        null,
                        0,
                        addedAt,
                        addedAt
                    )
                    inserted++
                    continue
                }

                val mergedNote = existing.noteName.takeUnless { it.isNullOrBlank() } ?: legacyNote
                val mergedGroup = existing.groupName
                    ?.takeUnless { it.isBlank() || it == FriendDefaults.DEFAULT_GROUP_NAME }
                    ?: legacyGroup
                    ?: existing.groupName
                    ?: FriendDefaults.DEFAULT_GROUP_NAME
                val mergedCreatedAt = if (existing.createdAt > 0L) minOf(existing.createdAt, addedAt) else addedAt
                val mergedLastInteraction = maxOf(existing.lastInteractionAt.takeIf { it > 0L } ?: mergedCreatedAt, addedAt)
                val changed = mergedNote != existing.noteName ||
                    mergedGroup != (existing.groupName ?: FriendDefaults.DEFAULT_GROUP_NAME) ||
                    mergedCreatedAt != existing.createdAt ||
                    mergedLastInteraction != existing.lastInteractionAt

                if (!changed) {
                    skipped++
                    continue
                }

                update(
                    "UPDATE $currentFriendsTable SET note_name = ?, group_name = ?, created_at = ?, last_interaction_at = ? WHERE user_uid = ? AND friend_uid = ?",
                    mergedNote,
                    mergedGroup,
                    mergedCreatedAt,
                    mergedLastInteraction,
                    ownerUid,
                    friendUid
                )
                updated++
            }
        }
        return LegacyMigrationScopeResult(LegacyMigrationScope.FRIENDS, inserted, updated, skipped)
    }

    private fun Connection.importRequests(): LegacyMigrationScopeResult {
        if (!hasTable(this, "friend_requests")) return LegacyMigrationScopeResult(LegacyMigrationScope.REQUESTS, 0, 0, 0)

        var inserted = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        query("SELECT to_uid, from_uid, timestamp FROM friend_requests") { rs ->
            while (rs.next()) {
                val senderUid = uidString(rs.getString("from_uid")) ?: continue
                val receiverUid = uidString(rs.getString("to_uid")) ?: continue
                val createdAt = rs.getLong("timestamp").takeIf { it > 0L } ?: now
                val exists = query(
                    "SELECT 1 FROM $currentRequestsTable WHERE sender_uid = ? AND receiver_uid = ? LIMIT 1",
                    senderUid,
                    receiverUid
                ) { current -> current.next() }
                if (exists) {
                    skipped++
                    continue
                }
                update(
                    "INSERT INTO $currentRequestsTable (sender_uid, receiver_uid, note, created_at) VALUES (?, ?, ?, ?)",
                    senderUid,
                    receiverUid,
                    null,
                    createdAt
                )
                inserted++
            }
        }
        return LegacyMigrationScopeResult(LegacyMigrationScope.REQUESTS, inserted, 0, skipped)
    }

    private fun Connection.importBlocks(): LegacyMigrationScopeResult {
        if (!hasTable(this, "blacklist")) return LegacyMigrationScopeResult(LegacyMigrationScope.BLOCKS, 0, 0, 0)

        var inserted = 0
        var skipped = 0
        query("SELECT blocker_uid, blocked_uid FROM blacklist") { rs ->
            while (rs.next()) {
                val ownerUid = uidString(rs.getString("blocker_uid")) ?: continue
                val blockedUid = uidString(rs.getString("blocked_uid")) ?: continue
                val exists = query(
                    "SELECT 1 FROM $currentBlocksTable WHERE user_uid = ? AND blocked_uid = ? LIMIT 1",
                    ownerUid,
                    blockedUid
                ) { current -> current.next() }
                if (exists) {
                    skipped++
                    continue
                }
                update(
                    "INSERT INTO $currentBlocksTable (user_uid, blocked_uid) VALUES (?, ?)",
                    ownerUid,
                    blockedUid
                )
                inserted++
            }
        }
        return LegacyMigrationScopeResult(LegacyMigrationScope.BLOCKS, inserted, 0, skipped)
    }

    private fun Connection.importSettings(): LegacyMigrationScopeResult {
        var inserted = 0
        var updated = 0
        var skipped = 0

        data class PreferenceRow(
            val notifyOnJoin: Boolean,
            val notifyOwnFriends: Boolean,
            val tpMode: Int,
            val lastOnline: Long
        )

        if (hasTable(this, "tp_permissions")) {
            query("SELECT player_uid, allowed FROM tp_permissions") { rs ->
                while (rs.next()) {
                    val uid = uidString(rs.getString("player_uid")) ?: continue
                    val allow = rs.getInt("allowed") != 0
                    val current = query(
                        "SELECT notify_on_join, notify_own_friends, tp_mode, last_online FROM $currentPreferencesTable WHERE uid = ?",
                        uid
                    ) { current ->
                        if (!current.next()) return@query null
                        PreferenceRow(
                            notifyOnJoin = current.getBoolean("notify_on_join"),
                            notifyOwnFriends = current.getBoolean("notify_own_friends"),
                            tpMode = current.getInt("tp_mode"),
                            lastOnline = current.getLong("last_online")
                        )
                    }
                    if (current == null) {
                        update(
                            "INSERT INTO $currentPreferencesTable (uid, notify_on_join, notify_own_friends, tp_allowed, tp_mode, last_online) VALUES (?, ?, ?, ?, ?, ?)",
                            uid,
                            true,
                            true,
                            allow,
                            if (allow) FriendTeleportMode.DIRECT.value else FriendTeleportMode.DENY.value,
                            0L
                        )
                        inserted++
                    } else if (!allow && current.tpMode == FriendTeleportMode.DIRECT.value) {
                        update(
                            "UPDATE $currentPreferencesTable SET tp_allowed = ?, tp_mode = ? WHERE uid = ?",
                            false,
                            FriendTeleportMode.DENY.value,
                            uid
                        )
                        updated++
                    } else {
                        skipped++
                    }
                }
            }
        }

        if (hasTable(this, "friend_notify_settings")) {
            query("SELECT uid, notify_on_join, notify_own_friends FROM friend_notify_settings") { rs ->
                while (rs.next()) {
                    val uid = uidString(rs.getString("uid")) ?: continue
                    val notifyOnJoin = rs.getInt("notify_on_join") != 0
                    val notifyOwnFriends = rs.getInt("notify_own_friends") != 0
                    val current = query(
                        "SELECT notify_on_join, notify_own_friends, tp_mode, last_online FROM $currentPreferencesTable WHERE uid = ?",
                        uid
                    ) { current ->
                        if (!current.next()) return@query null
                        PreferenceRow(
                            notifyOnJoin = current.getBoolean("notify_on_join"),
                            notifyOwnFriends = current.getBoolean("notify_own_friends"),
                            tpMode = current.getInt("tp_mode"),
                            lastOnline = current.getLong("last_online")
                        )
                    }
                    if (current == null) {
                        update(
                            "INSERT INTO $currentPreferencesTable (uid, notify_on_join, notify_own_friends, tp_allowed, tp_mode, last_online) VALUES (?, ?, ?, ?, ?, ?)",
                            uid,
                            notifyOnJoin,
                            notifyOwnFriends,
                            true,
                            FriendTeleportMode.DIRECT.value,
                            0L
                        )
                        inserted++
                        continue
                    }

                    val mergedNotifyOnJoin = if (current.notifyOnJoin && !notifyOnJoin) false else current.notifyOnJoin
                    val mergedNotifyOwnFriends = if (current.notifyOwnFriends && !notifyOwnFriends) false else current.notifyOwnFriends
                    if (mergedNotifyOnJoin == current.notifyOnJoin && mergedNotifyOwnFriends == current.notifyOwnFriends) {
                        skipped++
                        continue
                    }
                    update(
                        "UPDATE $currentPreferencesTable SET notify_on_join = ?, notify_own_friends = ? WHERE uid = ?",
                        mergedNotifyOnJoin,
                        mergedNotifyOwnFriends,
                        uid
                    )
                    updated++
                }
            }
        }

        if (hasTable(this, "friend_last_online")) {
            query("SELECT uid, last_online FROM friend_last_online") { rs ->
                while (rs.next()) {
                    val uid = uidString(rs.getString("uid")) ?: continue
                    val lastOnline = rs.getLong("last_online").coerceAtLeast(0L)
                    val current = query(
                        "SELECT notify_on_join, notify_own_friends, tp_mode, last_online FROM $currentPreferencesTable WHERE uid = ?",
                        uid
                    ) { current ->
                        if (!current.next()) return@query null
                        PreferenceRow(
                            notifyOnJoin = current.getBoolean("notify_on_join"),
                            notifyOwnFriends = current.getBoolean("notify_own_friends"),
                            tpMode = current.getInt("tp_mode"),
                            lastOnline = current.getLong("last_online")
                        )
                    }
                    if (current == null) {
                        update(
                            "INSERT INTO $currentPreferencesTable (uid, notify_on_join, notify_own_friends, tp_allowed, tp_mode, last_online) VALUES (?, ?, ?, ?, ?, ?)",
                            uid,
                            true,
                            true,
                            true,
                            FriendTeleportMode.DIRECT.value,
                            lastOnline
                        )
                        inserted++
                        continue
                    }
                    if (lastOnline <= current.lastOnline) {
                        skipped++
                        continue
                    }
                    update("UPDATE $currentPreferencesTable SET last_online = ? WHERE uid = ?", lastOnline, uid)
                    updated++
                }
            }
        }

        data class PersonalRow(
            val teleport: Int,
            val notifyReceive: Int,
            val notifyBroadcast: Int
        )

        if (hasTable(this, "individual_friend_settings")) {
            query("SELECT player_uid, friend_uid, tp_state, notify_receive, notify_broadcast FROM individual_friend_settings") { rs ->
                while (rs.next()) {
                    val ownerUid = uidString(rs.getString("player_uid")) ?: continue
                    val friendUid = uidString(rs.getString("friend_uid")) ?: continue
                    val legacyTeleport = legacyPersonalState(rs.getInt("tp_state"), allowConfirm = true)
                    val legacyNotifyReceive = legacyPersonalState(rs.getInt("notify_receive"))
                    val legacyNotifyBroadcast = legacyPersonalState(rs.getInt("notify_broadcast"))
                    val current = query(
                        "SELECT teleport_state, notify_receive_state, notify_broadcast_state FROM $currentPersonalPreferencesTable WHERE owner_uid = ? AND friend_uid = ?",
                        ownerUid,
                        friendUid
                    ) { current ->
                        if (!current.next()) return@query null
                        PersonalRow(
                            teleport = current.getInt("teleport_state"),
                            notifyReceive = current.getInt("notify_receive_state"),
                            notifyBroadcast = current.getInt("notify_broadcast_state")
                        )
                    }
                    if (current == null) {
                        update(
                            "INSERT INTO $currentPersonalPreferencesTable (" +
                                "owner_uid, friend_uid, teleport_state, notify_receive_state, notify_broadcast_state, " +
                                "status_like_notice_state, status_comment_notice_state, wall_post_notice_state, wall_like_notice_state, wall_comment_notice_state" +
                                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            ownerUid,
                            friendUid,
                            legacyTeleport,
                            legacyNotifyReceive,
                            legacyNotifyBroadcast,
                            0,
                            0,
                            0,
                            0,
                            0
                        )
                        inserted++
                        continue
                    }

                    val mergedTeleport = if (current.teleport == FriendPersonalState.DEFAULT.value && legacyTeleport != FriendPersonalState.DEFAULT.value) legacyTeleport else current.teleport
                    val mergedNotifyReceive = if (current.notifyReceive == FriendPersonalState.DEFAULT.value && legacyNotifyReceive != FriendPersonalState.DEFAULT.value) legacyNotifyReceive else current.notifyReceive
                    val mergedNotifyBroadcast = if (current.notifyBroadcast == FriendPersonalState.DEFAULT.value && legacyNotifyBroadcast != FriendPersonalState.DEFAULT.value) legacyNotifyBroadcast else current.notifyBroadcast
                    if (mergedTeleport == current.teleport && mergedNotifyReceive == current.notifyReceive && mergedNotifyBroadcast == current.notifyBroadcast) {
                        skipped++
                        continue
                    }
                    update(
                        "UPDATE $currentPersonalPreferencesTable SET teleport_state = ?, notify_receive_state = ?, notify_broadcast_state = ? WHERE owner_uid = ? AND friend_uid = ?",
                        mergedTeleport,
                        mergedNotifyReceive,
                        mergedNotifyBroadcast,
                        ownerUid,
                        friendUid
                    )
                    updated++
                }
            }
        }

        return LegacyMigrationScopeResult(LegacyMigrationScope.SETTINGS, inserted, updated, skipped)
    }

    private fun Connection.importProfiles(): LegacyMigrationScopeResult {
        if (!hasTable(this, "profile")) return LegacyMigrationScopeResult(LegacyMigrationScope.PROFILE, 0, 0, 0)

        data class CurrentProfileRow(
            val birthday: String?,
            val birthdaySets: Int,
            val lastBirthdayReminder: String?
        )

        var inserted = 0
        var updated = 0
        var skipped = 0
        query("SELECT uid, birthday, totalSets, last_remind_date FROM profile") { rs ->
            while (rs.next()) {
                val uid = uidString(rs.getString("uid")) ?: continue
                val legacyBirthday = normalizeLegacyBirthday(rs.getString("birthday"))
                val legacySets = rs.getInt("totalSets").coerceAtLeast(0)
                val legacyReminder = rs.getString("last_remind_date")?.trim()?.takeIf { it.isNotBlank() }
                val current = query(
                    "SELECT birthday, birthday_sets, last_birthday_reminder FROM $currentProfilesTable WHERE uid = ?",
                    uid
                ) { current ->
                    if (!current.next()) return@query null
                    CurrentProfileRow(
                        birthday = current.getString("birthday"),
                        birthdaySets = current.getInt("birthday_sets"),
                        lastBirthdayReminder = current.getString("last_birthday_reminder")
                    )
                }

                if (current == null) {
                    update(
                        "INSERT INTO $currentProfilesTable (" +
                            "uid, bio, birthday, allow_requests, allow_msg, " +
                            "notify_status_like, notify_status_comment, notify_wall_post, notify_wall_like, notify_wall_comment, " +
                            "vanish_mode, birthday_sets, last_birthday_reminder, last_birthday_broadcast" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        uid,
                        ProfileRepository.DEFAULT_BIO,
                        legacyBirthday ?: "0000-00-00",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        false,
                        legacySets,
                        legacyReminder,
                        null
                    )
                    inserted++
                    continue
                }

                val currentBirthday = current.birthday?.takeUnless { it == "0000-00-00" || it.isBlank() }
                val mergedBirthday = currentBirthday ?: legacyBirthday
                val mergedSets = maxOf(current.birthdaySets, legacySets)
                val mergedReminder = current.lastBirthdayReminder ?: legacyReminder
                val changed = mergedBirthday != currentBirthday ||
                    mergedSets != current.birthdaySets ||
                    mergedReminder != current.lastBirthdayReminder
                if (!changed) {
                    skipped++
                    continue
                }
                update(
                    "UPDATE $currentProfilesTable SET birthday = ?, birthday_sets = ?, last_birthday_reminder = ? WHERE uid = ?",
                    mergedBirthday ?: "0000-00-00",
                    mergedSets,
                    mergedReminder,
                    uid
                )
                updated++
            }
        }
        return LegacyMigrationScopeResult(LegacyMigrationScope.PROFILE, inserted, updated, skipped)
    }

    private fun Connection.importChatMessages(): LegacyMigrationScopeResult {
        if (!hasTable(this, "offline_messages")) return LegacyMigrationScopeResult(LegacyMigrationScope.CHAT, 0, 0, 0)

        var inserted = 0
        var skipped = 0
        query("SELECT target_uid, sender_uid, message, timestamp, is_read FROM offline_messages ORDER BY timestamp ASC") { rs ->
            while (rs.next()) {
                val receiverUid = uidString(rs.getString("target_uid")) ?: continue
                val senderUid = uidString(rs.getString("sender_uid")) ?: continue
                val content = rs.getString("message")?.trim().orEmpty()
                if (content.isBlank()) {
                    skipped++
                    continue
                }
                val createdAt = rs.getLong("timestamp").coerceAtLeast(0L)
                val isRead = rs.getInt("is_read") != 0
                val exists = query(
                    "SELECT 1 FROM $currentChatTable WHERE sender_uid = ? AND receiver_uid = ? AND content = ? AND created_at = ? LIMIT 1",
                    senderUid,
                    receiverUid,
                    content,
                    createdAt
                ) { current -> current.next() }
                if (exists) {
                    skipped++
                    continue
                }
                update(
                    "INSERT INTO $currentChatTable (sender_uid, receiver_uid, content, created_at, is_read) VALUES (?, ?, ?, ?, ?)",
                    senderUid,
                    receiverUid,
                    content,
                    createdAt,
                    isRead
                )
                inserted++
            }
        }
        return LegacyMigrationScopeResult(LegacyMigrationScope.CHAT, inserted, 0, skipped)
    }

    private fun Connection.importSocialContent(): LegacyMigrationScopeResult {
        var inserted = 0
        var skipped = 0

        if (hasTable(this, "friend_status_updates")) {
            query("SELECT uid, message, timestamp FROM friend_status_updates ORDER BY timestamp ASC") { rs ->
                while (rs.next()) {
                    val uid = uidString(rs.getString("uid")) ?: continue
                    val content = rs.getString("message")?.trim().orEmpty()
                    if (content.isBlank()) {
                        skipped++
                        continue
                    }
                    val createdAt = rs.getLong("timestamp").coerceAtLeast(0L)
                    val exists = query(
                        "SELECT 1 FROM $currentStatusTable WHERE uid = ? AND content = ? AND created_at = ? LIMIT 1",
                        uid,
                        content,
                        createdAt
                    ) { current -> current.next() }
                    if (exists) {
                        skipped++
                        continue
                    }
                    update(
                        "INSERT INTO $currentStatusTable (uid, content, visibility, pinned, created_at) VALUES (?, ?, ?, ?, ?)",
                        uid,
                        content,
                        "PUBLIC",
                        0,
                        createdAt
                    )
                    inserted++
                }
            }
        }

        if (hasTable(this, "wall_messages")) {
            query("SELECT owner_uid, sender_uid, message, timestamp FROM wall_messages ORDER BY timestamp ASC") { rs ->
                while (rs.next()) {
                    val ownerUid = uidString(rs.getString("owner_uid")) ?: continue
                    val authorUid = uidString(rs.getString("sender_uid")) ?: continue
                    val content = rs.getString("message")?.trim().orEmpty()
                    if (content.isBlank()) {
                        skipped++
                        continue
                    }
                    val createdAt = rs.getLong("timestamp").coerceAtLeast(0L)
                    val exists = query(
                        "SELECT 1 FROM $currentWallTable WHERE owner_uid = ? AND author_uid = ? AND content = ? AND created_at = ? LIMIT 1",
                        ownerUid,
                        authorUid,
                        content,
                        createdAt
                    ) { current -> current.next() }
                    if (exists) {
                        skipped++
                        continue
                    }
                    update(
                        "INSERT INTO $currentWallTable (owner_uid, author_uid, content, visibility, approved, pinned, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        ownerUid,
                        authorUid,
                        content,
                        "PUBLIC",
                        1,
                        0,
                        createdAt
                    )
                    inserted++
                }
            }
        }

        return LegacyMigrationScopeResult(LegacyMigrationScope.SOCIAL, inserted, 0, skipped)
    }

    private fun activeScopes(): Set<LegacyMigrationScope> {
        val scopes = linkedSetOf(
            LegacyMigrationScope.FRIENDS,
            LegacyMigrationScope.REQUESTS,
            LegacyMigrationScope.BLOCKS,
            LegacyMigrationScope.SETTINGS
        )
        if (plugin.moduleManager.isEnabled("profile")) {
            scopes += LegacyMigrationScope.PROFILE
        }
        if (plugin.moduleManager.isEnabled("chat")) {
            scopes += LegacyMigrationScope.CHAT
        }
        if (plugin.moduleManager.isEnabled("social")) {
            scopes += LegacyMigrationScope.SOCIAL
        }
        return scopes
    }

    private fun invalidateRuntimeCaches() {
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val chatModule = plugin.moduleManager.getModule<org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule>("chat")
        val profileModule = plugin.moduleManager.getModule<org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule>("profile")
        val socialModule = plugin.moduleManager.getModule<org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule>("social")

        org.bukkit.Bukkit.getOnlinePlayers().forEach { player ->
            val uid = player.uid
            friendModule?.friendManager?.invalidate(uid)
            friendModule?.requestManager?.invalidate(uid)
            friendModule?.blockManager?.invalidate(uid)
            friendModule?.preferencesManager?.invalidate(uid)
            profileModule?.manager?.invalidate(uid)
            chatModule?.manager?.invalidate(uid)
            socialModule?.manager?.invalidateStatusCache(uid)
            socialModule?.manager?.invalidateWallCache(uid)
        }
    }

    private fun hasTable(connection: Connection, tableName: String): Boolean {
        connection.metaData.getTables(null, null, tableName, null).use { rs ->
            while (rs.next()) {
                val actual = rs.getString("TABLE_NAME") ?: continue
                if (actual.equals(tableName, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun tableRowCount(connection: Connection, tableName: String): Int {
        return runCatching {
            connection.query("SELECT COUNT(*) FROM $tableName") { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }.getOrDefault(0)
    }

    private fun uidString(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeLegacyNote(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        return normalized.takeIf { it.isNotBlank() && it != "无备注" }
    }

    private fun normalizeLegacyGroup(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        return normalized.takeIf { it.isNotBlank() && it != "未分组" }
    }

    private fun normalizeLegacyBirthday(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        return normalized.takeIf { it.isNotBlank() && it != "未设置" && it != "0000-00-00" }
    }

    private fun legacyPersonalState(value: Int, allowConfirm: Boolean = false): Int {
        return when (value) {
            1 -> FriendPersonalState.ALLOW.value
            2 -> FriendPersonalState.DENY.value
            3 -> if (allowConfirm) FriendPersonalState.CONFIRM.value else FriendPersonalState.DEFAULT.value
            else -> FriendPersonalState.DEFAULT.value
        }
    }
}
