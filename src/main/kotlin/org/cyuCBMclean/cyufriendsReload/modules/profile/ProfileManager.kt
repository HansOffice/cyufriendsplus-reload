package org.cyuCBMclean.cyufriendsReload.modules.profile

import com.github.benmanes.caffeine.cache.Caffeine
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.api.event.CyuProfileUpdateEvent
import org.cyuCBMclean.cyufriendsReload.api.service.ProfileSnapshot
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialInteractionNoticeType
import org.bukkit.Bukkit
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

enum class BirthdaySetResult {
    SUCCESS,
    INVALID_FORMAT,
    LIMIT_REACHED
}

data class BirthdayReminderCounts(
    val today: Int,
    val upcoming: Int
)

data class BirthdayReminderEntry(
    val uid: String,
    val birthday: String,
    val daysAhead: Int
)

/**
 * 玩家资料缓存，签名、生日和隐私设置都从这里取
 */
class ProfileManager(
    private val plugin: CyufriendsReload,
    private val repository: ProfileRepository
) {

    private val defaultBioText = "这个人很神秘，还没有写签名"

    private val profileCache = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<String, ProfileData>()

    suspend fun loadProfile(uid: String): ProfileData {
        val data = repository.getProfile(uid)
        profileCache.put(uid, data)
        DebugLogger.debug(1) { "资料缓存载入: uid=$uid source=db" }
        return data
    }

    fun loadProfileSync(uid: String): ProfileData {
        val data = repository.getProfileSync(uid)
        profileCache.put(uid, data)
        DebugLogger.debug(1) { "资料缓存载入: uid=$uid source=db-sync" }
        return data
    }

    fun getProfile(uid: String): ProfileData? = profileCache.getIfPresent(uid) ?: repository.getProfileSync(uid).also {
        profileCache.put(uid, it)
        DebugLogger.debug(2) { "资料缓存回填: uid=$uid source=db-sync" }
    }

    fun getProfileStoredSync(uid: String): ProfileData {
        return getProfile(uid) ?: loadProfileSync(uid)
    }

    fun unloadProfile(uid: String) {
        profileCache.invalidate(uid)
        DebugLogger.debug(2) { "资料缓存已清理: uid=$uid reason=unload" }
    }

    suspend fun updateProfile(data: ProfileData) {
        repository.saveProfile(data)
        profileCache.put(data.uid, data)
        Bukkit.getPluginManager().callEvent(
            CyuProfileUpdateEvent(
                ProfileSnapshot(
                    uid = data.uid,
                    bio = data.bio,
                    birthday = data.birthday.takeIf { it != "0000-00-00" && it.isNotBlank() },
                    allowRequests = data.allowRequests,
                    allowPrivateMsg = data.allowPrivateMsg,
                    vanishMode = data.vanishMode,
                    onlineScope = plugin.onlineScope(data.uid),
                    serverName = plugin.onlineServerName(data.uid)
                )
            )
        )
        DebugLogger.debug(1) {
            "资料已更新: uid=${data.uid} bioChars=${data.bio.normalizedLength()} birthdaySet=${data.birthday.isNotBlank() && data.birthday != "0000-00-00"} " +
                "allowRequests=${data.allowRequests} allowPrivateMsg=${data.allowPrivateMsg}"
        }
    }

    fun canReceiveRequest(uid: String): Boolean = getProfile(uid)?.allowRequests ?: true
    fun canReceiveMsg(uid: String): Boolean = getProfile(uid)?.allowPrivateMsg ?: true
    fun canReceiveSocialNoticeSync(uid: String, type: SocialInteractionNoticeType): Boolean {
        val profile = getProfileStoredSync(uid)
        return when (type) {
            SocialInteractionNoticeType.STATUS_LIKE -> profile.notifyStatusLike
            SocialInteractionNoticeType.STATUS_COMMENT -> profile.notifyStatusComment
            SocialInteractionNoticeType.WALL_POST -> profile.notifyWallPost
            SocialInteractionNoticeType.WALL_LIKE -> profile.notifyWallLike
            SocialInteractionNoticeType.WALL_COMMENT -> profile.notifyWallComment
        }
    }
    fun canReceiveRequestSync(uid: String): Boolean = getProfileStoredSync(uid).allowRequests
    fun canReceiveMsgSync(uid: String): Boolean = getProfileStoredSync(uid).allowPrivateMsg
    suspend fun canReceiveRequestStored(uid: String): Boolean = profile(uid).allowRequests
    suspend fun canReceiveMsgStored(uid: String): Boolean = profile(uid).allowPrivateMsg

    suspend fun updateSocialNotificationSetting(uid: String, type: SocialInteractionNoticeType, enabled: Boolean): ProfileData {
        val profile = profile(uid)
        when (type) {
            SocialInteractionNoticeType.STATUS_LIKE -> profile.notifyStatusLike = enabled
            SocialInteractionNoticeType.STATUS_COMMENT -> profile.notifyStatusComment = enabled
            SocialInteractionNoticeType.WALL_POST -> profile.notifyWallPost = enabled
            SocialInteractionNoticeType.WALL_LIKE -> profile.notifyWallLike = enabled
            SocialInteractionNoticeType.WALL_COMMENT -> profile.notifyWallComment = enabled
        }
        updateProfile(profile)
        DebugLogger.debug(1) { "社交提醒开关已更新: uid=$uid type=${type.id} enabled=$enabled" }
        return profile
    }

    suspend fun setBirthday(uid: String, birthday: String, limit: Int): BirthdaySetResult {
        val clean = birthday.trim()
        if (!isValidBirthday(clean)) {
            DebugLogger.debug(1) { "生日设置已拒绝: uid=$uid reason=invalid-format chars=${clean.length}" }
            return BirthdaySetResult.INVALID_FORMAT
        }

        val profile = loadProfile(uid)
        if (profile.birthdaySets >= limit.coerceAtLeast(1)) {
            DebugLogger.debug(1) { "生日设置已拒绝: uid=$uid reason=limit-reached used=${profile.birthdaySets} limit=${limit.coerceAtLeast(1)}" }
            return BirthdaySetResult.LIMIT_REACHED
        }

        profile.birthday = clean
        profile.birthdaySets += 1
        updateProfile(profile)
        DebugLogger.debug(1) { "生日已设置: uid=$uid birthdaySets=${profile.birthdaySets}" }
        return BirthdaySetResult.SUCCESS
    }

    suspend fun getBirthday(uid: String): String? {
        val birthday = (getProfile(uid) ?: loadProfile(uid)).birthday
        return birthday.takeIf { it != "0000-00-00" && it.isNotBlank() }
    }

    fun getBirthdaySync(uid: String): String? {
        val birthday = (getProfile(uid) ?: loadProfileSync(uid)).birthday
        return birthday.takeIf { it != "0000-00-00" && it.isNotBlank() }
    }

    suspend fun getTodayBirthdays(): List<String> {
        val today = LocalDate.now().toString().substring(5, 10)
        return repository.getBirthdaysByMonthDay(today)
    }

    fun getTodayBirthdaysSync(): List<String> {
        val today = LocalDate.now().toString().substring(5, 10)
        return repository.getBirthdaysByMonthDaySync(today)
    }

    suspend fun getBirthdaysAfter(daysAhead: Long): List<String> {
        val monthDay = LocalDate.now().plusDays(daysAhead).toString().substring(5, 10)
        return repository.getBirthdaysByMonthDay(monthDay)
    }

    fun getBirthdaysAfterSync(daysAhead: Long): List<String> {
        val monthDay = LocalDate.now().plusDays(daysAhead).toString().substring(5, 10)
        return repository.getBirthdaysByMonthDaySync(monthDay)
    }

    fun birthdayReminderOffsets(): List<Int> {
        return plugin.config.getIntegerList("birthdayReminder.advance-days")
            .ifEmpty { listOf(0, 1) }
            .distinct()
            .sorted()
    }

    fun birthdayReminderCountsSync(friendUids: Set<String>): BirthdayReminderCounts {
        if (friendUids.isEmpty()) return BirthdayReminderCounts(today = 0, upcoming = 0)
        val offsets = birthdayReminderOffsets()
        val today = getTodayBirthdaysSync().count { it in friendUids }
        val upcoming = offsets
            .asSequence()
            .filter { it > 0 }
            .sumOf { offset -> getBirthdaysAfterSync(offset.toLong()).count { it in friendUids } }
        return BirthdayReminderCounts(today = today, upcoming = upcoming)
    }

    fun birthdayEntriesSync(friendUids: Set<String>): List<BirthdayReminderEntry> {
        if (friendUids.isEmpty()) return emptyList()
        val seen = linkedSetOf<String>()
        val entries = mutableListOf<BirthdayReminderEntry>()
        birthdayReminderOffsets().forEach { offset ->
            val dayUids = if (offset == 0) {
                getTodayBirthdaysSync()
            } else {
                getBirthdaysAfterSync(offset.toLong())
            }
            dayUids
                .filter { it in friendUids && seen.add(it) }
                .forEach { friendUid ->
                    val birthday = getBirthdaySync(friendUid) ?: return@forEach
                    entries += BirthdayReminderEntry(
                        uid = friendUid,
                        birthday = birthday,
                        daysAhead = offset
                    )
                }
        }
        return entries.sortedWith(
            compareBy<BirthdayReminderEntry> { it.daysAhead }
                .thenBy { it.birthday }
                .thenBy { it.uid }
        )
    }

    suspend fun isBirthdayToday(uid: String): Boolean {
        val birthday = getBirthday(uid) ?: return false
        return birthday.endsWith(LocalDate.now().toString().substring(5, 10))
    }

    suspend fun checkAndMarkBirthdayReminder(uid: String): Boolean {
        return repository.markBirthdayReminder(uid, LocalDate.now().toString()).also { marked ->
            DebugLogger.debug(1) { "生日提醒标记已更新: uid=$uid marked=$marked" }
        }
    }

    suspend fun checkAndMarkBirthdayBroadcast(uid: String, scope: String): Boolean {
        return repository.markBirthdayBroadcast(uid, scope).also { marked ->
            DebugLogger.debug(1) { "生日广播标记已更新: uid=$uid scope=$scope marked=$marked" }
        }
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        repository.updateUid(oldUid, newUid)
        profileCache.invalidate(oldUid)
        profileCache.invalidate(newUid)
        DebugLogger.debug(1) { "资料 UID 已迁移: oldUid=$oldUid newUid=$newUid" }
    }

    fun invalidate(uid: String) {
        profileCache.invalidate(uid)
        DebugLogger.debug(2) { "资料缓存已清理: uid=$uid reason=invalidate" }
    }

    fun bioMaxLength(): Int {
        return plugin.config.getInt("profile.bio-max-length", 64).coerceIn(1, 512)
    }

    fun bioPreviewLength(): Int {
        return plugin.config.getInt("profile.bio-preview-length", 32).coerceIn(8, 256)
    }

    fun normalizeBio(content: String): String {
        return content.trim()
    }

    fun previewBio(content: String): String {
        val bio = displayBio(content)
        val previewLength = bioPreviewLength()
        if (bio.length <= previewLength) return bio
        return bio.take(previewLength).trimEnd() + "..."
    }

    fun displayBio(content: String): String {
        val bio = normalizeBio(content)
        return if (bio.isBlank()) defaultBioText else bio
    }

    private fun isValidBirthday(value: String): Boolean {
        return try {
            LocalDate.parse(value)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    fun birthdayLimit(player: Player): Int {
        val section = plugin.config.getConfigurationSection("birthdayLimits")
        if (section == null) return 1

        var limit = section.getInt("default", 1).coerceAtLeast(1)
        section.getKeys(false).forEach { key ->
            if (key != "default" && player.hasPermission("cyufriends.birthday.$key")) {
                limit = maxOf(limit, section.getInt(key, limit).coerceAtLeast(1))
            }
        }
        return limit
    }

    fun cachedProfileCount(): Int = profileCache.asMap().size

    private suspend fun profile(uid: String): ProfileData {
        return getProfile(uid) ?: loadProfile(uid)
    }

    private fun String?.normalizedLength(): Int {
        return this?.trim()?.length ?: 0
    }
}
