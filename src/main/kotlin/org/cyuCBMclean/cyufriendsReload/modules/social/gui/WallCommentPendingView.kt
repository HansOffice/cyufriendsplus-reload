package org.cyuCBMclean.cyufriendsReload.modules.social.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.modules.social.WallComment
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class WallCommentPendingView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: SocialModule,
    private val wallId: Int,
    private val ownerName: String,
    title: String
) : PaginatedView<WallComment>(player, title, pattern, itemsMap, 'R', 'P', 'N') {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm")

    @Volatile
    private var loading = false

    @Volatile
    private var loaded = false

    @Volatile
    private var replies: List<WallComment> = emptyList()

    fun invalidateCache() {
        loaded = false
        replies = emptyList()
    }

    override fun getSource(): List<WallComment> {
        if (loaded) return replies
        if (!loading) {
            loading = true
            CyuConcurrency.scheduler.runAsync(module.plugin) {
                val loadedReplies = runCatching { runBlocking { module.manager.getPendingWallReplies(wallId) } }.getOrDefault(emptyList())
                CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                    replies = loadedReplies
                    loaded = true
                    loading = false
                    onRender()
                }
            }
        }
        return emptyList()
    }

    override fun layoutReplacements(symbol: Char, slot: Int): Map<String, String> {
        return mapOf(
            "%owner%" to ownerName,
            "%wall_id%" to wallId.toString(),
            "%pending_count%" to replies.size.toString(),
            "%pending_hint%" to if (replies.isEmpty()) "当前没有待审评论" else "当前还有 ${replies.size} 条待审评论"
        )
    }

    override fun mapElement(element: WallComment): ItemStack {
        val template = itemsMap['R'] ?: return ItemStack(Material.PAPER)
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        val replacements = mapOf(
            "%author%" to authorName,
            "%author_uid%" to element.authorUid,
            "%owner%" to ownerName,
            "%comment_id%" to element.id.toString(),
            "%wall_id%" to wallId.toString(),
            "%time%" to dateFormat.format(Date(element.timestamp))
        )
        val item = template.render(player, replacements).clone()
        val meta = item.itemMeta ?: return item
        if (meta.hasLore()) {
            meta.lore = meta.lore?.map { line -> line.replace("%content%", GuiTextFormatter.renderUserText(element.content)) }
        }
        item.itemMeta = meta
        return GuiHeads.applyForUid(item, element.authorUid, player)
    }

    override fun onElementClick(element: WallComment, clickType: CyuClickType) {
        val template = itemsMap['R'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        val processed = nodes.map { node ->
            node.copy(
                payload = node.payload
                    .replace("%author%", authorName)
                    .replace("%author_uid%", element.authorUid)
                    .replace("%owner%", ownerName)
                    .replace("%comment_id%", element.id.toString())
                    .replace("%wall_id%", wallId.toString())
            )
        }
        ActionRegistry.execute(player, processed)
    }
}
