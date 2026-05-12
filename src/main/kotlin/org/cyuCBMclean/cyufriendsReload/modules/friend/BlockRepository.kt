package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

class BlockRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_friend_blocks"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            update(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "user_uid VARCHAR(36) NOT NULL, " +
                    "blocked_uid VARCHAR(36) NOT NULL, " +
                    "PRIMARY KEY (user_uid, blocked_uid))"
            )
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_blocked ON $tableName (blocked_uid)")
        }
    }

    suspend fun getBlocks(user: String): Set<String> = db.execute {
        query("SELECT blocked_uid FROM $tableName WHERE user_uid = ?", user) { rs ->
            val set = mutableSetOf<String>()
            while (rs.next()) {
                set.add(rs.getString("blocked_uid"))
            }
            set
        }
    }

    fun getBlocksSync(user: String): Set<String> = db.executeSync {
        query("SELECT blocked_uid FROM $tableName WHERE user_uid = ?", user) { rs ->
            val set = mutableSetOf<String>()
            while (rs.next()) {
                set.add(rs.getString("blocked_uid"))
            }
            set
        }
    }

    suspend fun isBlocked(user: String, blocked: String): Boolean = db.execute {
        query("SELECT 1 FROM $tableName WHERE user_uid = ? AND blocked_uid = ? LIMIT 1", user, blocked) { rs -> rs.next() }
    }

    fun isBlockedSync(user: String, blocked: String): Boolean = db.executeSync {
        query("SELECT 1 FROM $tableName WHERE user_uid = ? AND blocked_uid = ? LIMIT 1", user, blocked) { rs -> rs.next() }
    }

    suspend fun saveBlock(user: String, blocked: String) = db.execute {
        update("INSERT INTO $tableName (user_uid, blocked_uid) VALUES (?, ?)", user, blocked)
    }

    suspend fun deleteBlock(user: String, blocked: String) = db.execute {
        update("DELETE FROM $tableName WHERE user_uid = ? AND blocked_uid = ?", user, blocked)
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET user_uid = ? WHERE user_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET blocked_uid = ? WHERE blocked_uid = ?", newUid, oldUid)
    }
}
