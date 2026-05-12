package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.meta.SkullMeta
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class FriendRemoveConfirmView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val friendName: String,
    title: String
) : CyuView(player, title, pattern, itemsMap) {

    private val dynamicTemplates = mutableMapOf<Int, ItemTemplate>()

    override fun onRender() {
        val targetUid = CyuIdHook.getUidByName(friendName) ?: return
        val rawName = CyuIdHook.getName(targetUid) ?: friendName
        val data = module.friendManager.getFriendData(player.uid, targetUid)
        val displayName = data?.noteName ?: rawName
        val groupName = data?.groupName ?: "未分组"
        val offlinePlayer = CyuIdHook.getOfflinePlayer(targetUid)

        val replacements = mapOf(
            "%friend_name%" to displayName,
            "%raw_name%" to rawName,
            "%group_name%" to groupName,
            "%uid%" to targetUid
        )

        layoutActions.keys.toList().forEach { slot ->
            val template = layoutActions.remove(slot)?.template ?: return@forEach
            dynamicTemplates[slot] = template
            val item = template.render(player, replacements)
            val meta = item.itemMeta ?: return@forEach

            if (meta is SkullMeta && offlinePlayer != null && !template.hasHeadSource()) meta.owningPlayer = offlinePlayer

            item.itemMeta = meta
            setItem(slot, item)
        }
    }

    override fun onDynamicClick(slot: Int, clickType: CyuClickType) {
        val template = dynamicTemplates[slot] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val targetUid = CyuIdHook.getUidByName(friendName) ?: return
        val rawName = CyuIdHook.getName(targetUid) ?: friendName
        val data = module.friendManager.getFriendData(player.uid, targetUid)
        val displayName = data?.noteName ?: rawName
        val groupName = data?.groupName ?: "未分组"
        val processed = nodes.map {
            it.copy(payload = it.payload
                .replace("%friend_name%", displayName)
                .replace("%raw_name%", rawName)
                .replace("%group_name%", groupName)
                .replace("%uid%", targetUid))
        }
        ActionRegistry.execute(player, processed)
    }
}
