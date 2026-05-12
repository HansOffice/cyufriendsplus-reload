package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

class RequestRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_friend_requests"

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
            runCatching { update("ALTER TABLE $tableName ADD COLUMN note TEXT") }
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_receiver_created ON $tableName (receiver_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_sender_created ON $tableName (sender_uid, created_at)")
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
        query("SELECT COUNT(*) FROM $tableName WHERE sender_uid = ? AND created_at >= ?", sender, since) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    fun countSentSinceSync(sender: String, since: Long): Int = db.executeSync {
        query("SELECT COUNT(*) FROM $tableName WHERE sender_uid = ? AND created_at >= ?", sender, since) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    suspend fun hasRequest(sender: String, receiver: String): Boolean = db.execute {
        query("SELECT 1 FROM $tableName WHERE sender_uid = ? AND receiver_uid = ? LIMIT 1", sender, receiver) { rs -> rs.next() }
    }

    fun hasRequestSync(sender: String, receiver: String): Boolean = db.executeSync {
        query("SELECT 1 FROM $tableName WHERE sender_uid = ? AND receiver_uid = ? LIMIT 1", sender, receiver) { rs -> rs.next() }
    }

    suspend fun saveRequest(sender: String, receiver: String, note: String?, time: Long) = db.execute {
        update("INSERT INTO $tableName (sender_uid, receiver_uid, note, created_at) VALUES (?, ?, ?, ?)", sender, receiver, note, time)
    }

    suspend fun deleteRequest(sender: String, receiver: String) = db.execute {
        update("DELETE FROM $tableName WHERE sender_uid = ? AND receiver_uid = ?", sender, receiver)
    }

    suspend fun clearExpired(thresholdTime: Long) = db.execute {
        update("DELETE FROM $tableName WHERE created_at < ?", thresholdTime)
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET sender_uid = ? WHERE sender_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET receiver_uid = ? WHERE receiver_uid = ?", newUid, oldUid)
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
