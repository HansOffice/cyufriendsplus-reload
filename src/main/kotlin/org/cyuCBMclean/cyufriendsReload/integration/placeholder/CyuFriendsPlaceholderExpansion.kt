package org.cyuCBMclean.cyufriendsReload.integration.placeholder

import com.github.benmanes.caffeine.cache.Caffeine
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.globalOnlineEntries
import org.cyuCBMclean.cyufriendsReload.extension.isPlayerOnlineGlobally
import org.cyuCBMclean.cyufriendsReload.extension.isRemoteOnline
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPersonalState
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPersonalType
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendTeleportMode
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * PlaceholderAPI 的主变量入口，别在这里做太重的实时计算
 */
class CyuFriendsPlaceholderExpansion(
    private val plugin: CyufriendsReload,
    private val placeholderId: String = "cyufriends"
) : PlaceholderExpansion() {

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val responseCache = Caffeine.newBuilder()
        .expireAfterWrite(2, TimeUnit.SECONDS)
        .maximumSize(4096)
        .build<String, String>()

    override fun getIdentifier(): String = placeholderId

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String {
        if (player == null) return ""
        val key = "${player.uniqueId}:${params.lowercase()}"
        responseCache.getIfPresent(key)?.let { return it }
        return resolve(player, params).also { responseCache.put(key, it) }
    }

    private fun resolve(player: OfflinePlayer, params: String): String {
        val uid = CyuIdHook.getUid(player.uniqueId)
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
        val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
        val lower = params.lowercase()

        return when (lower) {
            "total_count" -> friendModule?.friendManager?.getFriendCountSync(uid)?.toString() ?: "0"
            "request_count" -> friendModule?.requestManager?.countReceivedSync(uid)?.toString() ?: "0"
            "offline_messages_count" -> chatModule?.manager?.unreadCountSync(uid)?.toString() ?: "0"
            "daily_requests_remaining" -> dailyRequestsRemaining(player, uid, friendModule)
            else -> resolveDynamic(uid, params, lower, friendModule, profileModule, socialModule)
        }
    }

    private fun resolveDynamic(
        uid: String,
        params: String,
        lower: String,
        friendModule: FriendModule?,
        profileModule: ProfileModule?,
        socialModule: SocialModule?
    ): String {
        if (friendModule == null) return ""

        if (lower.startsWith("online_player_name_by_slot_")) {
            val index = slotIndex(lower.removePrefix("online_player_name_by_slot_")) ?: return "无效槽位"
            return sortedOnlineEntries().getOrNull(index)?.second ?: "无玩家"
        }

        if (lower.startsWith("is_friend_by_slot_")) {
            val index = slotIndex(lower.removePrefix("is_friend_by_slot_")) ?: return "无效槽位"
            val target = sortedOnlineEntries().getOrNull(index) ?: return "无玩家"
            return if (friendModule.friendManager.isFriendStable(uid, target.first)) "已添加" else "未添加"
        }

        if (lower.startsWith("friendlist_")) {
            return resolveFriendList(uid, params, lower, friendModule, profileModule, socialModule)
        }

        if (lower.startsWith("owned_tag_count_")) {
            val tag = resolveOwnedTag(uid, params.substring("owned_tag_count_".length), friendModule) ?: return "0"
            return friendModule.friendManager.tagSummaries(uid).firstOrNull { it.name.equals(tag, ignoreCase = true) }?.count?.toString() ?: "0"
        }

        if (lower.startsWith("owned_primary_tag_count_")) {
            val tag = resolveOwnedTag(uid, params.substring("owned_primary_tag_count_".length), friendModule) ?: return "0"
            return friendModule.friendManager.tagSummaries(uid).firstOrNull { it.name.equals(tag, ignoreCase = true) }?.primaryCount?.toString() ?: "0"
        }

        if (lower.startsWith("owned_tag_color_")) {
            val tag = resolveOwnedTag(uid, params.substring("owned_tag_color_".length), friendModule) ?: return ""
            return friendModule.friendManager.tagSummaries(uid).firstOrNull { it.name.equals(tag, ignoreCase = true) }?.color ?: ""
        }

        val named = namedPlaceholder(params, lower) ?: return ""
        val targetUid = resolveUid(named.value) ?: return "无数据"

        return when (named.key) {
            "last_online" -> lastOnline(targetUid, friendModule)
            "online_status" -> onlineStatus(targetUid)
            "server" -> plugin.onlineServerName(targetUid)
            "online_scope" -> plugin.onlineScope(targetUid)
            "is_remote" -> plugin.isRemoteOnline(targetUid).toString()
            "note" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.noteName ?: "无备注"
            "note_detail" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.noteDetail ?: "未设置"
            "group" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.groupName ?: "未分组"
            "primary_tag" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.primaryTag() ?: "未设置"
            "tag" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.primaryTag() ?: "未设置"
            "primary_tag_color" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.primaryTagColor() ?: ""
            "tag_color" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.primaryTagColor() ?: ""
            "tags" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.joinedTags().takeUnless { it.isNullOrBlank() } ?: "未设置"
            "tag_count" -> friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.tagNames?.size?.toString() ?: "0"
            "is_pinned" -> if (friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.pinned == true) "是" else "否"
            "is_blocked" -> if (friendModule.blockManager.isBlockedStable(uid, targetUid)) "已屏蔽" else "未屏蔽"
            "first_added" -> formatTime(friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.createdAt ?: 0L)
            "last_interaction" -> formatTime(friendModule.friendManager.getFriendDataStoredSync(uid, targetUid)?.lastInteractionAt ?: 0L)
            "is_friend" -> if (friendModule.friendManager.isFriendStable(uid, targetUid)) "是" else "否"
            "birthday" -> birthday(profileModule, targetUid)
            "is_tp_allowed" -> if (friendModule.preferencesManager.canReceiveTeleportStoredSync(targetUid)) "是" else "否"
            "tp_mode" -> teleportModeName(friendModule.preferencesManager.teleportModeStoredSync(targetUid))
            "effective_tp_mode" -> teleportModeName(friendModule.preferencesManager.resolveTeleportModeStoredSync(targetUid, uid))
            "personal_tp" -> personalStateName(friendModule.preferencesManager.snapshotPersonalStoredSync(uid, targetUid).teleport, FriendPersonalType.TELEPORT)
            "personal_notify" -> personalStateName(friendModule.preferencesManager.snapshotPersonalStoredSync(uid, targetUid).notifyReceive, FriendPersonalType.NOTIFY_RECEIVE)
            "personal_notifyme" -> personalStateName(friendModule.preferencesManager.snapshotPersonalStoredSync(uid, targetUid).notifyBroadcast, FriendPersonalType.NOTIFY_BROADCAST)
            else -> ""
        }
    }

    private fun resolveFriendList(
        uid: String,
        params: String,
        lower: String,
        friendModule: FriendModule,
        profileModule: ProfileModule?,
        socialModule: SocialModule?
    ): String {
        if (lower.startsWith("friendlist_friends_")) {
            val index = positionIndex(lower.removePrefix("friendlist_friends_")) ?: return "无效索引"
            val friendUid = friendModule.friendManager.getFriendEntriesStoredSync(uid).getOrNull(index)?.friendUid ?: return "无数据"
            return CyuIdHook.getName(friendUid) ?: friendUid
        }

        if (lower.startsWith("friendlist_online_status_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_online_status_by_index_"), friendModule) ?: return "无数据"
            return onlineStatus(friendUid)
        }

        if (lower.startsWith("friendlist_last_online_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_last_online_by_index_"), friendModule) ?: return "无数据"
            return lastOnline(friendUid, friendModule)
        }

        if (lower.startsWith("friendlist_note_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_note_by_index_"), friendModule) ?: return "无数据"
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.noteName ?: "无备注"
        }

        if (lower.startsWith("friendlist_note_detail_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_note_detail_by_index_"), friendModule) ?: return "无数据"
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.noteDetail ?: "未设置"
        }

        if (lower.startsWith("friendlist_group_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_group_by_index_"), friendModule) ?: return "无数据"
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.groupName ?: "未分组"
        }

        if (lower.startsWith("friendlist_tag_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_tag_by_index_"), friendModule) ?: return "无数据"
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.primaryTag() ?: "未设置"
        }

        if (lower.startsWith("friendlist_primary_tag_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_primary_tag_by_index_"), friendModule) ?: return "无数据"
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.primaryTag() ?: "未设置"
        }

        if (lower.startsWith("friendlist_tag_color_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_tag_color_by_index_"), friendModule) ?: return ""
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.primaryTagColor() ?: ""
        }

        if (lower.startsWith("friendlist_primary_tag_color_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_primary_tag_color_by_index_"), friendModule) ?: return ""
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.primaryTagColor() ?: ""
        }

        if (lower.startsWith("friendlist_tags_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_tags_by_index_"), friendModule) ?: return "无数据"
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.joinedTags().takeUnless { it.isNullOrBlank() } ?: "未设置"
        }

        if (lower.startsWith("friendlist_tag_count_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_tag_count_by_index_"), friendModule) ?: return "0"
            return friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.tagNames?.size?.toString() ?: "0"
        }

        if (lower.startsWith("friendlist_is_pinned_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_is_pinned_by_index_"), friendModule) ?: return "无数据"
            return if (friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.pinned == true) "是" else "否"
        }

        if (lower.startsWith("friendlist_first_added_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_first_added_by_index_"), friendModule) ?: return "无数据"
            return formatTime(friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.createdAt ?: 0L)
        }

        if (lower.startsWith("friendlist_last_interaction_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_last_interaction_by_index_"), friendModule) ?: return "无数据"
            return formatTime(friendModule.friendManager.getFriendDataStoredSync(uid, friendUid)?.lastInteractionAt ?: 0L)
        }

        if (lower.startsWith("friendlist_is_blocked_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_is_blocked_by_index_"), friendModule) ?: return "无数据"
            return if (friendModule.blockManager.isBlockedStable(uid, friendUid)) "已屏蔽" else "未屏蔽"
        }

        if (lower.startsWith("friendlist_birthday_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_birthday_by_index_"), friendModule) ?: return "无数据"
            return birthday(profileModule, friendUid)
        }

        if (lower.startsWith("friendlist_is_tp_allowed_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_is_tp_allowed_by_index_"), friendModule) ?: return "无数据"
            return if (friendModule.preferencesManager.canReceiveTeleportStoredSync(friendUid)) "是" else "否"
        }

        if (lower.startsWith("friendlist_tp_mode_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_tp_mode_by_index_"), friendModule) ?: return "无数据"
            return teleportModeName(friendModule.preferencesManager.teleportModeStoredSync(friendUid))
        }

        if (lower.startsWith("friendlist_effective_tp_mode_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_effective_tp_mode_by_index_"), friendModule) ?: return "无数据"
            return teleportModeName(friendModule.preferencesManager.resolveTeleportModeStoredSync(friendUid, uid))
        }

        if (lower.startsWith("friendlist_status_count_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_status_count_by_index_"), friendModule) ?: return "0"
            return socialModule?.manager?.getStatusCountCached(friendUid)?.toString() ?: "0"
        }

        if (lower.startsWith("friendlist_status_by_index_")) {
            val pair = intPair(lower.removePrefix("friendlist_status_by_index_")) ?: return "无效索引"
            val friendUid = friendModule.friendManager.getFriendEntriesStoredSync(uid).getOrNull(pair.first)?.friendUid ?: return "无数据"
            return pair.second?.let { socialModule?.manager?.getStatusByIndexSync(friendUid, uid, it) }
                ?: socialModule?.manager?.getLatestStatusSync(friendUid, uid)
                ?: "暂无动态"
        }

        if (lower.startsWith("friendlist_wall_count_by_index_")) {
            val friendUid = friendByIndex(uid, lower.removePrefix("friendlist_wall_count_by_index_"), friendModule) ?: return "0"
            return socialModule?.manager?.getVisibleWallCountSync(friendUid, uid)?.toString() ?: "0"
        }

        if (lower.startsWith("friendlist_wall_sender_by_index_")) {
            val pair = intPair(lower.removePrefix("friendlist_wall_sender_by_index_")) ?: return "无效索引"
            val friendUid = friendModule.friendManager.getFriendEntriesStoredSync(uid).getOrNull(pair.first)?.friendUid ?: return "无数据"
            val entry = (socialModule?.manager?.getWallCommentsSync(friendUid, uid) ?: emptyList()).getOrNull(pair.second ?: 0) ?: return "无数据"
            return CyuIdHook.getName(entry.authorUid) ?: entry.authorUid
        }

        if (lower.startsWith("friendlist_wall_time_by_index_")) {
            val pair = intPair(lower.removePrefix("friendlist_wall_time_by_index_")) ?: return "无效索引"
            val friendUid = friendModule.friendManager.getFriendEntriesStoredSync(uid).getOrNull(pair.first)?.friendUid ?: return "无数据"
            val entry = (socialModule?.manager?.getWallCommentsSync(friendUid, uid) ?: emptyList()).getOrNull(pair.second ?: 0) ?: return "无数据"
            return formatTime(entry.timestamp)
        }

        if (lower.startsWith("friendlist_wall_by_index_")) {
            val pair = intPair(lower.removePrefix("friendlist_wall_by_index_")) ?: return "无效索引"
            val friendUid = friendModule.friendManager.getFriendEntriesStoredSync(uid).getOrNull(pair.first)?.friendUid ?: return "无数据"
            return (socialModule?.manager?.getWallCommentsSync(friendUid, uid) ?: emptyList()).getOrNull(pair.second ?: 0)?.content ?: "暂无留言"
        }

        if (lower.startsWith("friendlist_request_sent_")) {
            val targetUid = resolveUid(params.substring("friendlist_request_sent_".length)) ?: return "0"
            return friendModule.requestManager.countSentSync(targetUid).toString()
        }

        if (lower.startsWith("friendlist_request_received_")) {
            val targetUid = resolveUid(params.substring("friendlist_request_received_".length)) ?: return "0"
            return friendModule.requestManager.countReceivedSync(targetUid).toString()
        }

        if (lower.startsWith("friendlist_request_status_")) {
            val targetUid = resolveUid(params.substring("friendlist_request_status_".length)) ?: return "无数据"
            return requestStatus(uid, targetUid, friendModule)
        }

        if (lower.startsWith("friendlist_friend_count_")) {
            val targetUid = resolveUid(params.substring("friendlist_friend_count_".length)) ?: return "0"
            return friendModule.friendManager.getFriendCountSync(targetUid).toString()
        }

        if (lower.startsWith("friendlist_status_count_")) {
            val targetUid = resolveUid(params.substring("friendlist_status_count_".length)) ?: return "0"
            return socialModule?.manager?.getStatusCountSync(targetUid)?.toString() ?: "0"
        }

        if (lower.startsWith("friendlist_status_")) {
            val targetUid = resolveUid(params.substring("friendlist_status_".length)) ?: return "无数据"
            return socialModule?.manager?.getLatestStatusSync(targetUid, uid) ?: "暂无动态"
        }

        if (lower.startsWith("friendlist_wall_page_count_")) {
            val targetUid = resolveUid(params.substring("friendlist_wall_page_count_".length)) ?: return "0"
            val count = socialModule?.manager?.getVisibleWallCountSync(targetUid, uid) ?: 0
            return ((count + 8) / 9).coerceAtLeast(1).toString()
        }

        if (lower.startsWith("friendlist_wall_page_current_")) {
            return "1"
        }

        if (lower.startsWith("friendlist_wall_count_")) {
            val targetUid = resolveUid(params.substring("friendlist_wall_count_".length)) ?: return "0"
            return socialModule?.manager?.getVisibleWallCountSync(targetUid, uid)?.toString() ?: "0"
        }

        if (lower.startsWith("friendlist_wall_sender_")) {
            val target = trailingNameIndex(params.substring("friendlist_wall_sender_".length)) ?: return "无效索引"
            val targetUid = resolveUid(target.first) ?: return "无数据"
            val entry = (socialModule?.manager?.getWallCommentsSync(targetUid, uid) ?: emptyList()).getOrNull(target.second) ?: return "无数据"
            return CyuIdHook.getName(entry.authorUid) ?: entry.authorUid
        }

        if (lower.startsWith("friendlist_wall_time_")) {
            val target = trailingNameIndex(params.substring("friendlist_wall_time_".length)) ?: return "无效索引"
            val targetUid = resolveUid(target.first) ?: return "无数据"
            val entry = (socialModule?.manager?.getWallCommentsSync(targetUid, uid) ?: emptyList()).getOrNull(target.second) ?: return "无数据"
            return formatTime(entry.timestamp)
        }

        if (lower.startsWith("friendlist_wall_")) {
            val target = trailingNameIndex(params.substring("friendlist_wall_".length)) ?: return "无效索引"
            val targetUid = resolveUid(target.first) ?: return "无数据"
            return (socialModule?.manager?.getWallCommentsSync(targetUid, uid) ?: emptyList()).getOrNull(target.second)?.content ?: "暂无留言"
        }

        return ""
    }

    private fun dailyRequestsRemaining(player: OfflinePlayer, uid: String, friendModule: FriendModule?): String {
        if (friendModule == null) return "0"
        val dailyLimit = requestDailyLimit(player)
        if (dailyLimit <= 0) return "无限制"
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val used = friendModule.requestManager.countSentSinceSync(uid, todayStart)
        return (dailyLimit - used).coerceAtLeast(0).toString()
    }

    private fun requestDailyLimit(player: OfflinePlayer): Int {
        val section = plugin.config.getConfigurationSection("requestLimits") ?: return 20
        var value = section.getConfigurationSection("default")?.getInt("daily", section.getInt("default", 20))
            ?: section.getInt("default", 20)
        val onlinePlayer = if (canUseLivePlayerPermissions()) plugin.server.getPlayer(player.uniqueId) else null
        if (onlinePlayer != null) {
            section.getKeys(false).forEach { group ->
                if (group != "default" && onlinePlayer.hasPermission("cyufriends.request.$group")) {
                    value = maxOf(
                        value,
                        section.getConfigurationSection(group)?.getInt("daily", value) ?: section.getInt(group, value)
                    )
                }
            }
        }
        return value.coerceAtLeast(0)
    }

    private fun sortedOnlineEntries(): List<Pair<String, String>> {
        return plugin.globalOnlineEntries()
            .sortedWith(compareBy({ it.remote }, { it.name.lowercase() }))
            .map { it.uid to it.name }
    }

    private fun canUseLivePlayerPermissions(): Boolean {
        return runCatching { org.bukkit.Bukkit.isPrimaryThread() }.getOrDefault(true)
    }

    private fun slotIndex(value: String): Int? {
        val slot = value.toIntOrNull() ?: return null
        if (slot <= 0) return null
        return slot - 1
    }

    private fun requestStatus(uid: String, targetUid: String, friendModule: FriendModule): String {
        if (friendModule.friendManager.isFriendStable(uid, targetUid)) return "已是好友"
        if (friendModule.requestManager.hasRequestStable(uid, targetUid)) return "已发送"
        if (friendModule.requestManager.hasRequestStable(targetUid, uid)) return "待处理"
        return "未申请"
    }

    private fun resolveOwnedTag(uid: String, input: String, friendModule: FriendModule): String? {
        return friendModule.friendManager.findOwnedTag(uid, input)
    }

    private fun friendByIndex(uid: String, indexText: String, friendModule: FriendModule): String? {
        val index = positionIndex(indexText) ?: return null
        return friendModule.friendManager.getFriendEntriesStoredSync(uid).getOrNull(index)?.friendUid
    }

    private fun namedPlaceholder(params: String, lower: String): NamedPlaceholder? {
        val keys = listOf(
            "last_online",
            "online_status",
            "server",
            "online_scope",
            "is_remote",
            "is_tp_allowed",
            "tp_mode",
            "effective_tp_mode",
            "is_blocked",
            "first_added",
            "last_interaction",
            "is_friend",
            "birthday",
            "personal_notifyme",
            "personal_notify",
            "personal_tp",
            "note",
            "note_detail",
            "group",
            "primary_tag",
            "primary_tag_color",
            "tag",
            "tag_color",
            "tags",
            "tag_count",
            "is_pinned"
        )
        val key = keys.firstOrNull { lower.startsWith("${it}_") } ?: return null
        return NamedPlaceholder(key, params.substring(key.length + 1))
    }

    private fun intPair(value: String): Pair<Int, Int?>? {
        val parts = value.split("_")
        val first = positionIndex(parts.getOrNull(0) ?: return null) ?: return null
        val second = parts.getOrNull(1)?.let { positionIndex(it) ?: return null }
        return first to second
    }

    private fun trailingNameIndex(value: String): Pair<String, Int>? {
        val split = value.lastIndexOf('_')
        if (split <= 0 || split >= value.lastIndex) return null
        val index = positionIndex(value.substring(split + 1)) ?: return null
        return value.substring(0, split) to index
    }

    private fun positionIndex(value: String): Int? {
        val index = value.toIntOrNull() ?: return null
        if (index <= 0) return null
        return index - 1
    }

    private fun onlineStatus(uid: String): String {
        return if (plugin.isPlayerOnlineGlobally(uid)) "在线" else "离线"
    }

    private fun resolveUid(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.toIntOrNull() != null) return trimmed
        if (runCatching { UUID.fromString(trimmed) }.isSuccess) return trimmed
        return CyuIdHook.getUidByName(trimmed)
    }

    private fun lastOnline(uid: String, friendModule: FriendModule): String {
        if (plugin.isPlayerOnlineGlobally(uid)) return "在线"
        return formatTime(friendModule.preferencesManager.lastOnlineSync(uid))
    }

    private fun birthday(profileModule: ProfileModule?, uid: String): String {
        val birthday = profileModule?.manager?.getProfileStoredSync(uid)?.birthday
        return birthday?.takeIf { it != "0000-00-00" && it.isNotBlank() } ?: "未设置生日"
    }

    private fun teleportModeName(mode: FriendTeleportMode): String {
        return when (mode) {
            FriendTeleportMode.DIRECT -> "允许直达"
            FriendTeleportMode.CONFIRM -> "需要确认"
            FriendTeleportMode.DENY -> "拒绝传送"
        }
    }

    private fun personalStateName(state: FriendPersonalState, type: FriendPersonalType): String {
        return state.displayName(type)
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "从未在线"
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }

    private data class NamedPlaceholder(val key: String, val value: String)
}
