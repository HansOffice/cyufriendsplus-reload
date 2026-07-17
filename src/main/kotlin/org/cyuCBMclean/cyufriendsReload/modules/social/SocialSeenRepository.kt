package org.cyuCBMclean.cyufriendsReload.modules.social

import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

class SocialSeenRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_social_status_seen"
    private val wallSeenTable = "cyu_social_wall_seen"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            update(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "owner_uid VARCHAR(36) NOT NULL, " +
                    "viewer_uid VARCHAR(36) NOT NULL, " +
                    "seen_at BIGINT NOT NULL, " +
                    "PRIMARY KEY(owner_uid, viewer_uid))"
            )
            update(
                "CREATE TABLE IF NOT EXISTS $wallSeenTable (" +
                    "owner_uid VARCHAR(36) NOT NULL, " +
                    "viewer_uid VARCHAR(36) NOT NULL, " +
                    "seen_at BIGINT NOT NULL, " +
                    "PRIMARY KEY(owner_uid, viewer_uid))"
            )
            if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_viewer ON $tableName (viewer_uid)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${wallSeenTable}_viewer ON $wallSeenTable (viewer_uid)")
                }
            } else {
                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_viewer (viewer_uid)")
                }

                runCatching {
                    update("ALTER TABLE $wallSeenTable ADD INDEX idx_${wallSeenTable}_viewer (viewer_uid)")
                }
            }
        }
    }

    fun createTableSync(databaseManager: DatabaseManager) {
        databaseManager.executeSync {
            update(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "owner_uid VARCHAR(36) NOT NULL, " +
                    "viewer_uid VARCHAR(36) NOT NULL, " +
                    "seen_at BIGINT NOT NULL, " +
                    "PRIMARY KEY(owner_uid, viewer_uid))"
            )
            update(
                "CREATE TABLE IF NOT EXISTS $wallSeenTable (" +
                    "owner_uid VARCHAR(36) NOT NULL, " +
                    "viewer_uid VARCHAR(36) NOT NULL, " +
                    "seen_at BIGINT NOT NULL, " +
                    "PRIMARY KEY(owner_uid, viewer_uid))"
            )
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_viewer ON $tableName (viewer_uid)")
            update("CREATE INDEX IF NOT EXISTS idx_${wallSeenTable}_viewer ON $wallSeenTable (viewer_uid)")
        }
    }

    fun getStatusSeenAtSync(ownerUid: String, viewerUid: String): Long = db.executeSync {
        query(
            "SELECT seen_at FROM $tableName WHERE owner_uid = ? AND viewer_uid = ?",
            ownerUid,
            viewerUid
        ) { rs ->
            if (rs.next()) rs.getLong("seen_at") else 0L
        }
    }

    fun getWallSeenAtSync(ownerUid: String, viewerUid: String): Long = db.executeSync {
        query(
            "SELECT seen_at FROM $wallSeenTable WHERE owner_uid = ? AND viewer_uid = ?",
            ownerUid,
            viewerUid
        ) { rs ->
            if (rs.next()) rs.getLong("seen_at") else 0L
        }
    }

    fun markStatusSeenSync(ownerUid: String, viewerUid: String, seenAt: Long) = db.executeSync {
        upsertSeen(tableName, ownerUid, viewerUid, seenAt)
    }

    fun markWallSeenSync(ownerUid: String, viewerUid: String, seenAt: Long) = db.executeSync {
        upsertSeen(wallSeenTable, ownerUid, viewerUid, seenAt)
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        updateUidAcrossTable(tableName, oldUid, newUid)
        updateUidAcrossTable(wallSeenTable, oldUid, newUid)
    }

    private fun java.sql.Connection.upsertSeen(table: String, ownerUid: String, viewerUid: String, seenAt: Long) {
        val updated = update(
            "UPDATE $table SET seen_at = ? WHERE owner_uid = ? AND viewer_uid = ? AND seen_at < ?",
            seenAt,
            ownerUid,
            viewerUid,
            seenAt
        )
        if (updated == 0) {
            val existing = query(
                "SELECT 1 FROM $table WHERE owner_uid = ? AND viewer_uid = ?",
                ownerUid,
                viewerUid
            ) { rs -> rs.next() }
            if (!existing) {
                update(
                    "INSERT INTO $table (owner_uid, viewer_uid, seen_at) VALUES (?, ?, ?)",
                    ownerUid,
                    viewerUid,
                    seenAt
                )
            }
        }
    }

    private fun java.sql.Connection.updateUidAcrossTable(table: String, oldUid: String, newUid: String) {
        val rows = query(
            "SELECT owner_uid, viewer_uid, seen_at FROM $table WHERE owner_uid = ? OR viewer_uid = ?",
            oldUid,
            oldUid
        ) { rs ->
            val result = mutableListOf<Triple<String, String, Long>>()
            while (rs.next()) {
                result += Triple(
                    rs.getString("owner_uid"),
                    rs.getString("viewer_uid"),
                    rs.getLong("seen_at")
                )
            }
            result
        }
        if (rows.isEmpty()) return

        update("DELETE FROM $table WHERE owner_uid = ? OR viewer_uid = ?", oldUid, oldUid)
        rows.forEach { (ownerUid, viewerUid, seenAt) ->
            val nextOwner = if (ownerUid == oldUid) newUid else ownerUid
            val nextViewer = if (viewerUid == oldUid) newUid else viewerUid
            upsertSeen(table, nextOwner, nextViewer, seenAt)
        }
    }
}
