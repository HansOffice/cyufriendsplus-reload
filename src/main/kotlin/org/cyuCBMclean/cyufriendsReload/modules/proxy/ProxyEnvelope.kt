package org.cyuCBMclean.cyufriendsReload.modules.proxy

data class ProxyEnvelope(
    val version: Int,
    val messageId: String,
    val correlationId: String?,
    val type: ProxyMessageType,
    val sourceServer: String,
    val targetServer: String?,
    val timestamp: Long,
    val subjectUid: String?,
    val payloadJson: String,
    val signature: String
)
