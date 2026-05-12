package org.cyuCBMclean.cyufriendsReload.api.service

data class ProfileSnapshot(
    val uid: String,
    val bio: String,
    val birthday: String?,
    val allowRequests: Boolean,
    val allowPrivateMsg: Boolean,
    val vanishMode: Boolean,
    val onlineScope: String,
    val serverName: String
)
