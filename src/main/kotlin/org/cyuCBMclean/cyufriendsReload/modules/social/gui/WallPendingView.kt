package org.cyuCBMclean.cyufriendsReload.modules.social.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.modules.social.WallEntry
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class WallPendingView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: SocialModule,
    private val targetUid: String,
    private val targetName: String,
    title: String
) : PaginatedView<WallEntry>(player, title, pattern, itemsMap, 'Q', 'P', 'N') {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm")

    @Volatile
    private var loading = false

    @Volatile
    private var loaded = false

    @Volatile
    private var entries: List<WallEntry> = emptyList()

    fun invalidateCache() {
        loaded = false
        entries = emptyList()
    }

    override fun getSource(): List<WallEntry> {
        if (loaded) return entries
        if (!loading) {
            loading = true
            CyuConcurrency.scheduler.runAsync(module.plugin) {
                val loadedEntries = runCatching { runBlocking { module.manager.getPendingWallEntries(targetUid) } }.getOrDefault(emptyList())
                CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                    entries = loadedEntries
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
            "%owner%" to targetName,
            "%pending_count%" to entries.size.toString(),
            "%pending_hint%" to if (entries.isEmpty()) "当前没有待审留言" else "当前还有 ${entries.size} 条待审留言"
        )
    }

    override fun mapElement(element: WallEntry): ItemStack {
        val template = itemsMap['Q'] ?: return ItemStack(Material.PAPER)
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        val replacements = mapOf(
            "%author%" to authorName,
            "%author_uid%" to element.authorUid,
            "%owner%" to targetName,
            "%wall_id%" to element.id.toString(),
            "%time%" to dateFormat.format(Date(element.timestamp)),
            "%visibility%" to element.visibility.displayName,
            "%pending_comment_count%" to element.pendingCommentCount.toString()
        )
        val item = template.render(player, replacements).clone()
        val meta = item.itemMeta ?: return item
        if (meta.hasLore()) {
            meta.lore = meta.lore?.map { line -> line.replace("%content%", GuiTextFormatter.renderUserText(element.content)) }
        }
        item.itemMeta = meta
        return GuiHeads.applyForUid(item, element.authorUid, player)
    }

    override fun onElementClick(element: WallEntry, clickType: CyuClickType) {
        val template = itemsMap['Q'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        val processed = nodes.map { node ->
            node.copy(
                payload = node.payload
                    .replace("%author%", authorName)
                    .replace("%author_uid%", element.authorUid)
                    .replace("%owner%", targetName)
                    .replace("%wall_id%", element.id.toString())
                    .replace("%visibility%", element.visibility.displayName)
                    .replace("%pending_comment_count%", element.pendingCommentCount.toString())
            )
        }
        ActionRegistry.execute(player, processed)
    }
}
