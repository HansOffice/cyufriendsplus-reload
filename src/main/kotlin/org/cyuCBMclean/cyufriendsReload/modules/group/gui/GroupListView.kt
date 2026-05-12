package org.cyuCBMclean.cyufriendsReload.modules.group.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.group.GroupModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class GroupListView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: GroupModule,
    title: String = "Groups"
) : PaginatedView<Pair<String, Int>>(player, title, pattern, itemsMap, 'G', 'P', 'N') {

    override fun getSource(): List<Pair<String, Int>> {
        return module.manager.groupedFriends(player.uid)
            .map { it.key to it.value.size }
            .sortedWith(
                compareByDescending<Pair<String, Int>> { isPinned(it.first) }
                    .thenBy<Pair<String, Int>> { it.first == "未分组" }
                    .thenBy { it.first }
            )
    }

    override fun mapElement(element: Pair<String, Int>): ItemStack {
        val template = itemsMap['G'] ?: return ItemStack(Material.CHEST)
        return template.render(
            player,
            mapOf(
                "%group_name%" to element.first,
                "%group_count%" to element.second.toString(),
                "%group_pin_state%" to if (isPinned(element.first)) "置顶显示" else "普通显示"
            )
        ).clone()
    }

    override fun onElementClick(element: Pair<String, Int>, clickType: CyuClickType) {
        val template = itemsMap['G'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val processedNodes = nodes.map { node ->
            node.copy(payload = node.payload
                .replace("%group_name%", element.first)
                .replace("%group_count%", element.second.toString())
                .replace("%group_pin_state%", if (isPinned(element.first)) "置顶显示" else "普通显示"))
        }
        ActionRegistry.execute(player, processedNodes)
    }

    private fun isPinned(groupName: String): Boolean {
        return module.friendModule?.preferencesManager?.isGroupPinnedStoredSync(player.uid, groupName) == true
    }
}
