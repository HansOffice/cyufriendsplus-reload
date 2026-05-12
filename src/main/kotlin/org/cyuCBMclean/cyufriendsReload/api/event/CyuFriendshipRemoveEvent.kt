package org.cyuCBMclean.cyufriendsReload.api.event

import org.bukkit.event.HandlerList

class CyuFriendshipRemoveEvent(
    val firstUid: String,
    val secondUid: String,
    val removedAt: Long
) : CyuFriendsEvent() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
