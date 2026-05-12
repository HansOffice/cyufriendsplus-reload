package org.cyuCBMclean.cyufriendsReload.modules.social

import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.executeBatch
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

data class WallEntry(
    val id: Int,
    val ownerUid: String,
    val authorUid: String,
    val content: String,
    val visibility: WallVisibility,
    val approved: Boolean,
    val pinned: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val pendingCommentCount: Int,
    val timestamp: Long
)

data class WallComment(
    val id: Int,
    val wallId: Int,
    val authorUid: String,
    val content: String,
    val approved: Boolean,
    val timestamp: Long
)

data class PendingWallReplyEntry(
    val id: Int,
    val wallId: Int,
    val ownerUid: String,
    val authorUid: String,
    val content: String,
    val timestamp: Long
)

class WallRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_social_wall"
    private val likesTable = "cyu_social_wall_likes"
    private val commentsTable = "cyu_social_wall_comments"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            val isSQLite = Settings.databaseType.equals("SQLite", ignoreCase = true)
            val sql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uid VARCHAR(36), author_uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', approved INTEGER DEFAULT 1, pinned INTEGER DEFAULT 0, created_at BIGINT)"
            } else {
                "CREATE TABLE IF NOT EXISTS $tableName (id INT AUTO_INCREMENT PRIMARY KEY, owner_uid VARCHAR(36), author_uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', approved INTEGER DEFAULT 1, pinned INTEGER DEFAULT 0, created_at BIGINT)"
            }
            val commentsSql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INTEGER PRIMARY KEY AUTOINCREMENT, wall_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, approved INTEGER DEFAULT 1, created_at BIGINT NOT NULL)"
            } else {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INT AUTO_INCREMENT PRIMARY KEY, wall_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, approved INTEGER DEFAULT 1, created_at BIGINT NOT NULL)"
            }
            update(sql)
            update("CREATE TABLE IF NOT EXISTS $likesTable (wall_id INT NOT NULL, user_uid VARCHAR(36) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY(wall_id, user_uid))")
            update(commentsSql)
            runCatching { update("ALTER TABLE $tableName ADD COLUMN visibility VARCHAR(16) DEFAULT 'PUBLIC'") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN approved INTEGER DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN pinned INTEGER DEFAULT 0") }
            runCatching { update("ALTER TABLE $commentsTable ADD COLUMN approved INTEGER DEFAULT 1") }
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_created ON $tableName (owner_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_author_created ON $tableName (author_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_pinned_created ON $tableName (owner_uid, pinned, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_approved_created ON $tableName (owner_uid, approved, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${likesTable}_user ON $likesTable (user_uid)")
            update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_wall_created ON $commentsTable (wall_id, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_wall_approved_created ON $commentsTable (wall_id, approved, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_author_created ON $commentsTable (author_uid, created_at)")
        }
    }

    fun createTableSync(databaseManager: DatabaseManager) {
        databaseManager.executeSync {
            val isSQLite = Settings.databaseType.equals("SQLite", ignoreCase = true)
            val sql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uid VARCHAR(36), author_uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', approved INTEGER DEFAULT 1, pinned INTEGER DEFAULT 0, created_at BIGINT)"
            } else {
                "CREATE TABLE IF NOT EXISTS $tableName (id INT AUTO_INCREMENT PRIMARY KEY, owner_uid VARCHAR(36), author_uid VARCHAR(36), content TEXT, visibility VARCHAR(16) DEFAULT 'PUBLIC', approved INTEGER DEFAULT 1, pinned INTEGER DEFAULT 0, created_at BIGINT)"
            }
            val commentsSql = if (isSQLite) {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INTEGER PRIMARY KEY AUTOINCREMENT, wall_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, approved INTEGER DEFAULT 1, created_at BIGINT NOT NULL)"
            } else {
                "CREATE TABLE IF NOT EXISTS $commentsTable (id INT AUTO_INCREMENT PRIMARY KEY, wall_id INT NOT NULL, author_uid VARCHAR(36) NOT NULL, content TEXT NOT NULL, approved INTEGER DEFAULT 1, created_at BIGINT NOT NULL)"
            }
            update(sql)
            update("CREATE TABLE IF NOT EXISTS $likesTable (wall_id INT NOT NULL, user_uid VARCHAR(36) NOT NULL, created_at BIGINT NOT NULL, PRIMARY KEY(wall_id, user_uid))")
            update(commentsSql)
            runCatching { update("ALTER TABLE $tableName ADD COLUMN visibility VARCHAR(16) DEFAULT 'PUBLIC'") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN approved INTEGER DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN pinned INTEGER DEFAULT 0") }
            runCatching { update("ALTER TABLE $commentsTable ADD COLUMN approved INTEGER DEFAULT 1") }
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_created ON $tableName (owner_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_author_created ON $tableName (author_uid, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_pinned_created ON $tableName (owner_uid, pinned, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${tableName}_owner_approved_created ON $tableName (owner_uid, approved, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${likesTable}_user ON $likesTable (user_uid)")
            update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_wall_created ON $commentsTable (wall_id, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_wall_approved_created ON $commentsTable (wall_id, approved, created_at)")
            update("CREATE INDEX IF NOT EXISTS idx_${commentsTable}_author_created ON $commentsTable (author_uid, created_at)")
        }
    }

    suspend fun addComment(owner: String, author: String, content: String, visibility: WallVisibility, approved: Boolean, time: Long) = db.execute {
        update(
            "INSERT INTO $tableName (owner_uid, author_uid, content, visibility, approved, pinned, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            owner,
            author,
            content,
            visibility.id,
            if (approved) 1 else 0,
            0,
            time
        )
    }

    fun addCommentSync(owner: String, author: String, content: String, visibility: WallVisibility, approved: Boolean, time: Long) = db.executeSync {
        update(
            "INSERT INTO $tableName (owner_uid, author_uid, content, visibility, approved, pinned, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            owner,
            author,
            content,
            visibility.id,
            if (approved) 1 else 0,
            0,
            time
        )
    }

    suspend fun getWall(owner: String): List<WallEntry> = db.execute {
        query(
                "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.owner_uid = ? ORDER BY w.pinned DESC, w.created_at DESC",
            owner
        ) { rs ->
            val list = mutableListOf<WallEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    fun getWallSync(owner: String): List<WallEntry> = db.executeSync {
        query(
            "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.owner_uid = ? ORDER BY w.pinned DESC, w.created_at DESC",
            owner
        ) { rs ->
            val list = mutableListOf<WallEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    suspend fun countWall(owner: String): Int = db.execute {
        query("SELECT COUNT(*) FROM $tableName WHERE owner_uid = ?", owner) { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    fun countWallSync(owner: String): Int = db.executeSync {
        query("SELECT COUNT(*) FROM $tableName WHERE owner_uid = ?", owner) { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun getById(id: Int): WallEntry? = db.execute {
        query(
                "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.id = ?",
            id
        ) { rs ->
            if (!rs.next()) return@query null
            readEntry(rs)
        }
    }

    fun getByIdSync(id: Int): WallEntry? = db.executeSync {
        query(
            "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.id = ?",
            id
        ) { rs ->
            if (!rs.next()) return@query null
            readEntry(rs)
        }
    }

    suspend fun delete(id: Int) = db.execute {
        update("DELETE FROM $likesTable WHERE wall_id = ?", id)
        update("DELETE FROM $commentsTable WHERE wall_id = ?", id)
        update("DELETE FROM $tableName WHERE id = ?", id)
    }

    fun deleteSync(id: Int) = db.executeSync {
        update("DELETE FROM $likesTable WHERE wall_id = ?", id)
        update("DELETE FROM $commentsTable WHERE wall_id = ?", id)
        update("DELETE FROM $tableName WHERE id = ?", id)
    }

    suspend fun pending(owner: String): List<WallEntry> = db.execute {
        query(
                "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.owner_uid = ? AND w.approved = 0 ORDER BY w.created_at DESC",
            owner
        ) { rs ->
            val list = mutableListOf<WallEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    fun pendingSync(owner: String): List<WallEntry> = db.executeSync {
        query(
            "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.owner_uid = ? AND w.approved = 0 ORDER BY w.created_at DESC",
            owner
        ) { rs ->
            val list = mutableListOf<WallEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    suspend fun countPendingWalls(ownerUid: String? = null): Int = db.execute {
        if (ownerUid == null) {
            query("SELECT COUNT(*) FROM $tableName WHERE approved = 0") { rs -> if (rs.next()) rs.getInt(1) else 0 }
        } else {
            query("SELECT COUNT(*) FROM $tableName WHERE owner_uid = ? AND approved = 0", ownerUid) { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun countPendingWallsSync(ownerUid: String? = null): Int = db.executeSync {
        if (ownerUid == null) {
            query("SELECT COUNT(*) FROM $tableName WHERE approved = 0") { rs -> if (rs.next()) rs.getInt(1) else 0 }
        } else {
            query("SELECT COUNT(*) FROM $tableName WHERE owner_uid = ? AND approved = 0", ownerUid) { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    suspend fun recentPendingWalls(limit: Int): List<WallEntry> = db.execute {
        query(
            "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.approved = 0 ORDER BY w.created_at DESC LIMIT ?",
            limit
        ) { rs ->
            val list = mutableListOf<WallEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    fun recentPendingWallsSync(limit: Int): List<WallEntry> = db.executeSync {
        query(
            "SELECT w.id, w.owner_uid, w.author_uid, w.content, w.visibility, w.approved, w.pinned, w.created_at, " +
                "(SELECT COUNT(*) FROM $likesTable l WHERE l.wall_id = w.id) AS like_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 1) AS comment_count, " +
                "(SELECT COUNT(*) FROM $commentsTable c WHERE c.wall_id = w.id AND c.approved = 0) AS pending_comment_count " +
                "FROM $tableName w WHERE w.approved = 0 ORDER BY w.created_at DESC LIMIT ?",
            limit
        ) { rs ->
            val list = mutableListOf<WallEntry>()
            while (rs.next()) {
                list.add(readEntry(rs))
            }
            list
        }
    }

    suspend fun trim(owner: String, keep: Int) = db.execute {
        val ids = query("SELECT id FROM $tableName WHERE owner_uid = ? ORDER BY pinned DESC, created_at DESC", owner) { rs ->
            val list = mutableListOf<Int>()
            while (rs.next()) list.add(rs.getInt("id"))
            list
        }.drop(keep.coerceAtLeast(0))

        if (ids.isNotEmpty()) {
            ids.forEach {
                update("DELETE FROM $likesTable WHERE wall_id = ?", it)
                update("DELETE FROM $commentsTable WHERE wall_id = ?", it)
            }
            executeBatch("DELETE FROM $tableName WHERE id = ?", ids.map { arrayOf<Any?>(it) })
        }
    }

    fun trimSync(owner: String, keep: Int) = db.executeSync {
        val ids = query("SELECT id FROM $tableName WHERE owner_uid = ? ORDER BY pinned DESC, created_at DESC", owner) { rs ->
            val list = mutableListOf<Int>()
            while (rs.next()) list.add(rs.getInt("id"))
            list
        }.drop(keep.coerceAtLeast(0))

        if (ids.isNotEmpty()) {
            ids.forEach {
                update("DELETE FROM $likesTable WHERE wall_id = ?", it)
                update("DELETE FROM $commentsTable WHERE wall_id = ?", it)
            }
            executeBatch("DELETE FROM $tableName WHERE id = ?", ids.map { arrayOf<Any?>(it) })
        }
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET owner_uid = ? WHERE owner_uid = ?", newUid, oldUid)
        update("UPDATE $tableName SET author_uid = ? WHERE author_uid = ?", newUid, oldUid)
        update("UPDATE $likesTable SET user_uid = ? WHERE user_uid = ?", newUid, oldUid)
        update("UPDATE $commentsTable SET author_uid = ? WHERE author_uid = ?", newUid, oldUid)
    }

    suspend fun likeWall(id: Int, uid: String, time: Long): Boolean = db.execute {
        val exists = query("SELECT 1 FROM $likesTable WHERE wall_id = ? AND user_uid = ? LIMIT 1", id, uid) { rs -> rs.next() }
        if (exists) return@execute false
        update("INSERT INTO $likesTable (wall_id, user_uid, created_at) VALUES (?, ?, ?)", id, uid, time)
        true
    }

    fun likeWallSync(id: Int, uid: String, time: Long): Boolean = db.executeSync {
        val exists = query("SELECT 1 FROM $likesTable WHERE wall_id = ? AND user_uid = ? LIMIT 1", id, uid) { rs -> rs.next() }
        if (exists) return@executeSync false
        update("INSERT INTO $likesTable (wall_id, user_uid, created_at) VALUES (?, ?, ?)", id, uid, time)
        true
    }

    suspend fun unlikeWall(id: Int, uid: String): Boolean = db.execute {
        update("DELETE FROM $likesTable WHERE wall_id = ? AND user_uid = ?", id, uid) > 0
    }

    fun unlikeWallSync(id: Int, uid: String): Boolean = db.executeSync {
        update("DELETE FROM $likesTable WHERE wall_id = ? AND user_uid = ?", id, uid) > 0
    }

    suspend fun clearPinned(ownerUid: String) = db.execute {
        update("UPDATE $tableName SET pinned = 0 WHERE owner_uid = ?", ownerUid)
    }

    fun clearPinnedSync(ownerUid: String) = db.executeSync {
        update("UPDATE $tableName SET pinned = 0 WHERE owner_uid = ?", ownerUid)
    }

    suspend fun updatePinned(id: Int, pinned: Boolean) = db.execute {
        update("UPDATE $tableName SET pinned = ? WHERE id = ?", if (pinned) 1 else 0, id)
    }

    fun updatePinnedSync(id: Int, pinned: Boolean) = db.executeSync {
        update("UPDATE $tableName SET pinned = ? WHERE id = ?", if (pinned) 1 else 0, id)
    }

    suspend fun updateApproved(id: Int, approved: Boolean) = db.execute {
        update("UPDATE $tableName SET approved = ? WHERE id = ?", if (approved) 1 else 0, id)
    }

    fun updateApprovedSync(id: Int, approved: Boolean) = db.executeSync {
        update("UPDATE $tableName SET approved = ? WHERE id = ?", if (approved) 1 else 0, id)
    }

    suspend fun addReply(wallId: Int, authorUid: String, content: String, approved: Boolean, time: Long) = db.execute {
        update("INSERT INTO $commentsTable (wall_id, author_uid, content, approved, created_at) VALUES (?, ?, ?, ?, ?)", wallId, authorUid, content, if (approved) 1 else 0, time)
    }

    fun addReplySync(wallId: Int, authorUid: String, content: String, approved: Boolean, time: Long) = db.executeSync {
        update("INSERT INTO $commentsTable (wall_id, author_uid, content, approved, created_at) VALUES (?, ?, ?, ?, ?)", wallId, authorUid, content, if (approved) 1 else 0, time)
    }

    suspend fun getReplies(wallId: Int, limit: Int, includePending: Boolean = false): List<WallComment> = db.execute {
        val sql = if (includePending) {
            "SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE wall_id = ? ORDER BY created_at DESC LIMIT ?"
        } else {
            "SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE wall_id = ? AND approved = 1 ORDER BY created_at DESC LIMIT ?"
        }
        query(sql, wallId, limit) { rs ->
            val list = mutableListOf<WallComment>()
            while (rs.next()) {
                list.add(
                    WallComment(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        approved = rs.getInt("approved") != 0,
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    fun getRepliesSync(wallId: Int, limit: Int, includePending: Boolean = false): List<WallComment> = db.executeSync {
        val sql = if (includePending) {
            "SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE wall_id = ? ORDER BY created_at DESC LIMIT ?"
        } else {
            "SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE wall_id = ? AND approved = 1 ORDER BY created_at DESC LIMIT ?"
        }
        query(sql, wallId, limit) { rs ->
            val list = mutableListOf<WallComment>()
            while (rs.next()) {
                list.add(
                    WallComment(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        approved = rs.getInt("approved") != 0,
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    fun getLikedWallIdsSync(userUid: String, wallIds: Collection<Int>): Set<Int> = db.executeSync {
        val ids = wallIds.distinct()
        if (ids.isEmpty()) return@executeSync emptySet()
        val placeholders = ids.joinToString(",") { "?" }
        val params = ArrayList<Any?>(ids.size + 1)
        params += userUid
        params.addAll(ids)
        query(
            "SELECT wall_id FROM $likesTable WHERE user_uid = ? AND wall_id IN ($placeholders)",
            *params.toTypedArray()
        ) { rs ->
            val liked = linkedSetOf<Int>()
            while (rs.next()) {
                liked += rs.getInt("wall_id")
            }
            liked
        }
    }

    suspend fun getPendingReplies(wallId: Int): List<WallComment> = db.execute {
        query("SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE wall_id = ? AND approved = 0 ORDER BY created_at DESC", wallId) { rs ->
            val list = mutableListOf<WallComment>()
            while (rs.next()) {
                list.add(
                    WallComment(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        approved = false,
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    fun getPendingRepliesSync(wallId: Int): List<WallComment> = db.executeSync {
        query("SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE wall_id = ? AND approved = 0 ORDER BY created_at DESC", wallId) { rs ->
            val list = mutableListOf<WallComment>()
            while (rs.next()) {
                list.add(
                    WallComment(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        approved = false,
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    suspend fun countPendingReplies(ownerUid: String? = null): Int = db.execute {
        if (ownerUid == null) {
            query("SELECT COUNT(*) FROM $commentsTable WHERE approved = 0") { rs -> if (rs.next()) rs.getInt(1) else 0 }
        } else {
            query(
                "SELECT COUNT(*) FROM $commentsTable c INNER JOIN $tableName w ON w.id = c.wall_id WHERE c.approved = 0 AND w.owner_uid = ?",
                ownerUid
            ) { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun countPendingRepliesSync(ownerUid: String? = null): Int = db.executeSync {
        if (ownerUid == null) {
            query("SELECT COUNT(*) FROM $commentsTable WHERE approved = 0") { rs -> if (rs.next()) rs.getInt(1) else 0 }
        } else {
            query(
                "SELECT COUNT(*) FROM $commentsTable c INNER JOIN $tableName w ON w.id = c.wall_id WHERE c.approved = 0 AND w.owner_uid = ?",
                ownerUid
            ) { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    suspend fun recentPendingReplies(limit: Int): List<PendingWallReplyEntry> = db.execute {
        query(
            "SELECT c.id, c.wall_id, w.owner_uid, c.author_uid, c.content, c.created_at " +
                "FROM $commentsTable c INNER JOIN $tableName w ON w.id = c.wall_id " +
                "WHERE c.approved = 0 ORDER BY c.created_at DESC LIMIT ?",
            limit
        ) { rs ->
            val list = mutableListOf<PendingWallReplyEntry>()
            while (rs.next()) {
                list.add(
                    PendingWallReplyEntry(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        ownerUid = rs.getString("owner_uid"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    fun recentPendingRepliesSync(limit: Int): List<PendingWallReplyEntry> = db.executeSync {
        query(
            "SELECT c.id, c.wall_id, w.owner_uid, c.author_uid, c.content, c.created_at " +
                "FROM $commentsTable c INNER JOIN $tableName w ON w.id = c.wall_id " +
                "WHERE c.approved = 0 ORDER BY c.created_at DESC LIMIT ?",
            limit
        ) { rs ->
            val list = mutableListOf<PendingWallReplyEntry>()
            while (rs.next()) {
                list.add(
                    PendingWallReplyEntry(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        ownerUid = rs.getString("owner_uid"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    suspend fun recentPendingReplies(ownerUid: String, limit: Int): List<PendingWallReplyEntry> = db.execute {
        query(
            "SELECT c.id, c.wall_id, w.owner_uid, c.author_uid, c.content, c.created_at " +
                "FROM $commentsTable c INNER JOIN $tableName w ON w.id = c.wall_id " +
                "WHERE c.approved = 0 AND w.owner_uid = ? ORDER BY c.created_at DESC LIMIT ?",
            ownerUid,
            limit
        ) { rs ->
            val list = mutableListOf<PendingWallReplyEntry>()
            while (rs.next()) {
                list.add(
                    PendingWallReplyEntry(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        ownerUid = rs.getString("owner_uid"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    fun recentPendingRepliesSync(ownerUid: String, limit: Int): List<PendingWallReplyEntry> = db.executeSync {
        query(
            "SELECT c.id, c.wall_id, w.owner_uid, c.author_uid, c.content, c.created_at " +
                "FROM $commentsTable c INNER JOIN $tableName w ON w.id = c.wall_id " +
                "WHERE c.approved = 0 AND w.owner_uid = ? ORDER BY c.created_at DESC LIMIT ?",
            ownerUid,
            limit
        ) { rs ->
            val list = mutableListOf<PendingWallReplyEntry>()
            while (rs.next()) {
                list.add(
                    PendingWallReplyEntry(
                        id = rs.getInt("id"),
                        wallId = rs.getInt("wall_id"),
                        ownerUid = rs.getString("owner_uid"),
                        authorUid = rs.getString("author_uid"),
                        content = rs.getString("content"),
                        timestamp = rs.getLong("created_at")
                    )
                )
            }
            list
        }
    }

    suspend fun getReplyById(id: Int): WallComment? = db.execute {
        query("SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE id = ?", id) { rs ->
            if (!rs.next()) return@query null
            WallComment(
                id = rs.getInt("id"),
                wallId = rs.getInt("wall_id"),
                authorUid = rs.getString("author_uid"),
                content = rs.getString("content"),
                approved = rs.getInt("approved") != 0,
                timestamp = rs.getLong("created_at")
            )
        }
    }

    fun getReplyByIdSync(id: Int): WallComment? = db.executeSync {
        query("SELECT id, wall_id, author_uid, content, approved, created_at FROM $commentsTable WHERE id = ?", id) { rs ->
            if (!rs.next()) return@query null
            WallComment(
                id = rs.getInt("id"),
                wallId = rs.getInt("wall_id"),
                authorUid = rs.getString("author_uid"),
                content = rs.getString("content"),
                approved = rs.getInt("approved") != 0,
                timestamp = rs.getLong("created_at")
            )
        }
    }

    suspend fun updateReplyApproved(id: Int, approved: Boolean) = db.execute {
        update("UPDATE $commentsTable SET approved = ? WHERE id = ?", if (approved) 1 else 0, id)
    }

    fun updateReplyApprovedSync(id: Int, approved: Boolean) = db.executeSync {
        update("UPDATE $commentsTable SET approved = ? WHERE id = ?", if (approved) 1 else 0, id)
    }

    suspend fun deleteReply(id: Int) = db.execute {
        update("DELETE FROM $commentsTable WHERE id = ?", id)
    }

    fun deleteReplySync(id: Int) = db.executeSync {
        update("DELETE FROM $commentsTable WHERE id = ?", id)
    }

    private fun readEntry(rs: java.sql.ResultSet): WallEntry {
        return WallEntry(
            id = rs.getInt("id"),
            ownerUid = rs.getString("owner_uid"),
            authorUid = rs.getString("author_uid"),
            content = rs.getString("content"),
            visibility = WallVisibility.fromValue(rs.getString("visibility")) ?: WallVisibility.PUBLIC,
            approved = rs.getInt("approved") != 0,
            pinned = rs.getInt("pinned") != 0,
            likeCount = rs.getInt("like_count"),
            commentCount = rs.getInt("comment_count"),
            pendingCommentCount = rs.getInt("pending_comment_count"),
            timestamp = rs.getLong("created_at")
        )
    }
}
