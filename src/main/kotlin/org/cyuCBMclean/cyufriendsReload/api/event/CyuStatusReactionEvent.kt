package org.cyuCBMclean.cyufriendsReload.api.event

import org.bukkit.event.HandlerList

class CyuStatusReactionEvent(
    val statusId: Int,
    val ownerUid: String,
    val actorUid: String,
    val liked: Boolean,
    val timestamp: Long
) : CyuFriendsEvent() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
