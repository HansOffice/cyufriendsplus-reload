package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

class RequestRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_friend_requests"
    private val logTableName = "cyu_friend_request_log"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            update(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "sender_uid VARCHAR(36) NOT NULL, " +
                    "receiver_uid VARCHAR(36) NOT NULL, " +
                    "note TEXT, " +
                    "created_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (sender_uid, receiver_uid))"
            )
            update(
                "CREATE TABLE IF NOT EXISTS $logTableName (" +
                    "sender_uid VARCHAR(36) NOT NULL, " +
                    "created_at BIGINT NOT NULL)"
            )
            runCatching {
                update("CREATE INDEX IF NOT EXISTS idx_${logTableName}_sender_created ON $logTableName (sender_uid, created_at)")
            }.recoverCatching {
                update("ALTER TABLE $logTableName ADD INDEX idx_${logTableName}_sender_created (sender_uid, created_at)")
            }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN note TEXT") }
            runCatching {
                update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_receiver_created (receiver_uid, created_at)")
            }

            runCatching {
                update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_sender_created (sender_uid, created_at)")
            }
        }
    }

    suspend fun getRequests(receiver: String): List<FriendRequestEntry> = db.execute {
        query("SELECT sender_uid, receiver_uid, note, created_at FROM $tableName WHERE receiver_uid = ? ORDER BY created_at DESC", receiver) { rs ->
            val entries = mutableListOf<FriendRequestEntry>()
            while (rs.next()) {
                entries.add(readEntry(rs))
            }
            entries
        }
    }

    fun getRequestsSync(receiver: String): List<FriendRequestEntry> = db.executeSync {
        query("SELECT sender_uid, receiver_uid, note, created_at FROM $tableName WHERE receiver_uid = ? ORDER BY created_at DESC", receiver) { rs ->
            val entries = mutableListOf<FriendRequestEntry>()
            while (rs.next()) {
                entries.add(readEntry(rs))
            }
            entries
        }
    }

    suspend fun getSentRequests(sender: String): List<FriendRequestEntry> = db.execute {
        query("SELECT sender_uid, receiver_uid, note, created_at FROM $tableName WHERE sender_uid = ? ORDER BY created_at DESC", sender) { rs ->
            val entries = mutableListOf<FriendRequestEntry>()
            while (rs.next()) {
                entries.add(readEntry(rs))
            }
            entries
        }
    }

    fun getSentRequestsSync(sender: String): List<FriendRequestEntry> = db.executeSync {
        query("SELECT sender_uid, receiver_uid, note, created_at FROM $tableName WHERE sender_uid = ? ORDER BY created_at DESC", sender) { rs ->
            val entries = mutableListOf<FriendRequestEntry>()
            while (rs.next()) {
                entries.add(readEntry(rs))
            }
            entries
        }
    }

    suspend fun countReceived(receiver: String): Int = db.execute {
        query("SELECT COUNT(*) FROM $tableName WHERE receiver_uid = ?", receiver) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    fun countReceivedSync(receiver: String): Int = db.executeSync {
        query("SELECT COUNT(*) FROM $tableName WHERE receiver_uid = ?", receiver) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    suspend fun countSent(sender: String): Int = db.execute {
        query("SELECT COUNT(*) FROM $tableName WHERE sender_uid = ?", sender) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    fun countSentSync(sender: String): Int = db.executeSync {
        query("SELECT COUNT(*) FROM $tableName WHERE sender_uid = ?", sender) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    suspend fun countSentSince(sender: String, since: Long): Int = db.execute {
        query("SELECT COUNT(*) FROM $logTableName WHERE sender_uid = ? AND created_at >= ?", sender, since) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    fun countSentSinceSync(sender: String, since: Long): Int = db.executeSync {
        query("SELECT COUNT(*) FROM $logTableName WHERE sender_uid = ? AND created_at >= ?", sender, since) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    suspend fun hasRequest(sender: String, receiver: String): Boolean = db.execute {
        query("SELECT 1 FROM $tableName WHERE sender_uid = ? AND receiver_uid = ? LIMIT 1", sender, receiver) { rs -> rs.next() }
    }

    fun hasRequestSync(sender: String, receiver: String): Boolean = db.executeSync {
        query("SELECT 1 FROM $tableName WHERE sender_uid = ? AND receiver_uid = ? LIMIT 1", sender, receiver) { rs -> rs.next() }
    }

    suspend fun latestSentAt(sender: String): Long? = db.execute {
        query("SELECT MAX(created_at) FROM $logTableName WHERE sender_uid = ?", sender) { rs ->
            if (rs.next()) rs.getLong(1).takeIf { !rs.wasNull() } else null
        }
    }

    fun latestSentAtSync(sender: String): Long? = db.executeSync {
        query("SELECT MAX(created_at) FROM $logTableName WHERE sender_uid = ?", sender) { rs ->
            if (rs.next()) rs.getLong(1).takeIf { !rs.wasNull() } else null
        }
    }

    suspend fun saveRequest(sender: String, receiver: String, note: String?, time: Long) = db.transaction {
        update("INSERT INTO $tableName (sender_uid, receiver_uid, note, created_at) VALUES (?, ?, ?, ?)", sender, receiver, note, time)
        update("INSERT INTO $logTableName (sender_uid, created_at) VALUES (?, ?)", sender, time)
    }

    suspend fun deleteRequest(sender: String, receiver: String) = db.execute {
        update("DELETE FROM $tableName WHERE sender_uid = ? AND receiver_uid = ?", sender, receiver)
    }

    suspend fun clearExpired(thresholdTime: Long) = db.transaction {
        update("DELETE FROM $tableName WHERE created_at < ?", thresholdTime)
        update("DELETE FROM $logTableName WHERE created_at < ?", thresholdTime)
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.transaction {
        update("UPDATE $tableName SET sender_uid = ? WHERE sender_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET receiver_uid = ? WHERE receiver_uid = ?", newUid, oldUid)
        update("UPDATE $logTableName SET sender_uid = ? WHERE sender_uid = ?", newUid, oldUid)
    }
    private fun readEntry(rs: java.sql.ResultSet): FriendRequestEntry {
        return FriendRequestEntry(
            senderUid = rs.getString("sender_uid"),
            receiverUid = rs.getString("receiver_uid"),
            note = rs.getString("note")?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = rs.getLong("created_at")
        )
    }
}
