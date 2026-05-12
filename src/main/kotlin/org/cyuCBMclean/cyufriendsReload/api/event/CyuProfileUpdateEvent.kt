package org.cyuCBMclean.cyufriendsReload.api.event

import org.bukkit.event.HandlerList
import org.cyuCBMclean.cyufriendsReload.api.service.ProfileSnapshot

class CyuProfileUpdateEvent(
    val profile: ProfileSnapshot
) : CyuFriendsEvent() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
