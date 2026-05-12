package org.cyuCBMclean.cyufriendsReload.modules.proxy

data class RemotePresence(
    val uid: String,
    val name: String,
    val serverId: String,
    val headSource: String?,
    val onlineAt: Long,
    val lastSeenAt: Long
)
