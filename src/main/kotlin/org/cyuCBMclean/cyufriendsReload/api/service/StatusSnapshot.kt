package org.cyuCBMclean.cyufriendsReload.api.service

data class StatusSnapshot(
    val id: Int,
    val uid: String,
    val content: String,
    val visibility: String,
    val pinned: Boolean,
    val timestamp: Long
)
