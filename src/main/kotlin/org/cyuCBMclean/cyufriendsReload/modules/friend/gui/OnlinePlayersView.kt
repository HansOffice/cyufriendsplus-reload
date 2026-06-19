package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.displayServerName
import org.cyuCBMclean.cyufriendsReload.extension.globalOnlineEntries
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class OnlinePlayersView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    title: String = "Online Players"
) : PaginatedView<OnlinePlayersView.OnlineEntry>(player, title, pattern, itemsMap, 'O', 'P', 'N') {

    private var cachedEntries: List<OnlineEntry> = emptyList()

    override fun getSource(): List<OnlineEntry> {
        val uid = player.uid
        val friendUids = module.friendManager.getFriendEntriesStoredSync(uid)
            .mapTo(linkedSetOf()) { it.friendUid }
        val entries = module.plugin.globalOnlineEntries()
            .asSequence()
            .filter { it.uid != uid && it.uid !in friendUids }
            .map {
                OnlineEntry(
                    uid = it.uid,
                    name = it.name,
                    serverId = it.serverId,
                    remote = it.remote
                )
            }
            .sortedWith(compareBy<OnlineEntry>({ it.remote }, { it.name.lowercase() }))
            .toList()

        cachedEntries = entries
        return entries
    }

    override fun viewReplacements(): Map<String, String> {
        return mapOf(
            "%page%" to page.toString(),
            "%total_pages%" to totalPagesFor(cachedEntries.size).toString(),
            "%entry_count%" to cachedEntries.size.toString(),
            "%online_count%" to cachedEntries.size.toString()
        )
    }

    override fun mapElement(element: OnlineEntry): ItemStack {
        val template = itemsMap['O'] ?: return ItemStack(Material.PLAYER_HEAD)
        val replacements = replacements(element)
        val baseItem = template.render(player, replacements).clone()
        return if (template.hasHeadSource()) {
            baseItem
        } else {
            GuiHeads.applyForUid(baseItem, element.uid, player)
        }
    }

    override fun onElementClick(element: OnlineEntry, clickType: CyuClickType) {
        val template = itemsMap['O'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return

        val replacements = replacements(element)
        val processedNodes = nodes.map { node ->
            replacements.entries.fold(node.payload) { text, (key, value) ->
                text.replace(key, value)
            }.let { node.copy(payload = it) }
        }

        ActionRegistry.execute(player, processedNodes)
    }

    private fun replacements(entry: OnlineEntry): Map<String, String> {
        val serverName = module.plugin.displayServerName(entry.serverId)
        val onlineSource = if (entry.remote) "跨服在线" else "本服在线"
        return mapOf(
            "%target_name%" to entry.name,
            "%target_uid%" to entry.uid,
            "%target_server%" to (entry.serverId ?: "unknown"),
            "%target_server_name%" to serverName,
            "%target_online_source%" to onlineSource
        )
    }

    data class OnlineEntry(
        val uid: String,
        val name: String,
        val serverId: String?,
        val remote: Boolean
    )
}
