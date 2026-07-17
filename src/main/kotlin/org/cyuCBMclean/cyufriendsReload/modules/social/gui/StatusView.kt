package org.cyuCBMclean.cyufriendsReload.modules.social.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.modules.social.StatusEntry
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionNode
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StatusView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: SocialModule,
    private val targetUid: String? = null,
    title: String = "Status"
) : PaginatedView<StatusEntry>(player, title, pattern, itemsMap, 'S', 'P', 'N') {

    companion object {
        private const val HIDDEN_LINE = "__cyu_hidden__"
    }

    private val dateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private val viewerUid = player.uid
    private val viewerIsAdmin = player.hasPermission("cyufriends.admin")
    private var cachedStatuses: List<StatusEntry> = emptyList()
    @Volatile
    private var likedStatusIds: Set<Int> = emptySet()
    @Volatile
    private var unreadStatusIds: Set<Int> = emptySet()
    @Volatile
    private var submittedSeenAt: Long = 0L

    override fun viewReplacements(): Map<String, String> {
        val targetName = targetUid?.let { CyuIdHook.getName(it) ?: it } ?: player.name
        val selfScoped = targetUid == null || targetUid == viewerUid
        val unreadCount = unreadStatusIds.size
        return mapOf(
            "%view_mode%" to if (targetUid == null) "全服动态" else targetName,
            "%status_target_name%" to targetName,
            "%status_back_label%" to if (selfScoped) "返回主页" else "返回好友资料",
            "%status_back_command%" to if (selfScoped) "friend home" else "friend profile $targetName",
            "%status_switch_label%" to if (selfScoped) "前往我的留言墙" else "前往对方留言墙",
            "%status_unread_count%" to unreadCount.toString(),
            "%status_unread_summary%" to if (unreadCount > 0) "还有 $unreadCount 条未查看更新" else "最近动态都已看完"
        )
    }

    override suspend fun prepareData() {
        cachedStatuses = if (targetUid == null) {
            module.manager.getGlobalStatuses(viewerUid)
        } else {
            module.manager.getStatuses(targetUid, viewerUid)
        }
        likedStatusIds = if (cachedStatuses.isEmpty()) {
            emptySet()
        } else {
            module.manager.getLikedStatusIdsSync(viewerUid, cachedStatuses.map(StatusEntry::id))
        }
        unreadStatusIds = if (targetUid == null || cachedStatuses.isEmpty()) {
            emptySet()
        } else {
            module.manager.unreadStatusIdsSync(targetUid, viewerUid, cachedStatuses)
        }
        val ownerUid = targetUid
        if (ownerUid != null && ownerUid != viewerUid && cachedStatuses.isNotEmpty()) {
            module.manager.markStatusSeenSync(ownerUid, viewerUid, cachedStatuses)
            submittedSeenAt = cachedStatuses.maxOfOrNull(StatusEntry::timestamp) ?: 0L
        }
    }

    override fun getSource(): List<StatusEntry> = cachedStatuses
    override fun mapElement(element: StatusEntry): ItemStack {
        val template = itemsMap['S'] ?: return ItemStack(Material.PAPER)
        val authorName = CyuIdHook.getName(element.uid) ?: "未知玩家"
        val timeString = dateFormat.format(Instant.ofEpochMilli(element.timestamp))
        val replacements = mapOf(
            "%author%" to authorName,
            "%author_uid%" to element.uid,
            "%time%" to timeString,
            "%status_id%" to element.id.toString(),
            "%visibility%" to element.visibility.displayName,
            "%pin_state%" to if (element.pinned) "已置顶" else "普通",
            "%like_count%" to element.likeCount.toString(),
            "%comment_count%" to element.commentCount.toString(),
            "%like_state%" to if (element.id in likedStatusIds) "已点赞" else "未点赞",
            "%unread_state%" to if (element.id in unreadStatusIds) "未读更新" else "已查看"
        ) + actionLoreReplacements(element)
        val baseItem = template.render(player, replacements).clone()
        val meta = baseItem.itemMeta ?: return baseItem

        if (meta.hasLore()) {
            meta.lore = meta.lore
                ?.map { it.replace("%content%", GuiTextFormatter.renderUserText(element.content)) }
                ?.filterNot { it.contains(HIDDEN_LINE) }
        }

        baseItem.itemMeta = meta
        return GuiHeads.applyForUid(baseItem, element.uid, player)
    }

    override fun onElementClick(element: StatusEntry, clickType: CyuClickType) {
        val authorName = CyuIdHook.getName(element.uid) ?: "未知玩家"
        when (clickType) {
            CyuClickType.LEFT -> {
                player.performCommand(if (element.id in likedStatusIds) "status unlike ${element.id}" else "status like ${element.id}")
                return
            }
            CyuClickType.RIGHT -> {
                ActionRegistry.execute(player, listOf(ActionNode("status_comment_input", element.id.toString())))
                return
            }
            CyuClickType.MIDDLE -> {
                player.performCommand("status comments ${element.id}")
                return
            }
            CyuClickType.SHIFT_LEFT -> {
                player.performCommand("friend contact ${element.uid} $authorName")
                return
            }
            CyuClickType.DOUBLE_CLICK -> {
                if (canManageStatus(element)) {
                    player.performCommand(if (element.pinned) "status unpin ${element.id}" else "status pin ${element.id}")
                }
                return
            }
            CyuClickType.SHIFT_RIGHT -> {
                if (canManageStatus(element)) {
                    player.performCommand("status delete ${element.id}")
                } else {
                    player.performCommand("friend contact ${element.uid} $authorName")
                }
                return
            }
            else -> Unit
        }
    }

    private fun actionLoreReplacements(element: StatusEntry): Map<String, String> {
        val canManage = canManageStatus(element)
        val liked = element.id in likedStatusIds
        return mapOf(
            "%status_like_hint_mm%" to "<white>左键</white> <gray>${if (liked) "取消赞" else "点赞"}</gray>",
            "%status_comment_hint_mm%" to "<white>右键</white> <gray>评论</gray>",
            "%status_comments_hint_mm%" to "<white>中键</white> <gray>评论区</gray>",
            "%status_contact_hint_mm%" to "<white>Shift 左键</white> <gray>联系作者</gray>",
            "%status_pin_hint_mm%" to conditionalLine(canManage, "<white>双击</white> <gray>${if (element.pinned) "取消置顶" else "置顶"}</gray>"),
            "%status_delete_hint_mm%" to conditionalLine(canManage, "<white>Shift 右键</white> <gray>删除</gray>")
        )
    }

    private fun canManageStatus(element: StatusEntry): Boolean {
        return viewerUid == element.uid || viewerIsAdmin
    }

    private fun conditionalLine(condition: Boolean, line: String): String {
        return if (condition) line else HIDDEN_LINE
    }
}
