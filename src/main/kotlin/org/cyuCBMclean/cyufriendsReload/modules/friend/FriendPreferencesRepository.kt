package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

data class FriendPreferences(
    val uid: String,
    val notifyOnJoin: Boolean = true,
    val notifyOwnFriends: Boolean = true,
    val teleportMode: FriendTeleportMode = FriendTeleportMode.DIRECT,
    val lastOnline: Long = 0L
) {
    val tpAllowed: Boolean
        get() = teleportMode != FriendTeleportMode.DENY
}

enum class FriendTeleportMode(val value: Int, val displayName: String, val id: String) {
    DIRECT(1, "允许直达", "direct"),
    CONFIRM(2, "需要确认", "confirm"),
    DENY(3, "拒绝传送", "deny");

    fun next(): FriendTeleportMode = when (this) {
        DIRECT -> CONFIRM
        CONFIRM -> DENY
        DENY -> DIRECT
    }

    companion object {
        fun of(value: Int): FriendTeleportMode = entries.firstOrNull { it.value == value } ?: DIRECT

        fun fromAllowed(allowed: Boolean): FriendTeleportMode = if (allowed) DIRECT else DENY

        fun fromId(id: String?): FriendTeleportMode? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

enum class FriendPersonalState(val value: Int, private val baseDisplayName: String) {
    DEFAULT(0, "继承全局"),
    ALLOW(1, "允许"),
    DENY(2, "拒绝"),
    CONFIRM(3, "需要确认");

    fun next(type: FriendPersonalType): FriendPersonalState = when (type) {
        FriendPersonalType.TELEPORT -> when (this) {
            DEFAULT -> ALLOW
            ALLOW -> CONFIRM
            CONFIRM -> DENY
            DENY -> DEFAULT
        }
        FriendPersonalType.NOTIFY_RECEIVE,
        FriendPersonalType.NOTIFY_BROADCAST,
        FriendPersonalType.STATUS_LIKE_NOTICE,
        FriendPersonalType.STATUS_COMMENT_NOTICE,
        FriendPersonalType.WALL_POST_NOTICE,
        FriendPersonalType.WALL_LIKE_NOTICE,
        FriendPersonalType.WALL_COMMENT_NOTICE -> when (this) {
            DEFAULT -> ALLOW
            ALLOW -> DENY
            DENY,
            CONFIRM -> DEFAULT
        }
    }

    fun displayName(type: FriendPersonalType): String = when (type) {
        FriendPersonalType.TELEPORT -> when (this) {
            DEFAULT -> "继承全局"
            ALLOW -> "允许直达"
            CONFIRM -> "需要确认"
            DENY -> "拒绝传送"
        }
        FriendPersonalType.NOTIFY_RECEIVE,
        FriendPersonalType.NOTIFY_BROADCAST,
        FriendPersonalType.STATUS_LIKE_NOTICE,
        FriendPersonalType.STATUS_COMMENT_NOTICE,
        FriendPersonalType.WALL_POST_NOTICE,
        FriendPersonalType.WALL_LIKE_NOTICE,
        FriendPersonalType.WALL_COMMENT_NOTICE -> baseDisplayName
    }

    fun resolve(globalMode: FriendTeleportMode): FriendTeleportMode = when (this) {
        DEFAULT -> globalMode
        ALLOW -> FriendTeleportMode.DIRECT
        CONFIRM -> FriendTeleportMode.CONFIRM
        DENY -> FriendTeleportMode.DENY
    }

    companion object {
        fun of(value: Int): FriendPersonalState = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

enum class FriendPersonalType(val displayName: String) {
    TELEPORT("好友传送"),
    NOTIFY_RECEIVE("上线提醒"),
    NOTIFY_BROADCAST("上线可见"),
    STATUS_LIKE_NOTICE("动态点赞提醒"),
    STATUS_COMMENT_NOTICE("动态评论提醒"),
    WALL_POST_NOTICE("留言提醒"),
    WALL_LIKE_NOTICE("留言点赞提醒"),
    WALL_COMMENT_NOTICE("留言评论提醒")
}

data class FriendPersonalPreferences(
    val ownerUid: String,
    val friendUid: String,
    val teleport: FriendPersonalState = FriendPersonalState.DEFAULT,
    val notifyReceive: FriendPersonalState = FriendPersonalState.DEFAULT,
    val notifyBroadcast: FriendPersonalState = FriendPersonalState.DEFAULT,
    val statusLikeNotice: FriendPersonalState = FriendPersonalState.DEFAULT,
    val statusCommentNotice: FriendPersonalState = FriendPersonalState.DEFAULT,
    val wallPostNotice: FriendPersonalState = FriendPersonalState.DEFAULT,
    val wallLikeNotice: FriendPersonalState = FriendPersonalState.DEFAULT,
    val wallCommentNotice: FriendPersonalState = FriendPersonalState.DEFAULT
)

data class FriendGroupPreferences(
    val ownerUid: String,
    val groupName: String,
    val teleport: FriendPersonalState = FriendPersonalState.DEFAULT,
    val notifyReceive: FriendPersonalState = FriendPersonalState.DEFAULT,
    val notifyBroadcast: FriendPersonalState = FriendPersonalState.DEFAULT,
    val pinned: Boolean = false
)

class FriendPreferencesRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_friend_preferences"
    private val personalTable = "cyu_friend_personal_preferences"
    private val groupTable = "cyu_friend_group_preferences"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            update("CREATE TABLE IF NOT EXISTS $tableName (uid VARCHAR(36) PRIMARY KEY, notify_on_join BOOLEAN DEFAULT 1, notify_own_friends BOOLEAN DEFAULT 1, tp_allowed BOOLEAN DEFAULT 1, tp_mode INT DEFAULT 0, last_online BIGINT DEFAULT 0)")
            update(
                "CREATE TABLE IF NOT EXISTS $personalTable (" +
                    "owner_uid VARCHAR(36), " +
                    "friend_uid VARCHAR(36), " +
                    "teleport_state INT DEFAULT 0, " +
                    "notify_receive_state INT DEFAULT 0, " +
                    "notify_broadcast_state INT DEFAULT 0, " +
                    "status_like_notice_state INT DEFAULT 0, " +
                    "status_comment_notice_state INT DEFAULT 0, " +
                    "wall_post_notice_state INT DEFAULT 0, " +
                    "wall_like_notice_state INT DEFAULT 0, " +
                    "wall_comment_notice_state INT DEFAULT 0, " +
                    "PRIMARY KEY(owner_uid, friend_uid))"
            )
            update("CREATE TABLE IF NOT EXISTS $groupTable (owner_uid VARCHAR(36), group_name VARCHAR(64), teleport_state INT DEFAULT 0, notify_receive_state INT DEFAULT 0, notify_broadcast_state INT DEFAULT 0, pinned INTEGER DEFAULT 0, PRIMARY KEY(owner_uid, group_name))")
            runCatching { update("ALTER TABLE $tableName ADD COLUMN notify_on_join BOOLEAN DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN notify_own_friends BOOLEAN DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN tp_allowed BOOLEAN DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN tp_mode INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN last_online BIGINT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN teleport_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN notify_receive_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN notify_broadcast_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN status_like_notice_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN status_comment_notice_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN wall_post_notice_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN wall_like_notice_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $personalTable ADD COLUMN wall_comment_notice_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $groupTable ADD COLUMN teleport_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $groupTable ADD COLUMN notify_receive_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $groupTable ADD COLUMN notify_broadcast_state INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $groupTable ADD COLUMN pinned INTEGER DEFAULT 0") }
            if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_last_online ON $tableName (last_online)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${personalTable}_friend ON $personalTable (friend_uid)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${groupTable}_owner ON $groupTable (owner_uid)")
                }
            } else {
                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_last_online (last_online)")
                }

                runCatching {
                    update("ALTER TABLE $personalTable ADD INDEX idx_${personalTable}_friend (friend_uid)")
                }

                runCatching {
                    update("ALTER TABLE $groupTable ADD INDEX idx_${groupTable}_owner (owner_uid)")
                }
            }
        }
    }

    suspend fun find(uid: String): FriendPreferences? = db.execute {
        readPreferences(uid)
    }

    fun findSync(uid: String): FriendPreferences? = db.executeSync {
        readPreferences(uid)
    }

    suspend fun save(preferences: FriendPreferences) = db.execute {
        val updated = update(
            "UPDATE $tableName SET notify_on_join = ?, notify_own_friends = ?, tp_allowed = ?, tp_mode = ?, last_online = ? WHERE uid = ?",
            preferences.notifyOnJoin,
            preferences.notifyOwnFriends,
            preferences.tpAllowed,
            preferences.teleportMode.value,
            preferences.lastOnline,
            preferences.uid
        )
        if (updated == 0) {
            val inserted = runCatching {
                update(
                    "INSERT INTO $tableName (uid, notify_on_join, notify_own_friends, tp_allowed, tp_mode, last_online) VALUES (?, ?, ?, ?, ?, ?)",
                    preferences.uid,
                    preferences.notifyOnJoin,
                    preferences.notifyOwnFriends,
                    preferences.tpAllowed,
                    preferences.teleportMode.value,
                    preferences.lastOnline
                )
            }.isSuccess

            if (!inserted) {
                update(
                    "UPDATE $tableName SET notify_on_join = ?, notify_own_friends = ?, tp_allowed = ?, tp_mode = ?, last_online = ? WHERE uid = ?",
                    preferences.notifyOnJoin,
                    preferences.notifyOwnFriends,
                    preferences.tpAllowed,
                    preferences.teleportMode.value,
                    preferences.lastOnline,
                    preferences.uid
                )
            }
        }
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        val old = readPreferences(oldUid, newUid) ?: return@execute

        val targetExists = query("SELECT uid FROM $tableName WHERE uid = ?", newUid) { rs -> rs.next() }
        if (targetExists) {
            update(
                "UPDATE $tableName SET notify_on_join = ?, notify_own_friends = ?, tp_allowed = ?, tp_mode = ?, last_online = ? WHERE uid = ?",
                old.notifyOnJoin,
                old.notifyOwnFriends,
                old.tpAllowed,
                old.teleportMode.value,
                old.lastOnline,
                newUid
            )
            update("DELETE FROM $tableName WHERE uid = ?", oldUid)
        } else {
            update("UPDATE $tableName SET uid = ? WHERE uid = ?", newUid, oldUid)
        }
    }

    suspend fun findPersonal(ownerUid: String, friendUid: String): FriendPersonalPreferences? = db.execute {
        readPersonal(ownerUid, friendUid)
    }

    fun findPersonalSync(ownerUid: String, friendUid: String): FriendPersonalPreferences? = db.executeSync {
        readPersonal(ownerUid, friendUid)
    }

    suspend fun findPersonalByOwner(ownerUid: String): Map<String, FriendPersonalPreferences> = db.execute {
        readPersonalByOwner(ownerUid)
    }

    fun findPersonalByOwnerSync(ownerUid: String): Map<String, FriendPersonalPreferences> = db.executeSync {
        readPersonalByOwner(ownerUid)
    }

    suspend fun savePersonal(preferences: FriendPersonalPreferences) = db.execute {
        val updated = update(
            "UPDATE $personalTable SET teleport_state = ?, notify_receive_state = ?, notify_broadcast_state = ?, " +
                "status_like_notice_state = ?, status_comment_notice_state = ?, wall_post_notice_state = ?, wall_like_notice_state = ?, wall_comment_notice_state = ? " +
                "WHERE owner_uid = ? AND friend_uid = ?",
            preferences.teleport.value,
            preferences.notifyReceive.value,
            preferences.notifyBroadcast.value,
            preferences.statusLikeNotice.value,
            preferences.statusCommentNotice.value,
            preferences.wallPostNotice.value,
            preferences.wallLikeNotice.value,
            preferences.wallCommentNotice.value,
            preferences.ownerUid,
            preferences.friendUid
        )
        if (updated == 0) {
            val inserted = runCatching {
                update(
                    "INSERT INTO $personalTable (" +
                        "owner_uid, friend_uid, teleport_state, notify_receive_state, notify_broadcast_state, " +
                        "status_like_notice_state, status_comment_notice_state, wall_post_notice_state, wall_like_notice_state, wall_comment_notice_state" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    preferences.ownerUid,
                    preferences.friendUid,
                    preferences.teleport.value,
                    preferences.notifyReceive.value,
                    preferences.notifyBroadcast.value,
                    preferences.statusLikeNotice.value,
                    preferences.statusCommentNotice.value,
                    preferences.wallPostNotice.value,
                    preferences.wallLikeNotice.value,
                    preferences.wallCommentNotice.value
                )
            }.isSuccess

            if (!inserted) {
                update(
                    "UPDATE $personalTable SET teleport_state = ?, notify_receive_state = ?, notify_broadcast_state = ?, " +
                        "status_like_notice_state = ?, status_comment_notice_state = ?, wall_post_notice_state = ?, wall_like_notice_state = ?, wall_comment_notice_state = ? " +
                        "WHERE owner_uid = ? AND friend_uid = ?",
                    preferences.teleport.value,
                    preferences.notifyReceive.value,
                    preferences.notifyBroadcast.value,
                    preferences.statusLikeNotice.value,
                    preferences.statusCommentNotice.value,
                    preferences.wallPostNotice.value,
                    preferences.wallLikeNotice.value,
                    preferences.wallCommentNotice.value,
                    preferences.ownerUid,
                    preferences.friendUid
                )
            }
        }
    }

    suspend fun findGroup(ownerUid: String, groupName: String): FriendGroupPreferences? = db.execute {
        readGroup(ownerUid, groupName)
    }

    fun findGroupSync(ownerUid: String, groupName: String): FriendGroupPreferences? = db.executeSync {
        readGroup(ownerUid, groupName)
    }

    suspend fun findGroupsByOwner(ownerUid: String): Map<String, FriendGroupPreferences> = db.execute {
        readGroupsByOwner(ownerUid)
    }

    fun findGroupsByOwnerSync(ownerUid: String): Map<String, FriendGroupPreferences> = db.executeSync {
        readGroupsByOwner(ownerUid)
    }

    suspend fun saveGroup(preferences: FriendGroupPreferences) = db.execute {
        val updated = update(
            "UPDATE $groupTable SET teleport_state = ?, notify_receive_state = ?, notify_broadcast_state = ?, pinned = ? WHERE owner_uid = ? AND group_name = ?",
            preferences.teleport.value,
            preferences.notifyReceive.value,
            preferences.notifyBroadcast.value,
            if (preferences.pinned) 1 else 0,
            preferences.ownerUid,
            preferences.groupName
        )
        if (updated == 0) {
            val inserted = runCatching {
                update(
                    "INSERT INTO $groupTable (owner_uid, group_name, teleport_state, notify_receive_state, notify_broadcast_state, pinned) VALUES (?, ?, ?, ?, ?, ?)",
                    preferences.ownerUid,
                    preferences.groupName,
                    preferences.teleport.value,
                    preferences.notifyReceive.value,
                    preferences.notifyBroadcast.value,
                    if (preferences.pinned) 1 else 0
                )
            }.isSuccess

            if (!inserted) {
                update(
                    "UPDATE $groupTable SET teleport_state = ?, notify_receive_state = ?, notify_broadcast_state = ?, pinned = ? WHERE owner_uid = ? AND group_name = ?",
                    preferences.teleport.value,
                    preferences.notifyReceive.value,
                    preferences.notifyBroadcast.value,
                    if (preferences.pinned) 1 else 0,
                    preferences.ownerUid,
                    preferences.groupName
                )
            }
        }
    }

    suspend fun deletePersonal(ownerUid: String, friendUid: String) = db.execute {
        update("DELETE FROM $personalTable WHERE owner_uid = ? AND friend_uid = ?", ownerUid, friendUid)
    }

    suspend fun updatePersonalUid(oldUid: String, newUid: String) = db.execute {
        val rows = query(
            "SELECT owner_uid, friend_uid, teleport_state, notify_receive_state, notify_broadcast_state, " +
                "status_like_notice_state, status_comment_notice_state, wall_post_notice_state, wall_like_notice_state, wall_comment_notice_state " +
                "FROM $personalTable WHERE owner_uid = ? OR friend_uid = ?",
            oldUid,
            oldUid
        ) { rs ->
            val result = arrayListOf<PersonalRow>()
            while (rs.next()) {
                result += PersonalRow(
                    rs.getString("owner_uid"),
                    rs.getString("friend_uid"),
                    rs.getInt("teleport_state"),
                    rs.getInt("notify_receive_state"),
                    rs.getInt("notify_broadcast_state"),
                    rs.getInt("status_like_notice_state"),
                    rs.getInt("status_comment_notice_state"),
                    rs.getInt("wall_post_notice_state"),
                    rs.getInt("wall_like_notice_state"),
                    rs.getInt("wall_comment_notice_state")
                )
            }
            result
        }
        if (rows.isEmpty()) return@execute

        update("DELETE FROM $personalTable WHERE owner_uid = ? OR friend_uid = ?", oldUid, oldUid)

        rows.forEach { row ->
            val next = row.copy(
                ownerUid = if (row.ownerUid == oldUid) newUid else row.ownerUid,
                friendUid = if (row.friendUid == oldUid) newUid else row.friendUid
            )
            val existing = query(
                "SELECT teleport_state, notify_receive_state, notify_broadcast_state, " +
                    "status_like_notice_state, status_comment_notice_state, wall_post_notice_state, wall_like_notice_state, wall_comment_notice_state " +
                    "FROM $personalTable WHERE owner_uid = ? AND friend_uid = ?",
                next.ownerUid,
                next.friendUid
            ) { rs ->
                if (!rs.next()) return@query null
                PersonalRow(
                    next.ownerUid,
                    next.friendUid,
                    rs.getInt("teleport_state"),
                    rs.getInt("notify_receive_state"),
                    rs.getInt("notify_broadcast_state"),
                    rs.getInt("status_like_notice_state"),
                    rs.getInt("status_comment_notice_state"),
                    rs.getInt("wall_post_notice_state"),
                    rs.getInt("wall_like_notice_state"),
                    rs.getInt("wall_comment_notice_state")
                )
            }
            val merged = if (existing == null) {
                next
            } else {
                next.copy(
                    teleport = mergePersonalState(next.teleport, existing.teleport),
                    notifyReceive = mergePersonalState(next.notifyReceive, existing.notifyReceive),
                    notifyBroadcast = mergePersonalState(next.notifyBroadcast, existing.notifyBroadcast),
                    statusLikeNotice = mergePersonalState(next.statusLikeNotice, existing.statusLikeNotice),
                    statusCommentNotice = mergePersonalState(next.statusCommentNotice, existing.statusCommentNotice),
                    wallPostNotice = mergePersonalState(next.wallPostNotice, existing.wallPostNotice),
                    wallLikeNotice = mergePersonalState(next.wallLikeNotice, existing.wallLikeNotice),
                    wallCommentNotice = mergePersonalState(next.wallCommentNotice, existing.wallCommentNotice)
                )
            }
            val updated = update(
                "UPDATE $personalTable SET teleport_state = ?, notify_receive_state = ?, notify_broadcast_state = ?, " +
                    "status_like_notice_state = ?, status_comment_notice_state = ?, wall_post_notice_state = ?, wall_like_notice_state = ?, wall_comment_notice_state = ? " +
                    "WHERE owner_uid = ? AND friend_uid = ?",
                merged.teleport,
                merged.notifyReceive,
                merged.notifyBroadcast,
                merged.statusLikeNotice,
                merged.statusCommentNotice,
                merged.wallPostNotice,
                merged.wallLikeNotice,
                merged.wallCommentNotice,
                merged.ownerUid,
                merged.friendUid
            )
            if (updated == 0) {
                update(
                    "INSERT INTO $personalTable (" +
                        "owner_uid, friend_uid, teleport_state, notify_receive_state, notify_broadcast_state, " +
                        "status_like_notice_state, status_comment_notice_state, wall_post_notice_state, wall_like_notice_state, wall_comment_notice_state" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    merged.ownerUid,
                    merged.friendUid,
                    merged.teleport,
                    merged.notifyReceive,
                    merged.notifyBroadcast,
                    merged.statusLikeNotice,
                    merged.statusCommentNotice,
                    merged.wallPostNotice,
                    merged.wallLikeNotice,
                    merged.wallCommentNotice
                )
            }
        }

        update("UPDATE $groupTable SET owner_uid = ? WHERE owner_uid = ?", newUid, oldUid)
    }

    private fun java.sql.Connection.readPreferences(uid: String, mappedUid: String = uid): FriendPreferences? {
        return query("SELECT notify_on_join, notify_own_friends, tp_allowed, tp_mode, last_online FROM $tableName WHERE uid = ?", uid) { rs ->
            if (!rs.next()) return@query null
            val legacyAllowed = rs.getBoolean("tp_allowed")
            val rawMode = rs.getInt("tp_mode")
            FriendPreferences(
                uid = mappedUid,
                notifyOnJoin = rs.getBoolean("notify_on_join"),
                notifyOwnFriends = rs.getBoolean("notify_own_friends"),
                teleportMode = if (rawMode == 0) FriendTeleportMode.fromAllowed(legacyAllowed) else FriendTeleportMode.of(rawMode),
                lastOnline = rs.getLong("last_online")
            )
        }
    }

    private fun java.sql.Connection.readPersonal(ownerUid: String, friendUid: String): FriendPersonalPreferences? {
        return query(
            "SELECT teleport_state, notify_receive_state, notify_broadcast_state, " +
                "status_like_notice_state, status_comment_notice_state, wall_post_notice_state, wall_like_notice_state, wall_comment_notice_state " +
                "FROM $personalTable WHERE owner_uid = ? AND friend_uid = ?",
            ownerUid,
            friendUid
        ) { rs ->
            if (!rs.next()) return@query null
            FriendPersonalPreferences(
                ownerUid,
                friendUid,
                FriendPersonalState.of(rs.getInt("teleport_state")),
                FriendPersonalState.of(rs.getInt("notify_receive_state")),
                FriendPersonalState.of(rs.getInt("notify_broadcast_state")),
                FriendPersonalState.of(rs.getInt("status_like_notice_state")),
                FriendPersonalState.of(rs.getInt("status_comment_notice_state")),
                FriendPersonalState.of(rs.getInt("wall_post_notice_state")),
                FriendPersonalState.of(rs.getInt("wall_like_notice_state")),
                FriendPersonalState.of(rs.getInt("wall_comment_notice_state"))
            )
        }
    }

    private fun java.sql.Connection.readPersonalByOwner(ownerUid: String): Map<String, FriendPersonalPreferences> {
        return query(
            "SELECT friend_uid, teleport_state, notify_receive_state, notify_broadcast_state, " +
                "status_like_notice_state, status_comment_notice_state, wall_post_notice_state, wall_like_notice_state, wall_comment_notice_state " +
                "FROM $personalTable WHERE owner_uid = ?",
            ownerUid
        ) { rs ->
            val settings = linkedMapOf<String, FriendPersonalPreferences>()
            while (rs.next()) {
                val friendUid = rs.getString("friend_uid")
                settings[friendUid] = FriendPersonalPreferences(
                    ownerUid,
                    friendUid,
                    FriendPersonalState.of(rs.getInt("teleport_state")),
                    FriendPersonalState.of(rs.getInt("notify_receive_state")),
                    FriendPersonalState.of(rs.getInt("notify_broadcast_state")),
                    FriendPersonalState.of(rs.getInt("status_like_notice_state")),
                    FriendPersonalState.of(rs.getInt("status_comment_notice_state")),
                    FriendPersonalState.of(rs.getInt("wall_post_notice_state")),
                    FriendPersonalState.of(rs.getInt("wall_like_notice_state")),
                    FriendPersonalState.of(rs.getInt("wall_comment_notice_state"))
                )
            }
            settings
        }
    }

    private fun java.sql.Connection.readGroup(ownerUid: String, groupName: String): FriendGroupPreferences? {
        return query("SELECT teleport_state, notify_receive_state, notify_broadcast_state, pinned FROM $groupTable WHERE owner_uid = ? AND group_name = ?", ownerUid, groupName) { rs ->
            if (!rs.next()) return@query null
            FriendGroupPreferences(
                ownerUid = ownerUid,
                groupName = groupName,
                teleport = FriendPersonalState.of(rs.getInt("teleport_state")),
                notifyReceive = FriendPersonalState.of(rs.getInt("notify_receive_state")),
                notifyBroadcast = FriendPersonalState.of(rs.getInt("notify_broadcast_state")),
                pinned = rs.getInt("pinned") != 0
            )
        }
    }

    private fun java.sql.Connection.readGroupsByOwner(ownerUid: String): Map<String, FriendGroupPreferences> {
        return query("SELECT group_name, teleport_state, notify_receive_state, notify_broadcast_state, pinned FROM $groupTable WHERE owner_uid = ?", ownerUid) { rs ->
            val settings = linkedMapOf<String, FriendGroupPreferences>()
            while (rs.next()) {
                val groupName = rs.getString("group_name")
                settings[groupName] = FriendGroupPreferences(
                    ownerUid = ownerUid,
                    groupName = groupName,
                    teleport = FriendPersonalState.of(rs.getInt("teleport_state")),
                    notifyReceive = FriendPersonalState.of(rs.getInt("notify_receive_state")),
                    notifyBroadcast = FriendPersonalState.of(rs.getInt("notify_broadcast_state")),
                    pinned = rs.getInt("pinned") != 0
                )
            }
            settings
        }
    }

    private fun mergePersonalState(current: Int, existing: Int): Int {
        return if (current == FriendPersonalState.DEFAULT.value) existing else current
    }

    private data class PersonalRow(
        val ownerUid: String,
        val friendUid: String,
        val teleport: Int,
        val notifyReceive: Int,
        val notifyBroadcast: Int,
        val statusLikeNotice: Int,
        val statusCommentNotice: Int,
        val wallPostNotice: Int,
        val wallLikeNotice: Int,
        val wallCommentNotice: Int
    )
}
