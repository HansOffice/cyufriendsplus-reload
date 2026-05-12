package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class OnlinePlayersView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    title: String = "Online Players"
) : PaginatedView<Player>(player, title, pattern, itemsMap, 'O', 'P', 'N') {

    private var cachedPlayers: List<Player> = emptyList()

    override fun getSource(): List<Player> {
        val uid = player.uid
        val friends = module.friendManager.getOnlineFriends(uid)
        val players = Bukkit.getOnlinePlayers().filter {
            it.uniqueId != player.uniqueId && !friends.contains(it.uid)
        }
        cachedPlayers = players
        return players
    }

    override fun viewReplacements(): Map<String, String> {
        return mapOf(
            "%page%" to page.toString(),
            "%total_pages%" to totalPagesFor(cachedPlayers.size).toString(),
            "%entry_count%" to cachedPlayers.size.toString(),
            "%online_count%" to cachedPlayers.size.toString()
        )
    }

    override fun mapElement(element: Player): ItemStack {
        val template = itemsMap['O'] ?: return ItemStack(Material.PLAYER_HEAD)
        val replacements = mapOf("%target_name%" to element.name, "%target_uid%" to element.uid)
        val baseItem = template.render(player, replacements).clone()
        val meta = baseItem.itemMeta ?: return baseItem

        if (meta is SkullMeta && !template.hasHeadSource()) {
            meta.owningPlayer = element
        }

        baseItem.itemMeta = meta
        return baseItem
    }

    override fun onElementClick(element: Player, clickType: CyuClickType) {
        val template = itemsMap['O'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return

        val processedNodes = nodes.map { node ->
            node.copy(payload = node.payload.replace("%target_name%", element.name))
        }

        ActionRegistry.execute(player, processedNodes)
    }
}
