package org.cyuCBMclean.cyufriendsReload.ui.input

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TextInputRequest(
    val commandTemplate: String,
    val cancelMessageKey: String,
    val expireAtMillis: Long = Long.MAX_VALUE
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis >= expireAtMillis
    }
}

sealed class TextInputTakeResult {
    data class Active(val request: TextInputRequest) : TextInputTakeResult()
    data class Expired(val request: TextInputRequest) : TextInputTakeResult()
    object None : TextInputTakeResult()
}

object PendingTextInput {
    private val requests = ConcurrentHashMap<UUID, TextInputRequest>()

    fun put(playerId: UUID, request: TextInputRequest) {
        requests[playerId] = request
    }

    fun take(playerId: UUID, nowMillis: Long = System.currentTimeMillis()): TextInputTakeResult {
        val request = requests.remove(playerId) ?: return TextInputTakeResult.None
        return if (request.isExpired(nowMillis)) {
            TextInputTakeResult.Expired(request)
        } else {
            TextInputTakeResult.Active(request)
        }
    }

    fun clear(playerId: UUID) {
        requests.remove(playerId)
    }
}
