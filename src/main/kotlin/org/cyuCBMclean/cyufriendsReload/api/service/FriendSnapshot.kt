package org.cyuCBMclean.cyufriendsReload.api.service

data class FriendSnapshot(
    val ownerUid: String,
    val friendUid: String,
    val noteName: String?,
    val noteDetail: String?,
    val groupName: String,
    val tagName: String?,
    val tagNames: List<String>,
    val tagColors: Map<String, String>,
    val pinned: Boolean,
    val createdAt: Long,
    val lastInteractionAt: Long
)
