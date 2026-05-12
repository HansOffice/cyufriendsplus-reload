package org.cyuCBMclean.cyufriendsReload.api.service

data class ChatMessageSnapshot(
    val id: Int,
    val senderUid: String,
    val receiverUid: String,
    val content: String,
    val timestamp: Long
)

data class ConversationSnapshot(
    val partnerUid: String,
    val latestContent: String,
    val latestAt: Long,
    val unreadCount: Int,
    val latestSenderUid: String
)
