package org.cyuCBMclean.cyufriendsReload.modules.chat

import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.executeBatch
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

data class ChatMessage(
    val id: Int,
    val senderUid: String,
    val receiverUid: String,
    val content: String,
    val timestamp: Long
)

data class ChatConversationSummary(
    val partnerUid: String,
    val latestContent: String,
    val latestAt: Long,
    val unreadCount: Int,
    val latestSenderUid: String
)

class ChatRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_chat_messages"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            val sql = if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, sender_uid VARCHAR(36), receiver_uid VARCHAR(36), content TEXT, created_at BIGINT, is_read BOOLEAN)"
            } else {
                "CREATE TABLE IF NOT EXISTS $tableName (id INT AUTO_INCREMENT PRIMARY KEY, sender_uid VARCHAR(36), receiver_uid VARCHAR(36), content TEXT, created_at BIGINT, is_read BOOLEAN)"
            }
            update(sql)
            if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_receiver_read_created ON $tableName (receiver_uid, is_read, created_at)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_sender_receiver_created ON $tableName (sender_uid, receiver_uid, created_at)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_created ON $tableName (created_at)")
                }
            } else {
                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_receiver_read_created (receiver_uid, is_read, created_at)")
                }

                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_sender_receiver_created (sender_uid, receiver_uid, created_at)")
                }

                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_created (created_at)")
                }
            }
        }
    }

    fun createTableSync(databaseManager: DatabaseManager) {
        databaseManager.executeSync {
            val sql = if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, sender_uid VARCHAR(36), receiver_uid VARCHAR(36), content TEXT, created_at BIGINT, is_read BOOLEAN)"
            } else {
                "CREATE TABLE IF NOT EXISTS $tableName (id INT AUTO_INCREMENT PRIMARY KEY, sender_uid VARCHAR(36), receiver_uid VARCHAR(36), content TEXT, created_at BIGINT, is_read BOOLEAN)"
            }
            update(sql)
            if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_receiver_read_created ON $tableName (receiver_uid, is_read, created_at)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_sender_receiver_created ON $tableName (sender_uid, receiver_uid, created_at)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_created ON $tableName (created_at)")
                }
            } else {
                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_receiver_read_created (receiver_uid, is_read, created_at)")
                }

                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_sender_receiver_created (sender_uid, receiver_uid, created_at)")
                }

                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_created (created_at)")
                }
            }
        }
    }

    suspend fun saveMessage(sender: String, receiver: String, content: String, time: Long, isRead: Boolean) = db.execute {
        update("INSERT INTO $tableName (sender_uid, receiver_uid, content, created_at, is_read) VALUES (?, ?, ?, ?, ?)", sender, receiver, content, time, isRead)
    }

    fun saveMessageSync(sender: String, receiver: String, content: String, time: Long, isRead: Boolean) = db.executeSync {
        update("INSERT INTO $tableName (sender_uid, receiver_uid, content, created_at, is_read) VALUES (?, ?, ?, ?, ?)", sender, receiver, content, time, isRead)
    }

    suspend fun getUnreadMessages(receiver: String): List<ChatMessage> = db.execute {
        query("SELECT id, sender_uid, receiver_uid, content, created_at FROM $tableName WHERE receiver_uid = ? AND is_read = ?", receiver, false) { rs ->
            val list = mutableListOf<ChatMessage>()
            while (rs.next()) {
                list.add(ChatMessage(rs.getInt("id"), rs.getString("sender_uid"), rs.getString("receiver_uid"), rs.getString("content"), rs.getLong("created_at")))
            }
            list
        }
    }

    fun getUnreadMessagesSync(receiver: String): List<ChatMessage> = db.executeSync {
        query("SELECT id, sender_uid, receiver_uid, content, created_at FROM $tableName WHERE receiver_uid = ? AND is_read = ?", receiver, false) { rs ->
            val list = mutableListOf<ChatMessage>()
            while (rs.next()) {
                list.add(ChatMessage(rs.getInt("id"), rs.getString("sender_uid"), rs.getString("receiver_uid"), rs.getString("content"), rs.getLong("created_at")))
            }
            list
        }
    }

    fun countUnreadSync(receiver: String): Int = db.executeSync {
        query("SELECT COUNT(*) FROM $tableName WHERE receiver_uid = ? AND is_read = ?", receiver, false) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    suspend fun getConversationSummaries(uid: String, limit: Int): List<ChatConversationSummary> = db.execute {
        readConversationSummaries(uid, limit)
    }

    fun getConversationSummariesSync(uid: String, limit: Int): List<ChatConversationSummary> = db.executeSync {
        readConversationSummaries(uid, limit)
    }

    suspend fun getConversation(uid1: String, uid2: String, limit: Int): List<ChatMessage> = db.execute {
        query(
            "SELECT id, sender_uid, receiver_uid, content, created_at FROM $tableName WHERE (sender_uid = ? AND receiver_uid = ?) OR (sender_uid = ? AND receiver_uid = ?) ORDER BY created_at DESC LIMIT ?",
            uid1,
            uid2,
            uid2,
            uid1,
            limit
        ) { rs ->
            val list = mutableListOf<ChatMessage>()
            while (rs.next()) {
                list.add(ChatMessage(rs.getInt("id"), rs.getString("sender_uid"), rs.getString("receiver_uid"), rs.getString("content"), rs.getLong("created_at")))
            }
            list
        }
    }

    suspend fun markAsRead(ids: List<Int>) = db.execute {
        if (ids.isEmpty()) return@execute
        val params = ids.map { arrayOf<Any?>(it) }
        executeBatch("UPDATE $tableName SET is_read = 1 WHERE id = ?", params)
    }

    suspend fun markConversationUnreadAsRead(receiver: String, sender: String): Int = db.execute {
        update("UPDATE $tableName SET is_read = 1 WHERE receiver_uid = ? AND sender_uid = ? AND is_read = 0", receiver, sender)
    }

    fun markConversationUnreadAsReadSync(receiver: String, sender: String): Int = db.executeSync {
        update("UPDATE $tableName SET is_read = 1 WHERE receiver_uid = ? AND sender_uid = ? AND is_read = 0", receiver, sender)
    }

    suspend fun markUnreadAsRead(receiver: String): Int = db.execute {
        update("UPDATE $tableName SET is_read = 1 WHERE receiver_uid = ? AND is_read = ?", receiver, false)
    }

    fun markUnreadAsReadSync(receiver: String): Int = db.executeSync {
        update("UPDATE $tableName SET is_read = 1 WHERE receiver_uid = ? AND is_read = ?", receiver, false)
    }

    suspend fun deleteExpiredUnread(threshold: Long) = db.execute {
        update("DELETE FROM $tableName WHERE is_read = ? AND created_at < ?", false, threshold)
    }

    fun deleteExpiredUnreadSync(threshold: Long) = db.executeSync {
        update("DELETE FROM $tableName WHERE is_read = ? AND created_at < ?", false, threshold)
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET sender_uid = ? WHERE sender_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET receiver_uid = ? WHERE receiver_uid = ?", newUid, oldUid)
    }

    private fun java.sql.Connection.readConversationSummaries(uid: String, limit: Int): List<ChatConversationSummary> {
        val rows = query(
            "SELECT CASE WHEN sender_uid = ? THEN receiver_uid ELSE sender_uid END AS partner_uid, " +
                "MAX(created_at) AS latest_at, " +
                "SUM(CASE WHEN receiver_uid = ? AND is_read = 0 THEN 1 ELSE 0 END) AS unread_count " +
                "FROM $tableName WHERE sender_uid = ? OR receiver_uid = ? " +
                "GROUP BY CASE WHEN sender_uid = ? THEN receiver_uid ELSE sender_uid END " +
                "ORDER BY latest_at DESC, partner_uid ASC LIMIT ?",
            uid,
            uid,
            uid,
            uid,
            uid,
            limit.coerceAtLeast(1)
        ) { rs ->
            val list = mutableListOf<Triple<String, Long, Int>>()
            while (rs.next()) {
                list += Triple(
                    rs.getString("partner_uid"),
                    rs.getLong("latest_at"),
                    rs.getInt("unread_count")
                )
            }
            list
        }
        if (rows.isEmpty()) return emptyList()

        return rows.mapNotNull { (partnerUid, latestAt, unreadCount) ->
            query(
                "SELECT sender_uid, receiver_uid, content, created_at FROM $tableName " +
                    "WHERE (sender_uid = ? AND receiver_uid = ?) OR (sender_uid = ? AND receiver_uid = ?) " +
                    "ORDER BY created_at DESC, id DESC LIMIT 1",
                uid,
                partnerUid,
                partnerUid,
                uid
            ) { rs ->
                if (!rs.next()) return@query null
                ChatConversationSummary(
                    partnerUid = partnerUid,
                    latestContent = rs.getString("content") ?: "",
                    latestAt = latestAt,
                    unreadCount = unreadCount,
                    latestSenderUid = rs.getString("sender_uid")
                )
            }
        }
    }
}
