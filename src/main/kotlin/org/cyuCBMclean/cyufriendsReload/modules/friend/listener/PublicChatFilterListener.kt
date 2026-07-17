package org.cyuCBMclean.cyufriendsReload.modules.friend.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule

class PublicChatFilterListener(
    private val plugin: CyufriendsReload,
    private val module: FriendModule
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!plugin.config.getBoolean("chatFilter.hide-blocked-public-chat", true)) return

        val senderUid = CyuIdHook.getUid(event.player)
        val bypassPermission = plugin.config.getString("chatFilter.bypass-permission", "cyufriends.bypass.chatfilter")
            ?: "cyufriends.bypass.chatfilter"
        val iterator = event.recipients.iterator()
        while (iterator.hasNext()) {
            val recipient = iterator.next()
            if (recipient.uniqueId == event.player.uniqueId) continue
            if (recipient.hasPermission(bypassPermission)) continue
            val recipientUid = CyuIdHook.getUid(recipient)
            if (module.blockManager.isBlockedStable(recipientUid, senderUid)) {
                iterator.remove()
            }
        }
    }
}
