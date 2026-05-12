package org.cyuCBMclean.cyufriendsReload.modules.proxy

import java.util.concurrent.ConcurrentHashMap

class RemotePresenceDirectory {

    private val entries = ConcurrentHashMap<String, RemotePresence>()

    fun online(presence: RemotePresence) {
        entries[presence.uid] = presence
    }

    fun offline(uid: String) {
        entries.remove(uid)
    }

    fun updateHead(uid: String, headSource: String?) {
        entries.computeIfPresent(uid) { _, old ->
            old.copy(headSource = headSource, lastSeenAt = System.currentTimeMillis())
        }
    }

    fun moveUid(oldUid: String, newUid: String) {
        val current = entries.remove(oldUid) ?: return
        entries[newUid] = current.copy(uid = newUid, lastSeenAt = System.currentTimeMillis())
    }

    fun find(uid: String): RemotePresence? = entries[uid]

    fun all(): List<RemotePresence> = entries.values.sortedBy { it.uid }

    fun count(): Int = entries.size

    fun countByServer(): Map<String, Int> {
        return entries.values
            .groupingBy { it.serverId }
            .eachCount()
            .toSortedMap()
    }

    fun replaceAll(values: List<RemotePresence>) {
        entries.clear()
        values.forEach { entries[it.uid] = it }
    }
}
