package org.cyuCBMclean.cyufriendsReload.modules.friend

data class FriendRequestEntry(
    val senderUid: String,
    val receiverUid: String,
    val note: String?,
    val createdAt: Long
)
