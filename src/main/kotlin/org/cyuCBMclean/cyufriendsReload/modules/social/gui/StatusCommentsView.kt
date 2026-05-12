package org.cyuCBMclean.cyufriendsReload.modules.social.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.modules.social.StatusComment
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class StatusCommentsView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: SocialModule,
    private val statusId: Int,
    private val ownerUid: String,
    private val ownerName: String,
    title: String
) : PaginatedView<StatusComment>(player, title, pattern, itemsMap, 'C', 'P', 'N') {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm")
    @Volatile
    private var loading = false
    private var cachedComments: List<StatusComment> = emptyList()

    override fun getSource(): List<StatusComment> {
        val cached = module.manager.getStatusCommentsSync(statusId, 40)
        cachedComments = cached
        if (cached.isNotEmpty()) return cached
        if (!loading) {
            loading = true
            CyuConcurrency.scheduler.runAsync(module.plugin) {
                val loaded = runCatching { runBlocking { module.manager.getStatusComments(statusId, 40) } }.getOrDefault(emptyList())
                CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                    cachedComments = loaded
                    loading = false
                    onRender()
                }
            }
        }
        return cachedComments
    }

    override fun viewReplacements(): Map<String, String> {
        return mapOf(
            "%status_id%" to statusId.toString(),
            "%owner_name%" to ownerName,
            "%comment_total%" to cachedComments.size.toString()
        )
    }

    override fun mapElement(element: StatusComment): ItemStack {
        val template = itemsMap['C'] ?: return ItemStack(Material.PAPER)
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        val canDelete = canDelete(element)
        val replacements = mapOf(
            "%author%" to authorName,
            "%author_uid%" to element.authorUid,
            "%status_id%" to statusId.toString(),
            "%comment_id%" to element.id.toString(),
            "%time%" to dateFormat.format(Date(element.timestamp)),
            "%comment_action_hint_mm%" to if (canDelete) "<white>右键</white> <gray>删除评论</gray>" else "<white>右键</white> <gray>查看作者资料</gray>"
        )
        val baseItem = template.render(player, replacements).clone()
        val meta = baseItem.itemMeta ?: return baseItem
        if (meta.hasLore()) {
            meta.lore = meta.lore?.map { it.replace("%content%", GuiTextFormatter.renderUserText(element.content)) }
        }
        baseItem.itemMeta = meta
        return GuiHeads.applyForUid(baseItem, element.authorUid, player)
    }

    override fun onElementClick(element: StatusComment, clickType: CyuClickType) {
        val authorName = CyuIdHook.getName(element.authorUid) ?: "未知玩家"
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("friend contact ${element.authorUid} $authorName")
            CyuClickType.RIGHT -> {
                if (canDelete(element)) {
                    player.performCommand("status commentdelete ${element.id}")
                } else {
                    player.performCommand("friend profile $authorName")
                }
            }
            CyuClickType.MIDDLE -> player.performCommand("status view $ownerName")
            else -> {
                val template = itemsMap['C'] ?: return
                val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
                ActionRegistry.execute(player, nodes.map { node ->
                    node.copy(
                        payload = node.payload
                            .replace("%author%", authorName)
                            .replace("%author_uid%", element.authorUid)
                            .replace("%comment_id%", element.id.toString())
                            .replace("%status_id%", statusId.toString())
                    )
                })
            }
        }
    }

    private fun canDelete(comment: StatusComment): Boolean {
        return comment.authorUid == player.uid || ownerUid == player.uid || player.hasPermission("cyufriends.admin")
    }
}
