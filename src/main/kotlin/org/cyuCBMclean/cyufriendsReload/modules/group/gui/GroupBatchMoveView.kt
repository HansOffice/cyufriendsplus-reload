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

class GroupBatchMoveView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: GroupModule,
    private val sourceGroup: String,
    title: String
) : PaginatedView<String>(player, title, pattern, itemsMap, 'G', 'P', 'N') {

    private var cachedTargets: List<String> = emptyList()

    override fun getSource(): List<String> {
        val groups = module.manager.groups(player.uid).toMutableSet()
        groups += "未分组"
        val targets = groups
            .map(module.manager::normalize)
            .distinct()
            .filterNot { it.equals(sourceGroup, ignoreCase = false) }
            .sorted()
        cachedTargets = targets
        return targets
    }

    override fun viewReplacements(): Map<String, String> {
        val memberCount = module.manager.friendsInGroup(player.uid, sourceGroup).size
        return mapOf(
            "%source_group%" to sourceGroup,
            "%source_count%" to memberCount.toString(),
            "%target_count%" to cachedTargets.size.toString()
        )
    }

    override fun mapElement(element: String): ItemStack {
        val template = itemsMap['G'] ?: return ItemStack(Material.CHEST)
        return template.render(
            player,
            mapOf(
                "%group_name%" to element,
                "%source_group%" to sourceGroup
            )
        ).clone()
    }

    override fun onElementClick(element: String, clickType: CyuClickType) {
        val template = itemsMap['G'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        ActionRegistry.execute(player, nodes.map { node ->
            node.copy(
                payload = node.payload
                    .replace("%group_name%", element)
                    .replace("%source_group%", sourceGroup)
            )
        })
    }
}
