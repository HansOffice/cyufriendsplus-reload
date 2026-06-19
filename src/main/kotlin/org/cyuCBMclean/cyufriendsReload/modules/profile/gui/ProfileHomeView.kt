package org.cyuCBMclean.cyufriendsReload.modules.profile.gui

import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.globalOnlineCount
import org.cyuCBMclean.cyufriendsReload.extension.onlineCountGlobally
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendTeleportMode
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class ProfileHomeView(
    player: Player,
    pattern: GuiPattern,
    itemsMap: Map<Char, ItemTemplate>,
    private val plugin: CyufriendsReload,
    private val profileModule: ProfileModule,
    title: String
) : CyuView(player, title, pattern, itemsMap) {

    override fun onRender() {
        rerenderLayoutBindings()
        if (!plugin.moduleManager.isEnabled("chat")) {
            hideStaticSymbol('M')
        }
        if (!plugin.moduleManager.isEnabled("group")) {
            hideStaticSymbol('G')
        }
        if (!plugin.moduleManager.isEnabled("social")) {
            hideStaticSymbols('S', 'W')
        }
    }

    override fun viewReplacements(): Map<String, String> {
        val uid = player.uid
        val profile = profileModule.manager.getProfileStoredSync(uid)
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
        val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
        val groupEnabled = plugin.moduleManager.isEnabled("group")
        val friendEntries = friendModule?.friendManager?.getFriendEntriesStoredSync(uid) ?: emptyList()
        val friends = friendEntries.map { it.friendUid }.toSet()
        val onlineFriends = plugin.onlineCountGlobally(friends)
        val groups = if (groupEnabled) {
            friendEntries.map { it.groupName }.filter { it.isNotBlank() }.toSet().size
        } else {
            0
        }
        val requests = friendModule?.requestManager?.countReceivedSync(uid) ?: 0
        val sentRequests = friendModule?.requestManager?.countSentSync(uid) ?: 0
        val blacklist = friendModule?.blockManager?.getBlocksStoredSync(uid)?.size ?: 0
        val unread = chatModule?.manager?.unreadCountSync(uid) ?: 0
        val statuses = socialModule?.manager?.getStatusCountSync(uid) ?: 0
        val wall = socialModule?.manager?.getVisibleWallCountSync(uid) ?: 0
        val pendingWalls = socialModule?.manager?.pendingWallCountSync(uid) ?: 0
        val pendingReplies = socialModule?.manager?.pendingWallReplyCountSync(uid) ?: 0
        val recommends = friendModule?.friendManager?.recommendationsStoredSync(uid, 8)?.size ?: 0
        val preferences = friendModule?.preferencesManager?.snapshotStoredSync(uid)
        val birthday = profile.birthday.takeUnless { it == "0000-00-00" } ?: "未设置"
        val bio = profileModule.manager.previewBio(profile.bio)
        val birthdayCounts = profileModule.manager.birthdayReminderCountsSync(friends)
        val todayBirthdays = birthdayCounts.today
        val upcomingBirthdays = birthdayCounts.upcoming
        val notificationCount = requests + unread + pendingWalls + pendingReplies + todayBirthdays

        return mapOf(
            "%player_name%" to player.name,
            "%player_uid%" to uid,
            "%player_uid_label%" to CyuIdHook.displayLabel(uid),
            "%player_uid_display%" to CyuIdHook.displayValue(uid),
            "%friend_count%" to friends.size.toString(),
            "%friend_online_count%" to onlineFriends.toString(),
            "%group_count%" to groups.toString(),
            "%request_count%" to requests.toString(),
            "%sent_request_count%" to sentRequests.toString(),
            "%blacklist_count%" to blacklist.toString(),
            "%unread_count%" to unread.toString(),
            "%status_count%" to statuses.toString(),
            "%wall_count%" to wall.toString(),
            "%pending_wall_count%" to pendingWalls.toString(),
            "%pending_reply_count%" to pendingReplies.toString(),
            "%notification_count%" to notificationCount.toString(),
            "%recommend_count%" to recommends.toString(),
            "%birthday_today_count%" to todayBirthdays.toString(),
            "%birthday_upcoming_count%" to upcomingBirthdays.toString(),
            "%online_count%" to plugin.globalOnlineCount().toString(),
            "%birthday%" to birthday,
            "%bio%" to bio,
            "%bio_limit%" to profileModule.manager.bioMaxLength().toString(),
            "%notify_state%" to state(preferences?.notifyOnJoin ?: true),
            "%notifyme_state%" to state(preferences?.notifyOwnFriends ?: true),
            "%tp_state%" to teleportMode(preferences?.teleportMode)
        )
    }

    private fun state(enabled: Boolean): String {
        return if (enabled) "开启" else "关闭"
    }

    private fun teleportMode(mode: FriendTeleportMode?): String {
        return when (mode) {
            FriendTeleportMode.CONFIRM -> "需要确认"
            FriendTeleportMode.DENY -> "拒绝传送"
            else -> "允许直达"
        }
    }
}
