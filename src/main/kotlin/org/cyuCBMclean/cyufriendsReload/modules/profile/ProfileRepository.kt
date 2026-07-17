package org.cyuCBMclean.cyufriendsReload.modules.profile

import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.database.BaseRepository
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.database.query
import org.cyuCBMclean.cyufriendsReload.core.database.update

data class ProfileData(
    val uid: String,
    var bio: String = ProfileRepository.DEFAULT_BIO,
    var birthday: String = "0000-00-00",
    var allowRequests: Boolean = true,
    var allowPrivateMsg: Boolean = true,
    var notifyStatusLike: Boolean = true,
    var notifyStatusComment: Boolean = true,
    var notifyWallPost: Boolean = true,
    var notifyWallLike: Boolean = true,
    var notifyWallComment: Boolean = true,
    var vanishMode: Boolean = false,
    var birthdaySets: Int = 0,
    var lastBirthdayReminder: String? = null,
    var lastBirthdayBroadcast: String? = null
)

class ProfileRepository(private val db: DatabaseManager) : BaseRepository {

    override val tableName = "cyu_player_profiles"

    override suspend fun createTable(databaseManager: DatabaseManager) {
        databaseManager.execute {
            update(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "uid VARCHAR(36) PRIMARY KEY, " +
                    "bio TEXT, " +
                    "birthday VARCHAR(10), " +
                    "allow_requests BOOLEAN, " +
                    "allow_msg BOOLEAN, " +
                    "notify_status_like BOOLEAN DEFAULT 1, " +
                    "notify_status_comment BOOLEAN DEFAULT 1, " +
                    "notify_wall_post BOOLEAN DEFAULT 1, " +
                    "notify_wall_like BOOLEAN DEFAULT 1, " +
                    "notify_wall_comment BOOLEAN DEFAULT 1, " +
                    "vanish_mode BOOLEAN, " +
                    "birthday_sets INT DEFAULT 0, " +
                    "last_birthday_reminder VARCHAR(10), " +
                    "last_birthday_broadcast VARCHAR(16))"
            )
            runCatching { update("ALTER TABLE $tableName ADD COLUMN birthday_sets INT DEFAULT 0") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN last_birthday_reminder VARCHAR(10)") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN last_birthday_broadcast VARCHAR(16)") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN notify_status_like BOOLEAN DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN notify_status_comment BOOLEAN DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN notify_wall_post BOOLEAN DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN notify_wall_like BOOLEAN DEFAULT 1") }
            runCatching { update("ALTER TABLE $tableName ADD COLUMN notify_wall_comment BOOLEAN DEFAULT 1") }
            if (Settings.databaseType.equals("SQLite", ignoreCase = true)) {
                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_birthday ON $tableName (birthday)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_birthday_reminder ON $tableName (last_birthday_reminder)")
                }

                runCatching {
                    update("CREATE INDEX IF NOT EXISTS idx_${tableName}_birthday_broadcast ON $tableName (last_birthday_broadcast)")
                }
            } else {
                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_birthday (birthday)")
                }

                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_birthday_reminder (last_birthday_reminder)")
                }

                runCatching {
                    update("ALTER TABLE $tableName ADD INDEX idx_${tableName}_birthday_broadcast (last_birthday_broadcast)")
                }
            }
        }
    }

    suspend fun getProfile(uid: String): ProfileData = db.execute {
        readProfile(uid)
    }

    fun getProfileSync(uid: String): ProfileData = db.executeSync {
        readProfile(uid)
    }

    suspend fun saveProfile(data: ProfileData) = db.execute {
        update(
            "REPLACE INTO $tableName (" +
                "uid, bio, birthday, allow_requests, allow_msg, " +
                "notify_status_like, notify_status_comment, notify_wall_post, notify_wall_like, notify_wall_comment, " +
                "vanish_mode, birthday_sets, last_birthday_reminder, last_birthday_broadcast" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            data.uid,
            data.bio,
            data.birthday,
            data.allowRequests,
            data.allowPrivateMsg,
            data.notifyStatusLike,
            data.notifyStatusComment,
            data.notifyWallPost,
            data.notifyWallLike,
            data.notifyWallComment,
            data.vanishMode,
            data.birthdaySets,
            data.lastBirthdayReminder,
            data.lastBirthdayBroadcast
        )
    }

    suspend fun updateUid(oldUid: String, newUid: String) = db.execute {
        update("UPDATE $tableName SET uid = ? WHERE uid = ?", newUid, oldUid)
    }

    suspend fun getBirthdaysByMonthDay(monthDay: String): List<String> = db.execute {
        query("SELECT uid FROM $tableName WHERE birthday IS NOT NULL AND birthday <> ? AND SUBSTR(birthday, 6, 5) = ?", "0000-00-00", monthDay) { rs ->
            val list = mutableListOf<String>()
            while (rs.next()) {
                list.add(rs.getString("uid"))
            }
            list
        }
    }

    fun getBirthdaysByMonthDaySync(monthDay: String): List<String> = db.executeSync {
        query("SELECT uid FROM $tableName WHERE birthday IS NOT NULL AND birthday <> ? AND SUBSTR(birthday, 6, 5) = ?", "0000-00-00", monthDay) { rs ->
            val list = mutableListOf<String>()
            while (rs.next()) {
                list.add(rs.getString("uid"))
            }
            list
        }
    }

    suspend fun markBirthdayReminder(uid: String, date: String): Boolean = db.execute {
        update("UPDATE $tableName SET last_birthday_reminder = ? WHERE uid = ? AND (last_birthday_reminder IS NULL OR last_birthday_reminder <> ?)", date, uid, date) > 0
    }

    suspend fun markBirthdayBroadcast(uid: String, scope: String): Boolean = db.execute {
        update("UPDATE $tableName SET last_birthday_broadcast = ? WHERE uid = ? AND (last_birthday_broadcast IS NULL OR last_birthday_broadcast <> ?)", scope, uid, scope) > 0
    }

    private fun java.sql.Connection.readProfile(uid: String): ProfileData {
        return query("SELECT * FROM $tableName WHERE uid = ?", uid) { rs ->
            if (rs.next()) {
                ProfileData(
                    uid = uid,
                    bio = rs.getString("bio") ?: DEFAULT_BIO,
                    birthday = rs.getString("birthday") ?: "0000-00-00",
                    allowRequests = rs.getBoolean("allow_requests"),
                    allowPrivateMsg = rs.getBoolean("allow_msg"),
                    notifyStatusLike = rs.getBoolean("notify_status_like"),
                    notifyStatusComment = rs.getBoolean("notify_status_comment"),
                    notifyWallPost = rs.getBoolean("notify_wall_post"),
                    notifyWallLike = rs.getBoolean("notify_wall_like"),
                    notifyWallComment = rs.getBoolean("notify_wall_comment"),
                    vanishMode = rs.getBoolean("vanish_mode"),
                    birthdaySets = rs.getInt("birthday_sets"),
                    lastBirthdayReminder = rs.getString("last_birthday_reminder"),
                    lastBirthdayBroadcast = rs.getString("last_birthday_broadcast")
                )
            } else {
                ProfileData(uid)
            }
        }
    }

    companion object {
        const val DEFAULT_BIO = "这个人很懒，还没有留下签名。"
    }
}
