package org.cyuCBMclean.cyufriendsReload.api.service

data class RequestSnapshot(
    val senderUid: String,
    val receiverUid: String,
    val note: String?,
    val createdAt: Long
)
