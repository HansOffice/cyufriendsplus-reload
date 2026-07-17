package org.cyuCBMclean.cyufriendsReload.modules.proxy

import java.util.concurrent.ConcurrentHashMap

class MessageDeduplicator(private val ttlMillis: Long) {

    private val seen = ConcurrentHashMap<String, Long>()

    fun mark(messageId: String, now: Long = System.currentTimeMillis()): Boolean {
        cleanup(now)
        return seen.putIfAbsent(messageId, now) == null
    }

    private fun cleanup(now: Long) {
        seen.entries.removeIf { now - it.value > ttlMillis }
    }
}
