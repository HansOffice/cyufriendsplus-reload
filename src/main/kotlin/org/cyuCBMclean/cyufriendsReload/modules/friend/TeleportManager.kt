package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TeleportRequest(
    val senderUid: String,
    val senderName: String,
    val sourceServer: String?,
    val requestId: String = UUID.randomUUID().toString(),
    val expireAtMillis: Long
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis >= expireAtMillis
    }
}

/**
 * 传送请求只活一小段时间，过期就清掉
 */
class TeleportManager(
    private val plugin: CyufriendsReload
) {

    private val tpRequests = ConcurrentHashMap<String, TeleportRequest>()
    private val timeoutTasks = ConcurrentHashMap<String, CyuTask>()

    fun requestTimeoutSeconds(): Long {
        return plugin.config
            .getLong("settings.tp-request-timeout-seconds", 60L)
            .coerceIn(5L, 600L)
    }

    fun createRequest(senderUid: String, senderName: String, sourceServer: String?): TeleportRequest {
        val timeoutSeconds = requestTimeoutSeconds()
        return TeleportRequest(
            senderUid = senderUid,
            senderName = senderName,
            sourceServer = sourceServer,
            expireAtMillis = System.currentTimeMillis() + timeoutSeconds * 1000L
        ).also { request ->
            DebugLogger.debug(1) {
                "好友传送请求已创建: sender=$senderUid requestId=${request.requestId} source=${sourceServer ?: "local"} timeout=${timeoutSeconds}s"
            }
        }
    }

    fun sendRequest(receiverUid: String, request: TeleportRequest, onExpire: (TeleportRequest) -> Unit = {}): Boolean {
        if (getRequest(receiverUid) != null || tpRequests.putIfAbsent(receiverUid, request) != null) {
            DebugLogger.debug(1) {
                "好友传送请求已拒绝: receiver=$receiverUid sender=${request.senderUid} reason=request-pending"
            }
            return false
        }
        val delayTicks = ((request.expireAtMillis - System.currentTimeMillis()).coerceAtLeast(1000L) + 49L) / 50L
        timeoutTasks[receiverUid] = CyuConcurrency.scheduler.runLaterAsync(plugin, delayTicks) {
            expireRequest(receiverUid, request.requestId, onExpire)
        }
        DebugLogger.debug(1) {
            "好友传送请求已挂起: receiver=$receiverUid sender=${request.senderUid} requestId=${request.requestId} delayTicks=$delayTicks"
        }
        return true
    }

    fun getRequest(receiverUid: String): TeleportRequest? {
        val request = tpRequests[receiverUid] ?: return null
        if (!request.isExpired()) {
            return request
        }
        DebugLogger.debug(1) {
            "好友传送请求已过期读取: receiver=$receiverUid sender=${request.senderUid} requestId=${request.requestId}"
        }
        clearRequest(receiverUid)
        return null
    }

    fun getRequestSender(receiverUid: String): String? {
        return getRequest(receiverUid)?.senderUid
    }

    fun clearRequest(receiverUid: String): TeleportRequest? {
        timeoutTasks.remove(receiverUid)?.cancel()
        return tpRequests.remove(receiverUid).also { removed ->
            if (removed != null) {
                DebugLogger.debug(2) {
                    "好友传送请求已清理: receiver=$receiverUid sender=${removed.senderUid} requestId=${removed.requestId}"
                }
            }
        }
    }

    private fun expireRequest(receiverUid: String, requestId: String, onExpire: (TeleportRequest) -> Unit) {
        val request = tpRequests[receiverUid] ?: return
        if (request.requestId != requestId) return
        if (!tpRequests.remove(receiverUid, request)) return
        timeoutTasks.remove(receiverUid)
        DebugLogger.debug(1) {
            "好友传送请求已超时: receiver=$receiverUid sender=${request.senderUid} requestId=${request.requestId}"
        }
        onExpire(request)
    }
}
