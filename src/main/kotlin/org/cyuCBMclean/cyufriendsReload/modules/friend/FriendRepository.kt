package org.cyuCBMclean.cyufriendsReload.modules.friend

import net.kyori.adventure.text.minimessage.MiniMessage
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update
import java.sql.Connection

data class FriendData(
    val friendUid: String,
    var noteName: String? = null,
    var noteDetail: String? = null,
    var groupName: String = FriendDefaults.DEFAULT_GROUP_NAME,
    var tagName: String? = null,
    val tagNames: MutableList<String> = mutableListOf(),
    val tagColors: MutableMap<String, String> = linkedMapOf(),
    var pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var lastInteractionAt: Long = createdAt
) {
    fun primaryTag(): String? = tagName ?: tagNames.firstOrNull()

    fun orderedTags(): List<String> {
        val primary = primaryTag()
        val ordered = mutableListOf<String>()
        if (!primary.isNullOrBlank()) {
            ordered.add(primary)
        }
        tagNames.forEach {
            if (it.isNotBlank() && it !in ordered) {
                ordered.add(it)
            }
        }
        return ordered
    }

    fun joinedTags(separator: String = " · "): String {
        return orderedTags().joinToString(separator)
    }

    fun tagColor(tag: String): String {
        return tagColors[tag]?.takeIf { it.isNotBlank() } ?: FriendDefaults.TAG_COLOR_PALETTE[(tag.lowercase().hashCode() and Int.MAX_VALUE) % FriendDefaults.TAG_COLOR_PALETTE.size]
    }

    fun primaryTagColor(): String? {
        return primaryTag()?.let(::tagColor)
    }

    fun coloredTag(tag: String): String {
        val escaped = MiniMessage.miniMessage().escapeTags(tag)
        val color = tagColor(tag)
        return if (color.startsWith("#")) {
            "<color:$color>$escaped</color>"
        } else {
            "<$color>$escaped</$color>"
        }
    }

    fun primaryColoredTag(): String? {
        return primaryTag()?.let(::coloredTag)
    }

    fun joinedColoredTags(separator: String = "<gray> · </gray>"): String {
        return orderedTags().joinToString(separator) { coloredTag(it) }
    }
}

data class FriendRecommendation(
    val candidateUid: String,
    val mutualCount: Int,
    val latestSharedInteractionAt: Long
)

class FriendRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_friends"
    private val requestTable = "cyu_friend_requests"
    private val blockTable = "cyu_friend_blocks"
    private val tagsTable = "cyu_friend_tags"
    private val recommendationIgnoreTable = "cyu_friend_recommendation_ignores"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            update(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "user_uid VARCHAR(36), " +
                    "friend_uid VARCHAR(36), " +
                    "note_name VARCHAR(64), " +
                    "note_detail VARCHAR(255), " +
                    "group_name VARCHAR(64), " +
                    "tag_name VARCHAR(32), " +
                    "pinned INTEGER DEFAULT 0, " +
                    "created_at BIGINT, " +
                    "last_interaction_at BIGINT, " +
                    "PRIMARY KEY(user_uid, friend_uid))"
            )
            update("CREATE TABLE IF NOT EXISTS $requestTable (sender_uid VARCHAR(36), receiver_uid VARCHAR(36), created_at BIGINT, PRIMARY KEY(sender_uid, receiver_uid))")
            update("CREATE TABLE IF NOT EXISTS $blockTable (user_uid VARCHAR(36), blocked_uid VARCHAR(36), PRIMARY KEY(user_uid, blocked_uid))")
            update(
                "CREATE TABLE IF NOT EXISTS $tagsTable (" +
                    "user_uid VARCHAR(36), " +
                    "friend_uid VARCHAR(36), " +
                    "tag_name VARCHAR(32), " +
                    "tag_color VARCHAR(16), " +
                    "created_at BIGINT, " +
                    "PRIMARY KEY(user_uid, friend_uid, tag_name))"
            )
            update(
                "CREATE TABLE IF NOT EXISTS $recommendationIgnoreTable (" +
                    "owner_uid VARCHAR(36), " +
                    "candidate_uid VARCHAR(36), " +
                    "ignored_at BIGINT, " +
                    "expires_at BIGINT DEFAULT 0, " +
                    "PRIMARY KEY(owner_uid, candidate_uid))"
            )

            runCatching { update("ALTER TABLE $tableName ADD COLUMN note_name VARCHAR(64)") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN note_detail VARCHAR(255)") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN group_name VARCHAR(64) DEFAULT '${FriendDefaults.DEFAULT_GROUP_NAME}'") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN tag_name VARCHAR(32)") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN pinned INTEGER DEFAULT 0") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN last_interaction_at BIGINT") }
            runCatching { update("ALTER TABLE $tagsTable ADD COLUMN tag_color VARCHAR(16)") }
            runCatching { update("UPDATE $tableName SET last_interaction_at = created_at WHERE last_interaction_at IS NULL OR last_interaction_at = 0") }

            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_user_group ON $tableName (user_uid, group_name)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_user_tag ON $tableName (user_uid, tag_name)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_user_pinned ON $tableName (user_uid, pinned)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_user_last_interaction ON $tableName (user_uid, last_interaction_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tagsTable}_user_friend_created ON $tagsTable (user_uid, friend_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tagsTable}_user_tag ON $tagsTable (user_uid, tag_name)")
            update("CREATE INDEX IF NOT EXISTS idx_${recommendationIgnoreTable}_owner_expires ON $recommendationIgnoreTable (owner_uid, expires_at)")

            val legacyTags = query(
                "SELECT user_uid, friend_uid, tag_name, created_at FROM $tableName WHERE tag_name IS NOT NULL AND tag_name <> ''"
            ) { rs ->
                val rows = mutableListOf<Array<Any?>>()
                while (rs.next()) {
                    rows.add(
                        arrayOf(
                            rs.getString("user_uid"),
                            rs.getString("friend_uid"),
                            rs.getString("tag_name"),
                            rs.getLong("created_at").takeIf { it > 0L } ?: System.currentTimeMillis()
                        )
                    )
                }
                rows
            }
            legacyTags.forEach { row ->
                update(
                    "INSERT INTO $tagsTable (user_uid, friend_uid, tag_name, tag_color, created_at) " +
                        "SELECT ?, ?, ?, NULL, ? WHERE NOT EXISTS (" +
                        "SELECT 1 FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? AND tag_name = ?)",
                    row[0],
                    row[1],
                    row[2],
                    row[3],
                    row[0],
                    row[1],
                    row[2]
                )
            }
        }
    }

    suspend fun getFriends(uid: String): List<FriendData> = db.execute {
        loadFriends(uid)
    }

    fun getFriendsSync(uid: String): List<FriendData> = db.executeSync {
        loadFriends(uid)
    }

    suspend fun getFriend(userUid: String, friendUid: String): FriendData? = db.execute {
        loadFriend(userUid, friendUid)
    }

    fun getFriendSync(userUid: String, friendUid: String): FriendData? = db.executeSync {
        loadFriend(userUid, friendUid)
    }

    suspend fun areFriends(uid1: String, uid2: String): Boolean = db.execute {
        query("SELECT 1 FROM $tableName WHERE user_uid = ? AND friend_uid = ? LIMIT 1", uid1, uid2) { rs -> rs.next() }
    }

    fun areFriendsSync(uid1: String, uid2: String): Boolean = db.executeSync {
        query("SELECT 1 FROM $tableName WHERE user_uid = ? AND friend_uid = ? LIMIT 1", uid1, uid2) { rs -> rs.next() }
    }

    suspend fun addFriend(uid1: String, uid2: String, time: Long) = db.execute {
        update("INSERT INTO $tableName (user_uid, friend_uid, note_name, note_detail, group_name, tag_name, pinned, created_at, last_interaction_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", uid1, uid2, null, null, FriendDefaults.DEFAULT_GROUP_NAME, null, 0, time, time)
        update("INSERT INTO $tableName (user_uid, friend_uid, note_name, note_detail, group_name, tag_name, pinned, created_at, last_interaction_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", uid2, uid1, null, null, FriendDefaults.DEFAULT_GROUP_NAME, null, 0, time, time)
    }

    suspend fun removeFriend(uid1: String, uid2: String) = db.execute {
        update("DELETE FROM $tagsTable WHERE user_uid = ? AND friend_uid = ?", uid1, uid2)
        update("DELETE FROM $tagsTable WHERE user_uid = ? AND friend_uid = ?", uid2, uid1)
        update("DELETE FROM $tableName WHERE user_uid = ? AND friend_uid = ?", uid1, uid2)
        update("DELETE FROM $tableName WHERE user_uid = ? AND friend_uid = ?", uid2, uid1)
    }

    suspend fun updateNote(userUid: String, friendUid: String, note: String?) = db.execute {
        update("UPDATE $tableName SET note_name = ? WHERE user_uid = ? AND friend_uid = ?", note, userUid, friendUid)
    }

    suspend fun updateNoteDetail(userUid: String, friendUid: String, detail: String?) = db.execute {
        update("UPDATE $tableName SET note_detail = ? WHERE user_uid = ? AND friend_uid = ?", detail, userUid, friendUid)
    }

    suspend fun updateGroup(userUid: String, friendUid: String, group: String) = db.execute {
        update("UPDATE $tableName SET group_name = ? WHERE user_uid = ? AND friend_uid = ?", group, userUid, friendUid)
    }

    suspend fun addTag(userUid: String, friendUid: String, tag: String, createdAt: Long = System.currentTimeMillis()): Boolean = db.execute {
        val exists = query("SELECT 1 FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? AND tag_name = ? LIMIT 1", userUid, friendUid, tag) { rs -> rs.next() }
        if (exists) return@execute false
        update("INSERT INTO $tagsTable (user_uid, friend_uid, tag_name, tag_color, created_at) VALUES (?, ?, ?, NULL, ?)", userUid, friendUid, tag, createdAt)
        val currentPrimary = query("SELECT tag_name FROM $tableName WHERE user_uid = ? AND friend_uid = ?", userUid, friendUid) { rs -> if (rs.next()) rs.getString("tag_name") else null }
        if (currentPrimary.isNullOrBlank()) {
            update("UPDATE $tableName SET tag_name = ? WHERE user_uid = ? AND friend_uid = ?", tag, userUid, friendUid)
        }
        true
    }

    suspend fun removeTag(userUid: String, friendUid: String, tag: String): Boolean = db.execute {
        val removed = update("DELETE FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? AND tag_name = ?", userUid, friendUid, tag) > 0
        if (!removed) return@execute false
        val nextTag = query("SELECT tag_name FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? ORDER BY created_at ASC, tag_name ASC LIMIT 1", userUid, friendUid) { rs -> if (rs.next()) rs.getString("tag_name") else null }
        update("UPDATE $tableName SET tag_name = ? WHERE user_uid = ? AND friend_uid = ?", nextTag, userUid, friendUid)
        true
    }

    suspend fun clearTags(userUid: String, friendUid: String): Boolean = db.execute {
        val removed = update("DELETE FROM $tagsTable WHERE user_uid = ? AND friend_uid = ?", userUid, friendUid) > 0
        update("UPDATE $tableName SET tag_name = NULL WHERE user_uid = ? AND friend_uid = ?", userUid, friendUid)
        removed
    }

    suspend fun setPrimaryTag(userUid: String, friendUid: String, tag: String): Boolean = db.execute {
        val exists = query("SELECT 1 FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? AND tag_name = ? LIMIT 1", userUid, friendUid, tag) { rs -> rs.next() }
        if (!exists) return@execute false
        update("UPDATE $tableName SET tag_name = ? WHERE user_uid = ? AND friend_uid = ?", tag, userUid, friendUid)
        true
    }

    suspend fun setTagColor(userUid: String, friendUid: String, tag: String, color: String): Boolean = db.execute {
        val updated = update("UPDATE $tagsTable SET tag_color = ? WHERE user_uid = ? AND friend_uid = ? AND tag_name = ?", color, userUid, friendUid, tag)
        updated > 0
    }

    suspend fun clearTagColor(userUid: String, friendUid: String, tag: String): Boolean = db.execute {
        val updated = update("UPDATE $tagsTable SET tag_color = NULL WHERE user_uid = ? AND friend_uid = ? AND tag_name = ?", userUid, friendUid, tag)
        updated > 0
    }

    suspend fun getTags(userUid: String, friendUid: String): List<String> = db.execute {
        query("SELECT tag_name FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? ORDER BY created_at ASC, tag_name ASC", userUid, friendUid) { rs ->
            val list = mutableListOf<String>()
            while (rs.next()) {
                list.add(rs.getString("tag_name"))
            }
            list
        }
    }

    suspend fun getTagColors(userUid: String, friendUid: String): Map<String, String> = db.execute {
        query("SELECT tag_name, tag_color FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? AND tag_color IS NOT NULL AND tag_color <> ''", userUid, friendUid) { rs ->
            val map = linkedMapOf<String, String>()
            while (rs.next()) {
                map[rs.getString("tag_name")] = rs.getString("tag_color")
            }
            map
        }
    }

    fun getRecommendationsSync(userUid: String, limit: Int): List<FriendRecommendation> = db.executeSync {
        val now = System.currentTimeMillis()
        query(
            "SELECT f2.friend_uid AS candidate_uid, COUNT(*) AS mutual_count, MAX(f1.last_interaction_at) AS latest_shared_interaction " +
                "FROM $tableName f1 " +
                "JOIN $tableName f2 ON f1.friend_uid = f2.user_uid " +
                "WHERE f1.user_uid = ? " +
                "AND f2.friend_uid <> ? " +
                "AND NOT EXISTS (SELECT 1 FROM $tableName self WHERE self.user_uid = ? AND self.friend_uid = f2.friend_uid) " +
                "AND NOT EXISTS (SELECT 1 FROM $requestTable r1 WHERE r1.sender_uid = ? AND r1.receiver_uid = f2.friend_uid) " +
                "AND NOT EXISTS (SELECT 1 FROM $requestTable r2 WHERE r2.sender_uid = f2.friend_uid AND r2.receiver_uid = ?) " +
                "AND NOT EXISTS (SELECT 1 FROM $blockTable b1 WHERE b1.user_uid = ? AND b1.blocked_uid = f2.friend_uid) " +
                "AND NOT EXISTS (SELECT 1 FROM $blockTable b2 WHERE b2.user_uid = f2.friend_uid AND b2.blocked_uid = ?) " +
                "AND NOT EXISTS (SELECT 1 FROM $recommendationIgnoreTable i WHERE i.owner_uid = ? AND i.candidate_uid = f2.friend_uid AND (i.expires_at = 0 OR i.expires_at > ?)) " +
                "GROUP BY f2.friend_uid " +
                "ORDER BY mutual_count DESC, latest_shared_interaction DESC, candidate_uid ASC LIMIT ?",
            userUid,
            userUid,
            userUid,
            userUid,
            userUid,
            userUid,
            userUid,
            userUid,
            now,
            limit.coerceAtLeast(1)
        ) { rs ->
            val list = mutableListOf<FriendRecommendation>()
            while (rs.next()) {
                list += FriendRecommendation(
                    candidateUid = rs.getString("candidate_uid"),
                    mutualCount = rs.getInt("mutual_count"),
                    latestSharedInteractionAt = rs.getLong("latest_shared_interaction")
                )
            }
            list
        }
    }

    fun ignoreRecommendationSync(ownerUid: String, candidateUid: String, expiresAt: Long) = db.executeSync {
        val ignoredAt = System.currentTimeMillis()
        val updated = update(
            "UPDATE $recommendationIgnoreTable SET ignored_at = ?, expires_at = ? WHERE owner_uid = ? AND candidate_uid = ?",
            ignoredAt,
            expiresAt,
            ownerUid,
            candidateUid
        )
        if (updated == 0) {
            update(
                "INSERT INTO $recommendationIgnoreTable (owner_uid, candidate_uid, ignored_at, expires_at) VALUES (?, ?, ?, ?)",
                ownerUid,
                candidateUid,
                ignoredAt,
                expiresAt
            )
        }
    }

    fun clearRecommendationIgnoreSync(ownerUid: String, candidateUid: String): Boolean = db.executeSync {
        update(
            "DELETE FROM $recommendationIgnoreTable WHERE owner_uid = ? AND candidate_uid = ?",
            ownerUid,
            candidateUid
        ) > 0
    }

    suspend fun updateTag(userUid: String, friendUid: String, tag: String?) = db.execute {
        update("DELETE FROM $tagsTable WHERE user_uid = ? AND friend_uid = ?", userUid, friendUid)
        update("UPDATE $tableName SET tag_name = NULL WHERE user_uid = ? AND friend_uid = ?", userUid, friendUid)
        if (!tag.isNullOrBlank()) {
            update("INSERT INTO $tagsTable (user_uid, friend_uid, tag_name, tag_color, created_at) VALUES (?, ?, ?, NULL, ?)", userUid, friendUid, tag, System.currentTimeMillis())
            update("UPDATE $tableName SET tag_name = ? WHERE user_uid = ? AND friend_uid = ?", tag, userUid, friendUid)
        }
    }

    suspend fun updatePinned(userUid: String, friendUid: String, pinned: Boolean) = db.execute {
        update("UPDATE $tableName SET pinned = ? WHERE user_uid = ? AND friend_uid = ?", if (pinned) 1 else 0, userUid, friendUid)
    }

    suspend fun updateLastInteraction(userUid: String, friendUid: String, timestamp: Long) = db.execute {
        update("UPDATE $tableName SET last_interaction_at = ? WHERE user_uid = ? AND friend_uid = ?", timestamp, userUid, friendUid)
    }

    fun updateLastInteractionSync(userUid: String, friendUid: String, timestamp: Long) = db.executeSync {
        update("UPDATE $tableName SET last_interaction_at = ? WHERE user_uid = ? AND friend_uid = ?", timestamp, userUid, friendUid)
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET user_uid = ? WHERE user_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET friend_uid = ? WHERE friend_uid = ?", newUid, oldUid)
        update("UPDATE $tagsTable SET user_uid = ? WHERE user_uid = ?", newUid, oldUid)
        update("UPDATE $tagsTable SET friend_uid = ? WHERE friend_uid = ?", newUid, oldUid)
        update("UPDATE $recommendationIgnoreTable SET owner_uid = ? WHERE owner_uid = ?", newUid, oldUid)
        update("UPDATE $recommendationIgnoreTable SET candidate_uid = ? WHERE candidate_uid = ?", newUid, oldUid)
    }

    private fun Connection.loadFriends(uid: String): List<FriendData> {
        val friends = query(
            "SELECT friend_uid, note_name, note_detail, group_name, tag_name, pinned, created_at, last_interaction_at FROM $tableName WHERE user_uid = ?",
            uid
        ) { rs ->
            val list = mutableListOf<FriendData>()
            while (rs.next()) {
                val createdAt = rs.getLong("created_at")
                list.add(
                    FriendData(
                        friendUid = rs.getString("friend_uid"),
                        noteName = rs.getString("note_name"),
                        noteDetail = rs.getString("note_detail"),
                        groupName = rs.getString("group_name") ?: FriendDefaults.DEFAULT_GROUP_NAME,
                        tagName = rs.getString("tag_name"),
                        pinned = rs.getInt("pinned") != 0,
                        createdAt = createdAt,
                        lastInteractionAt = rs.getLong("last_interaction_at").takeIf { it > 0L } ?: createdAt
                    )
                )
            }
            list
        }
        if (friends.isEmpty()) return emptyList()
        val tagRows = query(
            "SELECT friend_uid, tag_name, tag_color FROM $tagsTable WHERE user_uid = ? ORDER BY created_at ASC, tag_name ASC",
            uid
        ) { rs ->
            val tagMap = linkedMapOf<String, MutableList<String>>()
            val colorMap = linkedMapOf<String, MutableMap<String, String>>()
            while (rs.next()) {
                val friendUid = rs.getString("friend_uid")
                val tagName = rs.getString("tag_name")
                tagMap.computeIfAbsent(friendUid) { mutableListOf() }.add(tagName)
                rs.getString("tag_color")?.takeIf { it.isNotBlank() }?.let { color ->
                    colorMap.computeIfAbsent(friendUid) { linkedMapOf() }[tagName] = color
                }
            }
            tagMap to colorMap
        }
        val tagMap = tagRows.first
        val colorMap = tagRows.second
        friends.forEach { friend ->
            val tags = tagMap[friend.friendUid].orEmpty()
            friend.tagNames.clear()
            friend.tagNames.addAll(tags)
            friend.tagColors.clear()
            friend.tagColors.putAll(colorMap[friend.friendUid].orEmpty())
            if (friend.tagName.isNullOrBlank()) {
                friend.tagName = tags.firstOrNull()
            }
        }
        return friends
    }

    private fun Connection.loadFriend(userUid: String, friendUid: String): FriendData? {
        val friend = query(
            "SELECT friend_uid, note_name, note_detail, group_name, tag_name, pinned, created_at, last_interaction_at FROM $tableName WHERE user_uid = ? AND friend_uid = ?",
            userUid,
            friendUid
        ) { rs ->
            if (!rs.next()) return@query null
            val createdAt = rs.getLong("created_at")
            FriendData(
                friendUid = rs.getString("friend_uid"),
                noteName = rs.getString("note_name"),
                noteDetail = rs.getString("note_detail"),
                groupName = rs.getString("group_name") ?: FriendDefaults.DEFAULT_GROUP_NAME,
                tagName = rs.getString("tag_name"),
                pinned = rs.getInt("pinned") != 0,
                createdAt = createdAt,
                lastInteractionAt = rs.getLong("last_interaction_at").takeIf { it > 0L } ?: createdAt
            )
        } ?: return null

        val tagRows = query(
            "SELECT tag_name, tag_color FROM $tagsTable WHERE user_uid = ? AND friend_uid = ? ORDER BY created_at ASC, tag_name ASC",
            userUid,
            friendUid
        ) { rs ->
            val tags = mutableListOf<String>()
            val colors = linkedMapOf<String, String>()
            while (rs.next()) {
                val tagName = rs.getString("tag_name")
                tags.add(tagName)
                rs.getString("tag_color")?.takeIf { it.isNotBlank() }?.let { colors[tagName] = it }
            }
            tags to colors
        }
        friend.tagNames.clear()
        friend.tagNames.addAll(tagRows.first)
        friend.tagColors.clear()
        friend.tagColors.putAll(tagRows.second)
        if (friend.tagName.isNullOrBlank()) {
            friend.tagName = tagRows.first.firstOrNull()
        }
        return friend
    }
}
