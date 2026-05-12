package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.executeBatch
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

enum class RelationshipTimelineType(
    val id: String,
    val displayName: String,
    val emptyPreview: String
) {
    PRIVATE_MESSAGE("private_message", "私聊消息", "没有附加内容"),
    STATUS_LIKE("status_like", "动态点赞", "给这条动态点了赞"),
    STATUS_COMMENT("status_comment", "动态评论", "留下了一条动态评论"),
    WALL_POST("wall_post", "留言墙留言", "留下了一条墙上留言"),
    WALL_LIKE("wall_like", "留言点赞", "给这条留言点了赞"),
    WALL_COMMENT("wall_comment", "留言评论", "留下了一条留言评论");

    companion object {
        fun fromId(id: String?): RelationshipTimelineType? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

data class RelationshipTimelineEntry(
    val id: Int,
    val ownerUid: String,
    val friendUid: String,
    val actorUid: String,
    val type: RelationshipTimelineType,
    val preview: String,
    val referenceId: Int?,
    val createdAt: Long
)

class RelationshipTimelineRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_friend_timeline"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            val sql = if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "owner_uid VARCHAR(36) NOT NULL, " +
                    "friend_uid VARCHAR(36) NOT NULL, " +
                    "actor_uid VARCHAR(36) NOT NULL, " +
                    "kind VARCHAR(32) NOT NULL, " +
                    "preview TEXT, " +
                    "reference_id INT, " +
                    "created_at BIGINT NOT NULL)"
            } else {
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "owner_uid VARCHAR(36) NOT NULL, " +
                    "friend_uid VARCHAR(36) NOT NULL, " +
                    "actor_uid VARCHAR(36) NOT NULL, " +
                    "kind VARCHAR(32) NOT NULL, " +
                    "preview TEXT, " +
                    "reference_id INT, " +
                    "created_at BIGINT NOT NULL)"
            }
            update(sql)
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_friend_created ON $tableName (owner_uid, friend_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_created ON $tableName (owner_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_friend_created ON $tableName (friend_uid, created_at)")
        }
    }

    fun addEntrySync(entry: RelationshipTimelineEntry) = db.executeSync {
        update(
            "INSERT INTO $tableName (owner_uid, friend_uid, actor_uid, kind, preview, reference_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            entry.ownerUid,
            entry.friendUid,
            entry.actorUid,
            entry.type.id,
            entry.preview,
            entry.referenceId,
            entry.createdAt
        )
    }

    fun getEntriesSync(ownerUid: String, friendUid: String, limit: Int): List<RelationshipTimelineEntry> = db.executeSync {
        query(
            "SELECT id, owner_uid, friend_uid, actor_uid, kind, preview, reference_id, created_at " +
                "FROM $tableName WHERE owner_uid = ? AND friend_uid = ? " +
                "ORDER BY created_at DESC, id DESC LIMIT ?",
            ownerUid,
            friendUid,
            limit.coerceAtLeast(1)
        ) { rs ->
            val list = mutableListOf<RelationshipTimelineEntry>()
            while (rs.next()) {
                list += RelationshipTimelineEntry(
                    id = rs.getInt("id"),
                    ownerUid = rs.getString("owner_uid"),
                    friendUid = rs.getString("friend_uid"),
                    actorUid = rs.getString("actor_uid"),
                    type = RelationshipTimelineType.fromId(rs.getString("kind")) ?: RelationshipTimelineType.PRIVATE_MESSAGE,
                    preview = rs.getString("preview").orEmpty(),
                    referenceId = rs.getInt("reference_id").takeIf { !rs.wasNull() },
                    createdAt = rs.getLong("created_at")
                )
            }
            list
        }
    }

    fun trimSync(ownerUid: String, friendUid: String, keep: Int) = db.executeSync {
        val ids = query(
            "SELECT id FROM $tableName WHERE owner_uid = ? AND friend_uid = ? ORDER BY created_at DESC, id DESC",
            ownerUid,
            friendUid
        ) { rs ->
            val list = mutableListOf<Int>()
            while (rs.next()) {
                list += rs.getInt("id")
            }
            list
        }.drop(keep.coerceAtLeast(0))

        if (ids.isNotEmpty()) {
            executeBatch("DELETE FROM $tableName WHERE id = ?", ids.map { arrayOf<Any?>(it) })
        }
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET owner_uid = ? WHERE owner_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET friend_uid = ? WHERE friend_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET actor_uid = ? WHERE actor_uid = ?", newUid, oldUid)
    }
}
