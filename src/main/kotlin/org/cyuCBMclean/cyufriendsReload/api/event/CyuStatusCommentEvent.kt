package org.cyuCBMclean.cyufriendsReload.api.event

import org.bukkit.event.HandlerList

class CyuStatusCommentEvent(
    val statusId: Int,
    val ownerUid: String,
    val authorUid: String,
    val content: String,
    val timestamp: Long
) : CyuFriendsEvent() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
