package org.cyuCBMclean.cyufriendsReload.modules.group.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.group.GroupModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class GroupMembersView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: GroupModule,
    private val groupName: String,
    title: String = "Group Members"
) : PaginatedView<String>(player, title, pattern, itemsMap, 'F', 'P', 'N') {

    override fun getSource(): List<String> {
        return module.manager.friendsInGroup(player.uid, groupName).sorted()
    }

    override fun mapElement(element: String): ItemStack {
        val template = itemsMap['F'] ?: return ItemStack(Material.PLAYER_HEAD)
        val rawName = CyuIdHook.getName(element) ?: "未知玩家"
        val fData = module.manager.friendData(player.uid, element)
        val displayName = fData?.noteName ?: rawName
        val replacements = mapOf(
            "%friend_name%" to displayName,
            "%raw_name%" to rawName,
            "%group_name%" to groupName,
            "%uid%" to element
        )
        val baseItem = template.render(player, replacements).clone()
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element, player)
    }

    override fun onElementClick(element: String, clickType: CyuClickType) {
        val template = itemsMap['F'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val rawName = CyuIdHook.getName(element) ?: "未知玩家"
        val fData = module.manager.friendData(player.uid, element)
        val displayName = fData?.noteName ?: rawName
        val processedNodes = nodes.map { node ->
            node.copy(payload = node.payload
                .replace("%friend_name%", displayName)
                .replace("%raw_name%", rawName)
                .replace("%group_name%", groupName)
                .replace("%uid%", element))
        }
        ActionRegistry.execute(player, processedNodes)
    }
}