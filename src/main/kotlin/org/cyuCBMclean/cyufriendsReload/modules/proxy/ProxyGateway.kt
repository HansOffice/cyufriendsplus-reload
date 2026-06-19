package org.cyuCBMclean.cyufriendsReload.modules.proxy

import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendTeleportMode
import org.bukkit.entity.Player

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlin.math.abs

class ProxyGateway(
    private val module: ProxyModule
) {

    fun requestSnapshot() {
        send(ProxyMessageType.PRESENCE_SNAPSHOT_REQUEST)
    }

    fun publishJoin(uid: String, name: String, headSource: String? = null) {
        send(ProxyMessageType.PRESENCE_JOIN, subjectUid = uid, payloadJson = JsonPayloads.join(name, headSource))
    }

    fun publishQuit(uid: String, carrier: Player? = null) {
        send(ProxyMessageType.PRESENCE_QUIT, subjectUid = uid, carrier = carrier, immediate = carrier != null)
    }

    fun invalidateRelation(vararg uids: String) {
        invalidate(ProxyMessageType.CACHE_INVALIDATE_RELATION, *uids)
    }

    fun invalidateProfile(vararg uids: String) {
        invalidate(ProxyMessageType.CACHE_INVALIDATE_PROFILE, *uids)
    }

    fun invalidateRequest(vararg uids: String) {
        invalidate(ProxyMessageType.CACHE_INVALIDATE_REQUEST, *uids)
    }

    fun invalidateSettings(vararg uids: String) {
        invalidate(ProxyMessageType.CACHE_INVALIDATE_SETTINGS, *uids)
    }

    fun invalidateMessage(vararg uids: String) {
        invalidate(ProxyMessageType.CACHE_INVALIDATE_MESSAGE, *uids)
    }

    fun invalidateStatus(vararg uids: String) {
        invalidate(ProxyMessageType.CACHE_INVALIDATE_STATUS, *uids)
    }

    fun invalidateWall(vararg uids: String) {
        invalidate(ProxyMessageType.CACHE_INVALIDATE_WALL, *uids)
    }

    fun publishUidChanged(oldUid: String, newUid: String) {
        send(ProxyMessageType.IDENTITY_UID_CHANGED, subjectUid = oldUid, payloadJson = JsonPayloads.uidChanged(newUid))
    }

    fun publishHeadSource(uid: String, headSource: String?): String? {
        return send(
            ProxyMessageType.IDENTITY_HEAD_UPDATED,
            subjectUid = uid,
            payloadJson = JsonPayloads.headUpdated(headSource)
        )
    }

    fun sendDirectMessage(senderUid: String, senderName: String, targetUid: String, content: String): String? {
        return send(
            ProxyMessageType.CHAT_DIRECT_DELIVER,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.directMessage(senderUid, senderName, content)
        )
    }

    fun sendDirectAck(targetServer: String, senderUid: String, correlationId: String): String? {
        return send(
            ProxyMessageType.CHAT_DIRECT_ACK,
            subjectUid = senderUid,
            targetServer = targetServer,
            correlationId = correlationId
        )
    }

    fun sendDirectReject(targetServer: String, senderUid: String, correlationId: String, reason: String): String? {
        return send(
            ProxyMessageType.CHAT_DIRECT_REJECT,
            subjectUid = senderUid,
            targetServer = targetServer,
            payloadJson = JsonPayloads.reject(reason),
            correlationId = correlationId
        )
    }

    fun sendFriendRequestNotify(senderUid: String, senderName: String, targetUid: String, note: String? = null): String? {
        return send(
            ProxyMessageType.FRIEND_REQUEST_NOTIFY,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.friendRequest(senderUid, senderName, note)
        )
    }

    fun sendFriendRequestAccepted(targetUid: String, actorName: String): String? {
        return send(
            ProxyMessageType.FRIEND_REQUEST_ACCEPTED,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.friendRequestResult(actorName)
        )
    }

    fun sendFriendRequestDenied(targetUid: String, actorName: String): String? {
        return send(
            ProxyMessageType.FRIEND_REQUEST_DENIED,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.friendRequestResult(actorName)
        )
    }

    fun sendFriendRequestRevoked(targetUid: String, actorName: String): String? {
        return send(
            ProxyMessageType.FRIEND_REQUEST_REVOKED,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.friendRequestResult(actorName)
        )
    }

    fun sendBirthdayNotify(targetUid: String, playerName: String, daysAhead: Int = 0): String? {
        return send(
            ProxyMessageType.NOTIFY_BIRTHDAY,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.birthdayNotify(playerName, daysAhead)
        )
    }

    fun sendSocialInteractionNotify(targetUid: String, kind: String, actorUid: String, actorName: String, preview: String? = null): String? {
        return send(
            ProxyMessageType.NOTIFY_SOCIAL_INTERACTION,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.socialInteraction(kind, actorUid, actorName, preview)
        )
    }

    fun sendTeleportRequest(requesterUid: String, requesterName: String, targetUid: String): String? {
        return send(
            ProxyMessageType.TELEPORT_REQUEST,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.teleportRequest(requesterUid, requesterName)
        )
    }

    fun sendTeleportPrecheck(requesterUid: String, requesterName: String, targetUid: String): String? {
        return send(
            ProxyMessageType.TELEPORT_PRECHECK,
            subjectUid = targetUid,
            payloadJson = JsonPayloads.teleportRequest(requesterUid, requesterName)
        )
    }

    fun sendTeleportPrecheckResult(
        targetServer: String,
        requesterUid: String,
        correlationId: String,
        status: String,
        targetUid: String? = null,
        targetName: String? = null,
        mode: FriendTeleportMode? = null
    ): String? {
        return send(
            ProxyMessageType.TELEPORT_PRECHECK_RESULT,
            subjectUid = requesterUid,
            targetServer = targetServer,
            correlationId = correlationId,
            payloadJson = JsonPayloads.teleportPrecheckResult(status, targetUid, targetName, mode)
        )
    }

    fun sendTeleportTransfer(requesterUid: String, targetUid: String, targetName: String): String? {
        return send(
            ProxyMessageType.TELEPORT_TRANSFER,
            subjectUid = requesterUid,
            payloadJson = JsonPayloads.teleportExecute(targetUid, targetName)
        )
    }

    fun sendTeleportFail(requesterUid: String, reason: String, actorName: String? = null): String? {
        return send(
            ProxyMessageType.TELEPORT_FAIL,
            subjectUid = requesterUid,
            payloadJson = JsonPayloads.reject(reason, actorName)
        )
    }

    fun sendTeleportExecuteAck(correlationId: String, status: String): String? {
        return send(
            ProxyMessageType.TELEPORT_EXECUTE_ACK,
            correlationId = correlationId,
            payloadJson = JsonPayloads.status(status)
        )
    }

    private fun invalidate(type: ProxyMessageType, vararg uids: String) {
        uids.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { uid -> send(type, subjectUid = uid) }
    }

    private fun send(
        type: ProxyMessageType,
        subjectUid: String? = null,
        targetServer: String? = null,
        payloadJson: String = "{}",
        correlationId: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        carrier: Player? = null,
        immediate: Boolean = false
    ): String? {
        val settings = module.settings ?: run {
            module.recordProxySendFailure("proxy settings 未加载")
            return null
        }
        if (!settings.enabled) {
            module.recordProxySendFailure("proxy 未启用")
            return null
        }
        val messageId = UUID.randomUUID().toString()

        val envelope = ProxyEnvelope(
            version = settings.protocolVersion,
            messageId = messageId,
            correlationId = correlationId,
            type = type,
            sourceServer = settings.serverId,
            targetServer = targetServer,
            timestamp = timestamp,
            subjectUid = subjectUid,
            payloadJson = payloadJson,
            signature = ""
        )
        val canonical = canonical(envelope)
        val signed = envelope.copy(signature = module.signer.sign(canonical))
        val resolvedCarrier = carrier ?: module.localPlayerCarrier() ?: run {
            module.recordProxySendFailure("没有可用的在线玩家承载插件消息")
            return null
        }
        DebugLogger.debug(2) {
            "跨服发包: ${envelopeSummary(signed)} | carrier=${resolvedCarrier.name}"
        }
        module.recordProxySend()
        val payload = encode(signed)
        if (immediate) {
            if (!module.sendPluginMessageNow(resolvedCarrier, payload)) return null
        } else {
            module.sendPluginMessage(resolvedCarrier, payload)
        }
        return messageId
    }

    fun decode(bytes: ByteArray): ProxyEnvelope? {
        val settings = module.settings ?: run {
            module.recordProxyReceiveFailure("proxy settings 未加载")
            return null
        }
        val input = java.io.DataInputStream(bytes.inputStream())
        val envelope = try {
            input.use {
                val version = it.readInt()
                val messageId = it.readUTF()
                val correlationId = if (it.readBoolean()) it.readUTF() else null
                val typeId = it.readUTF()
                val type = ProxyMessageType.fromId(typeId) ?: run {
                    module.recordProxyReceiveFailure("未知消息类型: $typeId")
                    DebugLogger.warning("跨服收包失败：未知消息类型: $typeId")
                    return null
                }
                val sourceServer = it.readUTF()
                val targetServer = if (it.readBoolean()) it.readUTF() else null
                val timestamp = it.readLong()
                val subjectUid = if (it.readBoolean()) it.readUTF() else null
                val payloadJson = it.readUTF()
                val signature = it.readUTF()
                ProxyEnvelope(version, messageId, correlationId, type, sourceServer, targetServer, timestamp, subjectUid, payloadJson, signature)
            }
        } catch (exception: Exception) {
            val reason = "无法解析消息体，原因=${exception.message}"
            module.recordProxyReceiveFailure(reason)
            DebugLogger.warning("跨服收包失败：$reason")
            return null
        }

        if (envelope.version != settings.protocolVersion) {
            val reason = "协议版本不匹配，remote=${envelope.version} local=${settings.protocolVersion}"
            module.recordProxyReceiveFailure(reason)
            DebugLogger.warning("跨服收包失败：$reason")
            return null
        }
        if (!module.signer.verify(canonical(envelope.copy(signature = "")), envelope.signature)) {
            val reason = "签名校验未通过，type=${envelope.type.id} source=${envelope.sourceServer}"
            module.recordProxyReceiveFailure(reason)
            DebugLogger.warning("跨服收包失败：$reason")
            return null
        }
        if (abs(System.currentTimeMillis() - envelope.timestamp) > settings.maxClockSkewSeconds * 1000L) {
            val reason = "消息时间偏差过大，type=${envelope.type.id} source=${envelope.sourceServer}"
            module.recordProxyReceiveFailure(reason)
            DebugLogger.warning("跨服收包失败：$reason")
            return null
        }
        module.recordProxyReceive()
        DebugLogger.debug(2) { "跨服收包成功: ${envelopeSummary(envelope)}" }
        return envelope
    }

    private fun encode(envelope: ProxyEnvelope): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { out ->
            out.writeInt(envelope.version)
            out.writeUTF(envelope.messageId)
            out.writeBoolean(envelope.correlationId != null)
            if (envelope.correlationId != null) out.writeUTF(envelope.correlationId)
            out.writeUTF(envelope.type.id)
            out.writeUTF(envelope.sourceServer)
            out.writeBoolean(envelope.targetServer != null)
            if (envelope.targetServer != null) out.writeUTF(envelope.targetServer)
            out.writeLong(envelope.timestamp)
            out.writeBoolean(envelope.subjectUid != null)
            if (envelope.subjectUid != null) out.writeUTF(envelope.subjectUid)
            out.writeUTF(envelope.payloadJson)
            out.writeUTF(envelope.signature)
        }
        return output.toByteArray()
    }

    private fun canonical(envelope: ProxyEnvelope): String {
        return buildString {
            append(envelope.version).append('\n')
            append(envelope.messageId).append('\n')
            append(envelope.correlationId.orEmpty()).append('\n')
            append(envelope.type.id).append('\n')
            append(envelope.sourceServer).append('\n')
            append(envelope.targetServer.orEmpty()).append('\n')
            append(envelope.timestamp).append('\n')
            append(envelope.subjectUid.orEmpty()).append('\n')
            append(envelope.payloadJson)
        }
    }

    private fun envelopeSummary(envelope: ProxyEnvelope): String {
        return "type=${envelope.type.id}, id=${envelope.messageId}, correlation=${envelope.correlationId ?: "-"}, " +
            "source=${envelope.sourceServer}, target=${envelope.targetServer ?: "*"}, subject=${envelope.subjectUid ?: "-"}"
    }
}
