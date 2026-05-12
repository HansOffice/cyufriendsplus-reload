package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendDefaults
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class FriendTagManageView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val targetName: String,
    title: String
) : PaginatedView<String>(player, title, pattern, itemsMap, 'T', 'P', 'N') {

    override fun getSource(): List<String> {
        val targetUid = CyuIdHook.getUidByName(targetName) ?: return emptyList()
        return module.friendManager.getFriendData(player.uid, targetUid)?.orderedTags() ?: emptyList()
    }

    override fun mapElement(element: String): ItemStack {
        val template = itemsMap['T'] ?: return ItemStack(Material.NAME_TAG)
        return template.render(player, replacements(element))
    }

    override fun onElementClick(element: String, clickType: CyuClickType) {
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

    private fun replacements(tagName: String): Map<String, String> {
        val targetUid = CyuIdHook.getUidByName(targetName)
        val data = targetUid?.let { module.friendManager.getFriendData(player.uid, it) }
        val isPrimary = data?.primaryTag()?.equals(tagName, ignoreCase = true) == true
        val color = data?.tagColor(tagName) ?: FriendDefaults.TAG_COLOR_PALETTE.first()
        val mm = data?.coloredTag(tagName) ?: "<gray>$tagName</gray>"
        return mapOf(
            "%raw_name%" to targetName,
            "%tag_name%" to tagName,
            "%tag_color%" to color,
            "%tag_mm%" to mm,
            "%primary_state%" to if (isPrimary) "主标签" else "普通标签"
        )
    }
}
