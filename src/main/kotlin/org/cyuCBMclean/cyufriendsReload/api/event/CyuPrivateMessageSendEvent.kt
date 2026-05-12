package org.cyuCBMclean.cyufriendsReload.api.event

import org.bukkit.event.HandlerList

class CyuPrivateMessageSendEvent(
    val senderUid: String,
    val receiverUid: String,
    val content: String,
    val read: Boolean,
    val timestamp: Long
) : CyuFriendsEvent() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
