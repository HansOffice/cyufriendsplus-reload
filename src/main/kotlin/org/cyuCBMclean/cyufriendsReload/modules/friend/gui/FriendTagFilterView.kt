package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendTagSummary
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class FriendTagFilterView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val currentFilter: String? = null,
    title: String
) : PaginatedView<FriendTagSummary>(player, title, pattern, itemsMap, 'T', 'P', 'N') {

    override fun getSource(): List<FriendTagSummary> {
        return module.friendManager.tagSummaries(player.uid)
    }

    override fun mapElement(element: FriendTagSummary): ItemStack {
        val template = itemsMap['T'] ?: return ItemStack(Material.NAME_TAG)
        return template.render(player, replacements(element))
    }

    override fun onElementClick(element: FriendTagSummary, clickType: CyuClickType) {
        val template = itemsMap['T'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val replacements = replacements(element)
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

    private fun replacements(summary: FriendTagSummary): Map<String, String> {
        val colored = if (summary.color.startsWith("#")) {
            "<color:${summary.color}>${summary.name}</color>"
        } else {
            "<${summary.color}>${summary.name}</${summary.color}>"
        }
        val active = currentFilter?.equals(summary.name, ignoreCase = true) == true
        return mapOf(
            "%tag_name%" to summary.name,
            "%tag_color%" to summary.color,
            "%tag_mm%" to colored,
            "%tag_label_mm%" to if (active) "<aqua><bold>当前 · ${summary.name}</bold></aqua>" else colored,
            "%friend_count%" to summary.count.toString(),
            "%primary_count%" to summary.primaryCount.toString(),
            "%active_state%" to if (active) "当前筛选中" else "未应用",
            "%active_badge_mm%" to if (active) "<aqua><bold>已选中</bold></aqua>" else "<gray>点击应用</gray>"
        )
    }
}
