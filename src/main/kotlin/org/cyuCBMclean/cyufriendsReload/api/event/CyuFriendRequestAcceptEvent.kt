package org.cyuCBMclean.cyufriendsReload.api.event

import org.bukkit.event.HandlerList

class CyuFriendRequestAcceptEvent(
    val senderUid: String,
    val receiverUid: String,
    val timestamp: Long
) : CyuFriendsEvent() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
