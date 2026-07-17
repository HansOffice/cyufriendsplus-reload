package org.cyuCBMclean.cyufriendsReload.modules.social

import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.executeBatch
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

data class StatusEntry(
    val id: Int,
    val uid: String,
    val content: String,
    val visibility: StatusVisibility,
    val pinned: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val timestamp: Long
)

data class StatusComment(
    val id: Int,
    val statusId: Int,
    val authorUid: String,
    val content: String,
    val timestamp: Long
)

class StatusRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_social_status"
    private val likesTable = "cyu_social_status_likes"
    private val commentsTable = "cyu_social_status_comments"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            val isSQLite = Settings.databaseType.equals("SQLite", ignoreCase = true)
            val sql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', pinned INTEGER DEFAULT 0, created_at BIGINT)"
            } else {
                "CREATE TABLE IF NOT EXISTS $tableName (id INT AUTO_INCREMENT PRIMARY KEY, uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', pinned INTEGER DEFAULT 0, created_at BIGINT)"
            }
            val commentsSql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INTEGER PRIMARY KEY AUTOINCREMENT, status_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, created_at BIGINT NOT NULL)"
            } else {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INT AUTO_INCREMENT PRIMARY KEY, status_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, created_at BIGINT NOT NULL)"
            }
            update(sql)
            update("CREATE TABLE IF NOT EXISTS $likesTable (status_id INT NOT NULL, user_uid VARCHAR(36) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY(status_id, user_uid))")
            update(commentsSql)
            runCatching { update("ALTER TABLE $tableName ADD COLUMN visibility VARCHAR(16) DEFAULT 'PUBLIC'") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN pinned INTEGER DEFAULT 0") }
            if (isSQLite) {
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${tableName}_uid_pinned_created ON $tableName (uid, pinned, created_at)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${tableName}_created ON $tableName (created_at)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${likesTable}_user ON $likesTable (user_uid)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_status_created ON $commentsTable (status_id, created_at)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_author_created ON $commentsTable (author_uid, created_at)") }
            } else {
                runCatching { update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_uid_pinned_created (uid, pinned, created_at)") }
                runCatching { update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_created (created_at)") }
                runCatching { update("ALTER TABLE $likesTable ADD INDEX idx_${likesTable}_user (user_uid)") }
                runCatching { update("ALTER TABLE $commentsTable ADD INDEX idx_${commentsTable}_status_created (status_id, created_at)") }
                runCatching { update("ALTER TABLE $commentsTable ADD INDEX idx_${commentsTable}_author_created (author_uid, created_at)") }
            }
        }
    }

    fun createTableSync(databaseManager: DatabaseManager) {
        databaseManager.executeSync {
            val isSQLite = Settings.databaseType.equals("SQLite", ignoreCase = true)
            val sql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', pinned INTEGER DEFAULT 0, created_at BIGINT)"
            } else {
                "CREATE TABLE IF NOT EXISTS $tableName (id INT AUTO_INCREMENT PRIMARY KEY, uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', pinned INTEGER DEFAULT 0, created_at BIGINT)"
            }
            val commentsSql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INTEGER PRIMARY KEY AUTOINCREMENT, status_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, created_at BIGINT NOT NULL)"
            } else {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INT AUTO_INCREMENT PRIMARY KEY, status_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, created_at BIGINT NOT NULL)"
            }
            update(sql)
            update("CREATE TABLE IF NOT EXISTS $likesTable (status_id INT NOT NULL, user_uid VARCHAR(36) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY(status_id, user_uid))")
            update(commentsSql)
            runCatching { update("ALTER TABLE $tableName ADD COLUMN visibility VARCHAR(16) DEFAULT 'PUBLIC'") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN pinned INTEGER DEFAULT 0") }
            if (isSQLite) {
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${tableName}_uid_pinned_created ON $tableName (uid, pinned, created_at)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${tableName}_created ON $tableName (created_at)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${likesTable}_user ON $likesTable (user_uid)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_status_created ON $commentsTable (status_id, created_at)") }
                runCatching { update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_author_created ON $commentsTable (author_uid, created_at)") }
            } else {
                runCatching { update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_uid_pinned_created (uid, pinned, created_at)") }
                runCatching { update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_created (created_at)") }
                runCatching { update("ALTER TABLE $likesTable ADD INDEX idx_${likesTable}_user (user_uid)") }
                runCatching { update("ALTER TABLE $commentsTable ADD INDEX idx_${commentsTable}_status_created (status_id, created_at)") }
                runCatching { update("ALTER TABLE $commentsTable ADD INDEX idx_${commentsTable}_author_created (author_uid, created_at)") }
            }
        }
    }

    suspend fun publish(uid: String, content: String, visibility: StatusVisibility, time: Long) = db.execute {
        update("INSERT INTO $tableName (uid, content, visibility, pinned, created_at) VALUES (?, ?, ?, ?, ?)", uid, content, visibility.id, 0, time)
    }

    fun publishSync(uid: String, content: String, visibility: StatusVisibility, time: Long) = db.executeSync {
        update("INSERT INTO $tableName (uid, content, visibility, pinned, created_at) VALUES (?, ?, ?, ?, ?)", uid, content, visibility.id, 0, time)
    }

    suspend fun getRecent(limit: Int): List<StatusEntry> = db.execute {
        query(
            "SELECT s.id, s.uid, s.content, s.visibility, s.pinned, s.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.status_id = s.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.status_id = s.id) AS comment_count " +
                "FROM $tableName s ORDER BY s.pinned DESC, s.created_at DESC LIMIT ?",
            limit
        ) { rs ->
            val list = mutableListOf<StatusEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    fun getRecentSync(limit: Int): List<StatusEntry> = db.executeSync {
        query(
            "SELECT s.id, s.uid, s.content, s.visibility, s.pinned, s.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.status_id = s.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.status_id = s.id) AS comment_count " +
                "FROM $tableName s ORDER BY s.pinned DESC, s.created_at DESC LIMIT ?",
            limit
        ) { rs ->
            val list = mutableListOf<StatusEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    suspend fun getByUid(uid: String, limit: Int): List<StatusEntry> = db.execute {
        query(
            "SELECT s.id, s.uid, s.content, s.visibility, s.pinned, s.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.status_id = s.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.status_id = s.id) AS comment_count " +
                "FROM $tableName s WHERE s.uid = ? ORDER BY s.pinned DESC, s.created_at DESC LIMIT ?",
            uid,
            limit
        ) { rs ->
            val list = mutableListOf<StatusEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    fun getByUidSync(uid: String, limit: Int): List<StatusEntry> = db.executeSync {
        query(
            "SELECT s.id, s.uid, s.content, s.visibility, s.pinned, s.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.status_id = s.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.status_id = s.id) AS comment_count " +
                "FROM $tableName s WHERE s.uid = ? ORDER BY s.pinned DESC, s.created_at DESC LIMIT ?",
            uid,
            limit
        ) { rs ->
            val list = mutableListOf<StatusEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    suspend fun getById(id: Int): StatusEntry? = db.execute {
        query(
            "SELECT s.id, s.uid, s.content, s.visibility, s.pinned, s.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.status_id = s.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.status_id = s.id) AS comment_count " +
                "FROM $tableName s WHERE s.id = ?",
            id
        ) { rs ->
            if (!rs.next()) return@query null
            readEntry(rs)
        }
    }

    fun getByIdSync(id: Int): StatusEntry? = db.executeSync {
        query(
            "SELECT s.id, s.uid, s.content, s.visibility, s.pinned, s.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.status_id = s.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.status_id = s.id) AS comment_count " +
                "FROM $tableName s WHERE s.id = ?",
            id
        ) { rs ->
            if (!rs.next()) return@query null
            readEntry(rs)
        }
    }

    suspend fun countByUid(uid: String): Int = db.execute {
        query("SELECT COUNT(*) FROM $tableName WHERE uid = ?", uid) { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    fun countByUidSync(uid: String): Int = db.executeSync {
        query("SELECT COUNT(*) FROM $tableName WHERE uid = ?", uid) { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun delete(id: Int) = db.execute {
        update("DELETE FROM $likesTable WHERE status_id = ?", id)
        update("DELETE FROM $commentsTable WHERE status_id = ?", id)
        update("DELETE FROM $tableName WHERE id = ?", id)
    }

    fun deleteSync(id: Int) = db.executeSync {
        update("DELETE FROM $likesTable WHERE status_id = ?", id)
        update("DELETE FROM $commentsTable WHERE status_id = ?", id)
        update("DELETE FROM $tableName WHERE id = ?", id)
    }

    suspend fun clearPinned(uid: String) = db.execute {
        update("UPDATE $tableName SET pinned = 0 WHERE uid = ?", uid)
    }

    fun clearPinnedSync(uid: String) = db.executeSync {
        update("UPDATE $tableName SET pinned = 0 WHERE uid = ?", uid)
    }

    suspend fun updatePinned(id: Int, pinned: Boolean) = db.execute {
        update("UPDATE $tableName SET pinned = ? WHERE id = ?", if (pinned) 1 else 0, id)
    }

    fun updatePinnedSync(id: Int, pinned: Boolean) = db.executeSync {
        update("UPDATE $tableName SET pinned = ? WHERE id = ?", if (pinned) 1 else 0, id)
    }

    suspend fun trim(uid: String, keep: Int) = db.execute {
        val ids = query("SELECT id FROM $tableName WHERE uid = ? ORDER BY pinned DESC, created_at DESC", uid) { rs ->
            val list = mutableListOf<Int>()
            while (rs.next()) list.add(rs.getInt("id"))
            list
        }.drop(keep.coerceAtLeast(0))

        if (ids.isNotEmpty()) {
            ids.forEach {
                update("DELETE FROM $likesTable WHERE status_id = ?", it)
                update("DELETE FROM $commentsTable WHERE status_id = ?", it)
            }
            executeBatch("DELETE FROM $tableName WHERE id = ?", ids.map { arrayOf<Any?>(it) })
        }
    }

    fun trimSync(uid: String, keep: Int) = db.executeSync {
        val ids = query("SELECT id FROM $tableName WHERE uid = ? ORDER BY pinned DESC, created_at DESC", uid) { rs ->
            val list = mutableListOf<Int>()
            while (rs.next()) list.add(rs.getInt("id"))
            list
        }.drop(keep.coerceAtLeast(0))

        if (ids.isNotEmpty()) {
            ids.forEach {
                update("DELETE FROM $likesTable WHERE status_id = ?", it)
                update("DELETE FROM $commentsTable WHERE status_id = ?", it)
            }
            executeBatch("DELETE FROM $tableName WHERE id = ?", ids.map { arrayOf<Any?>(it) })
        }
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET uid = ? WHERE uid = ?", newUid, oldUid)
        update("UPDATE $likesTable SET user_uid = ? WHERE user_uid = ?", newUid, oldUid)
        update("UPDATE $commentsTable SET author_uid = ? WHERE author_uid = ?", newUid, oldUid)
    }

    suspend fun likeStatus(id: Int, uid: String, time: Long): Boolean = db.execute {
        val exists = query("SELECT 1 FROM $likesTable WHERE status_id = ? AND user_uid = ? LIMIT 1", id, uid) { rs -> rs.next() }
        if (exists) return@execute false
        update("INSERT INTO $likesTable (status_id, user_uid, created_at) VALUES (?, ?, ?)", id, uid, time)
        true
    }

    fun likeStatusSync(id: Int, uid: String, time: Long): Boolean = db.executeSync {
        val exists = query("SELECT 1 FROM $likesTable WHERE status_id = ? AND user_uid = ? LIMIT 1", id, uid) { rs -> rs.next() }
        if (exists) return@executeSync false
        update("INSERT INTO $likesTable (status_id, user_uid, created_at) VALUES (?, ?, ?)", id, uid, time)
        true
    }

    suspend fun unlikeStatus(id: Int, uid: String): Boolean = db.execute {
        update("DELETE FROM $likesTable WHERE status_id = ? AND user_uid = ?", id, uid) > 0
    }

    fun unlikeStatusSync(id: Int, uid: String): Boolean = db.executeSync {
        update("DELETE FROM $likesTable WHERE status_id = ? AND user_uid = ?", id, uid) > 0
    }

    suspend fun addComment(statusId: Int, authorUid: String, content: String, time: Long) = db.execute {
        update("INSERT INTO $commentsTable (status_id, author_uid, content, created_at) VALUES (?, ?, ?, ?)", statusId, authorUid, content, time)
    }

    fun addCommentSync(statusId: Int, authorUid: String, content: String, time: Long) = db.executeSync {
        update("INSERT INTO $commentsTable (status_id, author_uid, content, created_at) VALUES (?, ?, ?, ?)", statusId, authorUid, content, time)
    }

    suspend fun getComments(statusId: Int, limit: Int): List<StatusComment> = db.execute {
        query("SELECT id, status_id, author_uid, content, created_at FROM $commentsTable WHERE status_id = ? ORDER BY created_at DESC LIMIT ?", statusId, limit) { rs ->
            val list = mutableListOf<StatusComment>()
            while (rs.next()) {
                list.add(
                    StatusComment(
                        id = rs.getInt("id"),
                        statusId = rs.getInt("status_id"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    fun getCommentsSync(statusId: Int, limit: Int): List<StatusComment> = db.executeSync {
        query("SELECT id, status_id, author_uid, content, created_at FROM $commentsTable WHERE status_id = ? ORDER BY created_at DESC LIMIT ?", statusId, limit) { rs ->
            val list = mutableListOf<StatusComment>()
            while (rs.next()) {
                list.add(
                    StatusComment(
                        id = rs.getInt("id"),
                        statusId = rs.getInt("status_id"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    fun getLikedStatusIdsSync(userUid: String, statusIds: Collection<Int>): Set<Int> = db.executeSync {
        val ids = statusIds.distinct()
        if (ids.isEmpty()) return@executeSync emptySet()
        val placeholders = ids.joinToString(",") { "?" }
        val params = ArrayList<Any?>(ids.size + 1)
        params += userUid
        params.addAll(ids)
        query(
            "SELECT status_id FROM $likesTable WHERE user_uid = ? AND status_id IN ($placeholders)",
            *params.toTypedArray()
        ) { rs ->
            val liked = linkedSetOf<Int>()
            while (rs.next()) {
                liked += rs.getInt("status_id")
            }
            liked
        }
    }

    suspend fun getCommentById(id: Int): StatusComment? = db.execute {
        query("SELECT id, status_id, author_uid, content, created_at FROM $commentsTable WHERE id = ?", id) { rs ->
            if (!rs.next()) return@query null
            StatusComment(
                id = rs.getInt("id"),
                statusId = rs.getInt("status_id"),
                authorUid = rs.getString("author_uid"),
                content = rs.getString("content"),
                timestamp = rs.getLong("created_at")
            )
        }
    }

    fun getCommentByIdSync(id: Int): StatusComment? = db.executeSync {
        query("SELECT id, status_id, author_uid, content, created_at FROM $commentsTable WHERE id = ?", id) { rs ->
            if (!rs.next()) return@query null
            StatusComment(
                id = rs.getInt("id"),
                statusId = rs.getInt("status_id"),
                authorUid = rs.getString("author_uid"),
                content = rs.getString("content"),
                timestamp = rs.getLong("created_at")
            )
        }
    }

    suspend fun deleteComment(id: Int) = db.execute {
        update("DELETE FROM $commentsTable WHERE id = ?", id)
    }

    fun deleteCommentSync(id: Int) = db.executeSync {
        update("DELETE FROM $commentsTable WHERE id = ?", id)
    }

    private fun readEntry(rs: java.sql.ResultSet): StatusEntry {
        return StatusEntry(
            id = rs.getInt("id"),
            uid = rs.getString("uid"),
            content = rs.getString("content"),
            visibility = StatusVisibility.fromValue(rs.getString("visibility")) ?: StatusVisibility.PUBLIC,
            pinned = rs.getInt("pinned") != 0,
            likeCount = rs.getInt("like_count"),
            commentCount = rs.getInt("comment_count"),
            timestamp = rs.getLong("created_at")
        )
    }
}
