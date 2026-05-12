package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.meta.SkullMeta
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRequestNotes
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class AddFriendView(
    player: Player,
    pattern: GuiPattern,
    itemsMap: Map<Char, ItemTemplate>,
    private val targetName: String,
    title: String = "Add Friend"
) : CyuView(player, title, pattern, itemsMap) {

    private val dynamicTemplates = mutableMapOf<Int, ItemTemplate>()

    override fun onRender() {
        val slots = layoutActions.keys.toList()
        val targetUid = CyuIdHook.getUidByName(targetName)
        val displayName = targetUid?.let { CyuIdHook.getName(it) } ?: targetName
        val targetPlayer = targetUid?.let(CyuIdHook::getOnlinePlayer)
        val offlinePlayer = targetUid?.let { CyuIdHook.getOfflinePlayer(it) }
        val friendModule = CyufriendsReload.instance.moduleManager.getModule<FriendModule>("friend")
        val mutualFriends = if (targetUid != null && friendModule != null) {
            friendModule.friendManager.mutualFriendUidsStoredSync(player.uid, targetUid)
        } else {
            emptyList()
        }
        val replacements = mapOf(
            "%target_name%" to displayName,
            "%target_uid%" to (targetUid ?: "未知"),
            "%request_note_max%" to FriendRequestNotes.maxLength(CyufriendsReload.instance).toString(),
            "%mutual_friend_count%" to mutualFriends.size.toString(),
            "%mutual_friend_preview%" to mutualFriends.take(4).joinToString("、") { CyuIdHook.getName(it) ?: it }.ifBlank { "暂无共同好友" }
        )

        slots.forEach { slot ->
            val template = layoutActions.remove(slot)?.template ?: return@forEach
            dynamicTemplates[slot] = template

            val item = template.render(player, replacements)
            val meta = item.itemMeta ?: return@forEach

            if (meta is SkullMeta && !template.hasHeadSource()) {
                meta.owningPlayer = targetPlayer ?: offlinePlayer
            }

            item.itemMeta = meta
            setItem(slot, item)
        }
    }

    override fun onDynamicClick(slot: Int, clickType: CyuClickType) {
        val template = dynamicTemplates[slot] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val targetUid = CyuIdHook.getUidByName(targetName)
        val displayName = targetUid?.let { CyuIdHook.getName(it) } ?: targetName

        val processedNodes = nodes.map { node ->
            node.copy(payload = node.payload
                .replace("%target_name%", displayName)
                .replace("%target_uid%", targetUid ?: "未知"))
        }

        ActionRegistry.execute(player, processedNodes)
    }
}
