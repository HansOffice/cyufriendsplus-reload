package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineEntry
import org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class FriendTimelineView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val targetName: String,
    title: String
) : PaginatedView<RelationshipTimelineEntry>(player, title, pattern, itemsMap, 'E', 'P', 'N') {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm")
    private var cachedEntries: List<RelationshipTimelineEntry> = emptyList()
    private val targetUid: String? = CyuIdHook.getUidByName(targetName)

    override fun getSource(): List<RelationshipTimelineEntry> {
        val resolvedTargetUid = targetUid ?: return emptyList()
        return module.timelineManager.entriesSync(player.uid, resolvedTargetUid).also { cachedEntries = it }
    }

    override fun viewReplacements(): Map<String, String> {
        val latest = cachedEntries.firstOrNull()
        val latestType = latest?.type?.displayName ?: "暂无记录"
        val latestTime = latest?.createdAt
            ?.takeIf { it > 0L }
            ?.let { timeFormat.format(Date(it)) }
            ?: "暂无记录"
        return mapOf(
            "%timeline_count%" to cachedEntries.size.toString(),
            "%timeline_latest_type%" to latestType,
            "%timeline_latest_time%" to latestTime
        )
    }

    override fun mapElement(element: RelationshipTimelineEntry): ItemStack {
        val template = itemsMap['E'] ?: return ItemStack(Material.PAPER)
        val rawName = targetUid?.let { CyuIdHook.getName(it) } ?: targetName
        val friendName = module.friendManager.getFriendDataCached(player.uid, targetUid ?: return ItemStack(Material.PAPER))?.noteName ?: rawName
        val actorName = if (element.actorUid == player.uid) {
            "你"
        } else {
            module.friendManager.getFriendDataCached(player.uid, element.actorUid)?.noteName
                ?: (CyuIdHook.getName(element.actorUid) ?: rawName)
        }
        val replacements = mapOf(
            "%raw_name%" to rawName,
            "%friend_name%" to friendName,
            "%timeline_type%" to element.type.displayName,
            "%timeline_preview%" to element.preview,
            "%timeline_actor%" to actorName,
            "%timeline_time%" to timeFormat.format(Date(element.createdAt)),
            "%timeline_action%" to actionHint(element.type),
            "%timeline_state%" to if (element.actorUid == player.uid) "由你发起" else "由对方发起"
        )
        val baseItem = template.render(player, replacements).clone()
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element.actorUid, player)
    }

    override fun onElementClick(element: RelationshipTimelineEntry, clickType: CyuClickType) {
        val rawName = targetUid?.let { CyuIdHook.getName(it) } ?: targetName
        when (clickType) {
            CyuClickType.RIGHT -> player.performCommand("friend profiledetail $rawName")
            else -> player.performCommand(defaultCommand(element.type, rawName))
        }
    }

    private fun defaultCommand(type: RelationshipTimelineType, rawName: String): String {
        return when (type) {
            RelationshipTimelineType.PRIVATE_MESSAGE -> {
                if (module.plugin.moduleManager.isEnabled("chat")) "friend chat $rawName" else "friend profile $rawName"
            }
            RelationshipTimelineType.STATUS_LIKE,
            RelationshipTimelineType.STATUS_COMMENT -> {
                if (module.plugin.moduleManager.isEnabled("social")) "status view $rawName" else "friend profile $rawName"
            }
            RelationshipTimelineType.WALL_POST,
            RelationshipTimelineType.WALL_LIKE,
            RelationshipTimelineType.WALL_COMMENT -> {
                if (module.plugin.moduleManager.isEnabled("social")) "wall $rawName" else "friend profile $rawName"
            }
        }
    }

    private fun actionHint(type: RelationshipTimelineType): String {
        return when (type) {
            RelationshipTimelineType.PRIVATE_MESSAGE -> "左键前往会话"
            RelationshipTimelineType.STATUS_LIKE,
            RelationshipTimelineType.STATUS_COMMENT -> "左键前往动态"
            RelationshipTimelineType.WALL_POST,
            RelationshipTimelineType.WALL_LIKE,
            RelationshipTimelineType.WALL_COMMENT -> "左键前往留言墙"
        }
    }
}
