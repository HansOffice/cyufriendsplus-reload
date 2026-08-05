package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.integration.hook.IntimacyHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendDefaults
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPersonalState
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPersonalType
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView
import java.text.SimpleDateFormat
import java.util.Date

class FriendProfileView(
    player: Player,
    pattern: GuiPattern,
    itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val friendUid: String,
    private val friendName: String,
    title: String = "Friend Profile"
) : CyuView(player, title, pattern, itemsMap) {

    private val dynamicSlots = mutableMapOf<Int, LayoutBinding>()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    override fun onRender() {
        bindDynamicSlots()
        rerenderLayoutBindings()
        val replacements = replacements(friendUid)

        dynamicSlots.forEach { (slot, binding) ->
            val template = binding.template
            if (!isTemplateAvailable(template)) {
                setItem(slot, null)
                return@forEach
            }

            val item = template.render(player, replacements).clone()
            val result = if (!template.hasHeadSource() && isPlayerHead(item)) {
                GuiHeads.applyForUid(item, friendUid, player)
            } else {
                item
            }
            setItem(slot, result)
        }
    }

    override fun onDynamicClick(slot: Int, clickType: CyuClickType) {
        val binding = dynamicSlots[slot] ?: return
        if (!isTemplateAvailable(binding.template)) return
        val nodes = binding.template.actions[clickType] ?: binding.template.actions[CyuClickType.ALL] ?: return
        val replacements = replacements(friendUid)
        ActionRegistry.execute(
            player,
            nodes.map { node ->
                node.copy(
                    payload = replacements.entries.fold(node.payload) { current, entry ->
                        current.replace(entry.key, entry.value)
                    }
                )
            }
        )
    }

    private fun bindDynamicSlots() {
        if (dynamicSlots.isNotEmpty()) return
        layoutActions.toMap().forEach { (slot, binding) ->
            dynamicSlots[slot] = binding
            layoutActions.remove(slot)
        }
    }

    private fun replacements(targetUid: String): Map<String, String> {
        val friendData = module.friendManager.getFriendData(player.uid, targetUid)
        val groupEnabled = module.plugin.moduleManager.isEnabled("group")
        val rawName = CyuIdHook.getName(targetUid) ?: friendName
        val displayName = friendData?.noteName ?: rawName
        val noteName = friendData?.noteName ?: "未设置"
        val noteDetail = friendData?.noteDetail ?: "未设置"
        val groupName = if (groupEnabled) {
            friendData?.groupName ?: FriendDefaults.DEFAULT_GROUP_NAME
        } else {
            "未启用"
        }
        val primaryTag = friendData?.primaryTag() ?: "未设置"
        val primaryEditTag = friendData?.primaryTag() ?: friendData?.orderedTags()?.firstOrNull() ?: ""
        val tags = friendData?.joinedTags().takeUnless { it.isNullOrBlank() } ?: "未设置"
        val tagsMm = friendData?.joinedColoredTags().takeUnless { it.isNullOrBlank() } ?: "<gray>未设置</gray>"
        val primaryTagMm = friendData?.primaryColoredTag() ?: "<gray>未设置</gray>"
        val primaryTagColor = friendData?.primaryTagColor() ?: FriendDefaults.TAG_COLOR_PALETTE.first()
        val primaryFocusMm = if (friendData?.primaryTag().isNullOrBlank()) {
            "<gray>尚未设置主标签</gray>"
        } else {
            "<color:$primaryTagColor><bold>当前优先展示标签</bold></color>"
        }
        val tagCount = friendData?.tagNames?.size?.toString() ?: "0"
        val pinState = if (friendData?.pinned == true) "已置顶" else "未置顶"
        val groupPinState = if (groupEnabled) {
            if (module.preferencesManager.isGroupPinnedCached(player.uid, groupName)) "分组置顶" else "普通显示"
        } else {
            "未启用"
        }
        val friendSince = friendData?.createdAt?.takeIf { it > 0L }?.let { timeFormat.format(Date(it)) } ?: "未知时间"
        val lastInteraction = friendData?.lastInteractionAt?.takeIf { it > 0L }?.let { timeFormat.format(Date(it)) } ?: "暂无记录"
        val personal = module.preferencesManager.snapshotPersonal(player.uid, targetUid)
        val mutualFriends = module.friendManager.mutualFriendUidsCached(player.uid, targetUid)
        val mutualPreview = mutualFriends.take(4)
            .map { CyuIdHook.getName(it) ?: it }
            .joinToString("、")
            .ifBlank { "暂无共同好友" }
        val socialModule = module.plugin.moduleManager.getModule<SocialModule>("social")
        val statusUnreadCount = socialModule?.manager?.unreadStatusCountSync(targetUid, player.uid) ?: 0
        val wallUnreadCount = socialModule?.manager?.unreadWallCountSync(targetUid, player.uid) ?: 0
        val timelineCount = module.timelineManager.entriesSync(player.uid, targetUid, 6).size
        val intimacy = IntimacyHook.snapshot(player.uid, targetUid)

        return mapOf(
            "%friend_name%" to displayName,
            "%raw_name%" to rawName,
            "%note_name%" to noteName,
            "%note_detail%" to noteDetail,
            "%group_name%" to groupName,
            "%tag_name%" to tags,
            "%primary_tag%" to primaryTag,
            "%primary_edit_tag%" to primaryEditTag,
            "%primary_tag_color%" to primaryTagColor,
            "%tag_color%" to primaryTagColor,
            "%primary_tag_mm%" to primaryTagMm,
            "%primary_focus_mm%" to primaryFocusMm,
            "%tags%" to tags,
            "%tags_mm%" to tagsMm,
            "%tag_count%" to tagCount,
            "%pin_state%" to pinState,
            "%group_pin_state%" to groupPinState,
            "%friend_since%" to friendSince,
            "%last_interaction%" to lastInteraction,
            "%mutual_friend_count%" to mutualFriends.size.toString(),
            "%mutual_friend_preview%" to mutualPreview,
            "%server_name%" to CyufriendsReload.instance.onlineServerName(targetUid),
            "%online_scope%" to CyufriendsReload.instance.onlineScope(targetUid),
            "%personal_tp%" to personalStateName(personal.teleport, FriendPersonalType.TELEPORT),
            "%personal_notify%" to personalStateName(personal.notifyReceive, FriendPersonalType.NOTIFY_RECEIVE),
            "%personal_notifyme%" to personalStateName(personal.notifyBroadcast, FriendPersonalType.NOTIFY_BROADCAST),
            "%personal_status_like%" to personalStateName(personal.statusLikeNotice, FriendPersonalType.STATUS_LIKE_NOTICE),
            "%personal_status_comment%" to personalStateName(personal.statusCommentNotice, FriendPersonalType.STATUS_COMMENT_NOTICE),
            "%personal_wall_post%" to personalStateName(personal.wallPostNotice, FriendPersonalType.WALL_POST_NOTICE),
            "%personal_wall_like%" to personalStateName(personal.wallLikeNotice, FriendPersonalType.WALL_LIKE_NOTICE),
            "%personal_wall_comment%" to personalStateName(personal.wallCommentNotice, FriendPersonalType.WALL_COMMENT_NOTICE),
            "%status_unread_count%" to statusUnreadCount.toString(),
            "%wall_unread_count%" to wallUnreadCount.toString(),
            "%status_unread_state%" to if (statusUnreadCount > 0) "有新动态" else "已看完",
            "%wall_unread_state%" to if (wallUnreadCount > 0) "有新留言" else "已看完",
            "%timeline_count%" to timelineCount.toString(),
            "%intimacy_points%" to (intimacy?.points?.toString() ?: "0"),
            "%intimacy_level%" to (intimacy?.levelName ?: "未记录"),
            "%intimacy_level_color%" to (intimacy?.levelColor ?: "&7"),
            "%intimacy_level_mm%" to intimacyLevelText(intimacy),
            "%intimacy_next_level%" to (intimacy?.nextLevelName ?: "已满级"),
            "%intimacy_next_points%" to (intimacy?.nextLevelRemaining?.toString() ?: "0"),
            "%intimacy_days%" to (intimacy?.friendshipDays?.toString() ?: "0"),
            "%intimacy_rank%" to (intimacy?.rank?.toString() ?: "未上榜"),
            "%uid%" to targetUid
        )
    }

    private fun intimacyLevelText(snapshot: org.cyuCBMclean.cyufriendsReload.integration.hook.IntimacySnapshot?): String {
        if (snapshot == null) return "<gray>未记录</gray>"
        val color = snapshot.levelColor.replace("&", "")
        return if (color.startsWith("#")) {
            "<color:$color>${snapshot.levelName}</color>"
        } else {
            "<white>${snapshot.levelName}</white>"
        }
    }

    private fun personalStateName(state: FriendPersonalState, type: FriendPersonalType): String {
        return state.displayName(type)
    }

    private fun isPlayerHead(item: ItemStack): Boolean {
        val typeName = item.type.name
        return typeName.equals("PLAYER_HEAD", true) || typeName.equals("SKULL_ITEM", true)
    }

    private fun isTemplateAvailable(template: ItemTemplate): Boolean {
        return template.actions.values
            .flatten()
            .all(::isActionAvailable)
    }

    private fun isActionAvailable(node: org.cyuCBMclean.cyufriendsReload.ui.action.ActionNode): Boolean {
        if (node.executorId != "player") return true
        val payload = node.payload.lowercase()
        return when {
            payload.startsWith("friend chat ") -> module.plugin.moduleManager.isEnabled("chat")
            payload.startsWith("friend group") -> module.plugin.moduleManager.isEnabled("group")
            payload.startsWith("friend profilesocial") -> module.plugin.moduleManager.isEnabled("social")
            payload.startsWith("status ") || payload == "status" -> module.plugin.moduleManager.isEnabled("social")
            payload.startsWith("wall ") || payload == "wall" -> module.plugin.moduleManager.isEnabled("social")
            else -> true
        }
    }
}
