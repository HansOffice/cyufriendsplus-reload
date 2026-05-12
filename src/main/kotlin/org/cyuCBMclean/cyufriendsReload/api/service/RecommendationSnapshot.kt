package org.cyuCBMclean.cyufriendsReload.api.service

data class RecommendationSnapshot(
    val candidateUid: String,
    val mutualCount: Int,
    val recentInteractionAt: Long
)
