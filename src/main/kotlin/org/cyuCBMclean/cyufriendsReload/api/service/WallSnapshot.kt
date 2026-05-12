package org.cyuCBMclean.cyufriendsReload.api.service

data class WallSnapshot(
    val id: Int,
    val ownerUid: String,
    val authorUid: String,
    val content: String,
    val visibility: String,
    val approved: Boolean,
    val pinned: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val pendingCommentCount: Int,
    val timestamp: Long
)
