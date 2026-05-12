package org.cyuCBMclean.cyufriendsReload.modules.group.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.group.GroupModule
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class GroupMoveView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: GroupModule,
    private val friendName: String,
    title: String
) : PaginatedView<String>(player, title, pattern, itemsMap, 'G', 'P', 'N') {

    private val targetUid = CyuIdHook.getUidByName(friendName)

    override fun getSource(): List<String> {
        val groups = module.manager.groupedFriends(player.uid).keys
        return (groups + "未分组").filter { it.isNotBlank() }.distinct().sorted()
    }

    override fun mapElement(element: String): ItemStack {
        val template = itemsMap['G'] ?: return ItemStack(Material.CHEST)
        val count = module.manager.friendsInGroup(player.uid, element).size
        return template.render(player, mapOf("%group_name%" to element, "%group_count%" to count.toString())).clone()
    }

    override fun onElementClick(element: String, clickType: CyuClickType) {
        val uid = targetUid ?: return
        val ownerUid = player.uid
        val rawName = CyuIdHook.getName(uid) ?: friendName
        CyuConcurrency.scheduler.runAsync(module.plugin) {
            runBlocking { module.manager.moveFriend(ownerUid, uid, element) }
            CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                player.sendLang("group-set", mapOf("target" to rawName, "group" to element))
                player.playAudio("success")
                player.performCommand("friend profile $rawName")
            }
        }
    }
}
