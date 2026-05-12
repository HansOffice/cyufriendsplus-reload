package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class BlacklistView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    title: String = "Blacklist"
) : PaginatedView<String>(player, title, pattern, itemsMap, 'B', 'P', 'N') {

    override fun getSource(): List<String> {
        return module.blockManager.getBlocks(player.uid).toList()
    }

    override fun mapElement(element: String): ItemStack {
        val template = itemsMap['B'] ?: return ItemStack(Material.PLAYER_HEAD)
        val targetName = CyuIdHook.getName(element) ?: "未知玩家"
        val replacements = mapOf("%target_name%" to targetName, "%target_uid%" to element)
        val baseItem = template.render(player, replacements).clone()
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element, player)
    }

    override fun onElementClick(element: String, clickType: CyuClickType) {
        val template = itemsMap['B'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return

        val targetName = CyuIdHook.getName(element) ?: "未知玩家"

        val processedNodes = nodes.map { node ->
            node.copy(payload = node.payload.replace("%target_name%", targetName))
        }

        ActionRegistry.execute(player, processedNodes)
    }
}