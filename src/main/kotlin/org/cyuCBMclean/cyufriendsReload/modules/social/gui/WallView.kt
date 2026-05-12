package org.cyuCBMclean.cyufriendsReload.modules.social.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.modules.social.WallEntry
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionNode
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class WallView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: SocialModule,
    private val targetName: String,
    title: String = "Wall"
) : PaginatedView<WallEntry>(player, title, pattern, itemsMap, 'W', 'P', 'N') {

    companion object {
        private const val HIDDEN_LINE = "__cyu_hidden__"
    }

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm")
    private val targetUid = CyuIdHook.getUidByName(targetName)
    private val viewerUid = player.uid
    private val viewerName = player.name
    private val viewerIsAdmin = player.hasPermission("cyufriends.admin")

    @Volatile
    private var loading = false

    @Volatile
    private var visibleCount = 0

    @Volatile
    private var pendingWallCount = 0

    @Volatile
    private var pendingReplyCount = 0
    @Volatile
    private var likedWallIds: Set<Int> = emptySet()
    @Volatile
    private var unreadWallIds: Set<Int> = emptySet()
    @Volatile
    private var submittedSeenAt: Long = 0L

    override fun viewReplacements(): Map<String, String> {
        val ownerName = resolvedOwnerName()
        val selfScoped = targetUid == viewerUid || ownerName.equals(viewerName, ignoreCase = true)
        val unreadCount = unreadWallIds.size
        return mapOf(
            "%target_name%" to ownerName,
            "%wall_back_label%" to if (selfScoped) "返回主页" else "返回好友资料",
            "%wall_back_command%" to if (selfScoped) "friend home" else "friend profile $ownerName",
            "%wall_status_label%" to if (selfScoped) "前往我的动态" else "查看对方动态",
            "%wall_status_command%" to "status view $ownerName",
            "%wall_unread_count%" to unreadCount.toString(),
            "%wall_unread_summary%" to if (unreadCount > 0) "还有 $unreadCount 条未查看留言" else "留言墙已看完"
        )
    }

    override fun onRender() {
        super.onRender()
        if (!canReviewPending()) {
            hideStaticSymbol('R')
        }
        if (!canPostToWall()) {
            hideStaticSymbol('I')
        }
    }

    override fun getSource(): List<WallEntry> {
        val ownerUid = targetUid ?: return emptyList()
        val cached = module.manager.getWallCommentsCached(ownerUid, viewerUid)
        if (cached.isNotEmpty()) {
            visibleCount = cached.size
            pendingWallCount = if (canReviewPending()) cached.count { !it.approved } else 0
            pendingReplyCount = if (canReviewPending()) cached.sumOf { it.pendingCommentCount } else 0
            return cached.also(::refreshState)
        }
        if (!loading) {
            loading = true
            CyuConcurrency.scheduler.runAsync(module.plugin) {
                val loaded = runCatching { runBlocking { module.manager.getWallComments(ownerUid, viewerUid) } }.getOrDefault(emptyList())
                val loadedPendingWalls = if (canReviewPending()) {
                    runCatching { runBlocking { module.manager.getPendingWallEntries(ownerUid).size } }.getOrDefault(0)
                } else {
                    0
                }
                val loadedPendingReplies = if (canReviewPending()) loaded.sumOf { it.pendingCommentCount } else 0
                CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                    visibleCount = loaded.size
                    pendingWallCount = loadedPendingWalls
                    pendingReplyCount = loadedPendingReplies
                    loading = false
                    onRender()
                }
            }
        }
        return emptyList<WallEntry>().also {
            likedWallIds = emptySet()
            unreadWallIds = emptySet()
        }
    }

    override fun layoutReplacements(symbol: Char, slot: Int): Map<String, String> {
        val ownerName = resolvedOwnerName()
        return mapOf(
            "%owner%" to ownerName,
            "%visible_count%" to visibleCount.toString(),
            "%pending_count%" to pendingWallCount.toString(),
            "%pending_reply_count%" to pendingReplyCount.toString(),
            "%pending_hint%" to if (pendingWallCount > 0) "当前还有 $pendingWallCount 条待审留言" else "当前没有待审留言",
            "%pending_reply_hint%" to if (pendingReplyCount > 0) "当前还有 $pendingReplyCount 条待审评论" else "当前没有待审评论"
        )
    }

    override fun mapElement(element: WallEntry): ItemStack {
        val template = itemsMap['W'] ?: return ItemStack(Material.PAPER)
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        val ownerName = resolvedOwnerName()
        val replacements = mapOf(
            "%author%" to authorName,
            "%author_uid%" to element.authorUid,
            "%owner%" to ownerName,
            "%time%" to dateFormat.format(Date(element.timestamp)),
            "%wall_id%" to element.id.toString(),
            "%like_count%" to element.likeCount.toString(),
            "%comment_count%" to element.commentCount.toString(),
            "%pending_comment_count%" to element.pendingCommentCount.toString(),
            "%pin_state%" to if (element.pinned) "已置顶" else "普通留言",
            "%visibility%" to element.visibility.displayName,
            "%approve_state%" to if (element.approved) "已通过" else "待审核",
            "%like_state%" to if (element.id in likedWallIds) "已点赞" else "未点赞",
            "%unread_state%" to if (element.id in unreadWallIds) "未读更新" else "已查看"
        ) + actionLoreReplacements(element)
        val item = template.render(player, replacements).clone()
        val meta = item.itemMeta ?: return item
        if (meta.hasLore()) {
            meta.lore = meta.lore
                ?.map { line -> line.replace("%content%", GuiTextFormatter.renderUserText(element.content)) }
                ?.filterNot { it.contains(HIDDEN_LINE) }
        }
        item.itemMeta = meta
        return GuiHeads.applyForUid(item, element.authorUid, player)
    }

    override fun onElementClick(element: WallEntry, clickType: CyuClickType) {
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        when (clickType) {
            CyuClickType.LEFT -> {
                player.performCommand(if (element.id in likedWallIds) "wall unlike ${element.id}" else "wall like ${element.id}")
                return
            }
            CyuClickType.RIGHT -> {
                ActionRegistry.execute(player, listOf(ActionNode("wall_reply_input", element.id.toString())))
                return
            }
            CyuClickType.MIDDLE -> {
                if (canReviewReplies(element) && element.pendingCommentCount > 0) {
                    player.performCommand("wall commentpending ${element.id}")
                } else {
                    player.performCommand("wall comments ${element.id}")
                }
                return
            }
            CyuClickType.SHIFT_LEFT -> {
                player.performCommand("friend contact ${element.authorUid} $authorName")
                return
            }
            CyuClickType.DOUBLE_CLICK -> {
                if (canPinWall(element)) {
                    player.performCommand(if (element.pinned) "wall unpin ${element.id}" else "wall pin ${element.id}")
                }
                return
            }
            CyuClickType.SHIFT_RIGHT -> {
                if (canDeleteWall(element)) {
                    player.performCommand("wall delete ${element.id}")
                } else {
                    player.performCommand("wall comments ${element.id}")
                }
                return
            }
            else -> Unit
        }
    }

    private fun canReviewPending(): Boolean {
        val ownerUid = targetUid ?: return false
        return viewerUid == ownerUid || viewerIsAdmin
    }

    private fun canPostToWall(): Boolean {
        val ownerUid = targetUid ?: return false
        val friendModule = module.plugin.moduleManager.getModule<FriendModule>("friend")
        if (friendModule != null && friendModule.blockManager.isBlockedStable(ownerUid, viewerUid)) {
            return false
        }
        if (viewerUid == ownerUid) {
            return module.plugin.config.getBoolean("wallPermissions.allow-self", true)
        }
        val requireFriend = module.plugin.config.getBoolean("wallPermissions.post-requires-friend", true)
        return !requireFriend || friendModule?.friendManager?.isFriendStable(viewerUid, ownerUid) == true
    }

    private fun actionLoreReplacements(element: WallEntry): Map<String, String> {
        val liked = element.id in likedWallIds
        val middleHint = if (canReviewReplies(element) && element.pendingCommentCount > 0) {
            "<white>中键</white> <gray>待审评论</gray>"
        } else {
            "<white>中键</white> <gray>评论区</gray>"
        }
        return mapOf(
            "%wall_pending_comment_mm%" to conditionalLine(canReviewReplies(element), "<gray>待审评论</gray> <white>${element.pendingCommentCount}</white>"),
            "%wall_like_hint_mm%" to "<white>左键</white> <gray>${if (liked) "取消赞" else "点赞"}</gray>",
            "%wall_comment_hint_mm%" to "<white>右键</white> <gray>评论</gray>",
            "%wall_review_hint_mm%" to middleHint,
            "%wall_contact_hint_mm%" to "<white>Shift 左键</white> <gray>联系作者</gray>",
            "%wall_pin_hint_mm%" to conditionalLine(canPinWall(element), "<white>双击</white> <gray>${if (element.pinned) "取消置顶" else "置顶"}</gray>"),
            "%wall_delete_hint_mm%" to conditionalLine(canDeleteWall(element), "<white>Shift 右键</white> <gray>删除</gray>")
        )
    }

    private fun canReviewReplies(element: WallEntry): Boolean {
        return viewerUid == element.ownerUid || viewerIsAdmin
    }

    private fun canPinWall(element: WallEntry): Boolean {
        return viewerUid == element.ownerUid || viewerIsAdmin
    }

    private fun canDeleteWall(element: WallEntry): Boolean {
        return viewerUid == element.ownerUid || viewerUid == element.authorUid || viewerIsAdmin
    }

    private fun conditionalLine(condition: Boolean, line: String): String {
        return if (condition) line else HIDDEN_LINE
    }

    private fun refreshLikedWallIds(entries: List<WallEntry>) {
        likedWallIds = if (entries.isEmpty()) {
            emptySet()
        } else {
            module.manager.getLikedWallIdsSync(viewerUid, entries.map(WallEntry::id))
        }
    }

    private fun refreshUnreadWallIds(entries: List<WallEntry>) {
        val ownerUid = targetUid ?: run {
            unreadWallIds = emptySet()
            return
        }
        unreadWallIds = if (entries.isEmpty()) {
            emptySet()
        } else {
            module.manager.unreadWallIdsSync(ownerUid, viewerUid, entries)
        }
    }

    private fun scheduleSeenMark(entries: List<WallEntry>) {
        val ownerUid = targetUid ?: return
        val latestSeen = entries.maxOfOrNull(WallEntry::timestamp) ?: return
        if (latestSeen <= submittedSeenAt) return
        submittedSeenAt = latestSeen
        CyuConcurrency.scheduler.runAsync(module.plugin) {
            module.manager.markWallSeenSync(ownerUid, viewerUid, entries)
        }
    }

    private fun refreshState(entries: List<WallEntry>) {
        refreshLikedWallIds(entries)
        refreshUnreadWallIds(entries)
        scheduleSeenMark(entries)
    }

    private fun resolvedOwnerName(): String {
        return targetUid?.let { CyuIdHook.getName(it) ?: targetName } ?: targetName
    }
}
