package org.cyuCBMclean.cyufriendsReload.integration.listener

import kotlinx.coroutines.runBlocking
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyuidReload.event.PlayerUidChangeEvent

class CyuIdChangeListener(private val plugin: CyufriendsReload) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onUidChange(event: PlayerUidChangeEvent) {
        val oldUid = event.oldUid.toString()
        val newUid = event.newUid.toString()
        CyuIdHook.remapUid(event.playerUuid, event.playerName, oldUid, newUid)

        CyuConcurrency.scheduler.runAsync(plugin) {
            runBlocking {
                plugin.moduleManager.getModule<FriendModule>("friend")?.let {
                    it.friendManager.updateUid(oldUid, newUid)
                    it.requestManager.updateUid(oldUid, newUid)
                    it.blockManager.updateUid(oldUid, newUid)
                    it.preferencesManager.updateUid(oldUid, newUid)
                    it.timelineManager.updateUid(oldUid, newUid)
                }
                plugin.moduleManager.getModule<ChatModule>("chat")?.manager?.updateUid(oldUid, newUid)
                plugin.moduleManager.getModule<ProfileModule>("profile")?.manager?.updateUid(oldUid, newUid)
                plugin.moduleManager.getModule<SocialModule>("social")?.manager?.updateUid(oldUid, newUid)
                plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway?.publishUidChanged(oldUid, newUid)
            }
        }
    }
}
