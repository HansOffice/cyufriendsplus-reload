package org.cyuCBMclean.cyufriendsReload.modules.friend

import java.util.concurrent.ConcurrentHashMap

enum class FriendListSortMode(val id: String, val displayName: String) {
    RECENT("recent", "最近互动"),
    ONLINE("online", "在线优先"),
    SERVER("server", "服务器优先"),
    NAME("name", "名称排序");

    fun cycle(): FriendListSortMode = when (this) {
        RECENT -> ONLINE
        ONLINE -> SERVER
        SERVER -> NAME
        NAME -> RECENT
    }

    companion object {
        fun fromId(id: String?): FriendListSortMode? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

data class FriendListState(
    val filterTag: String? = null,
    val keyword: String? = null,
    val sortMode: FriendListSortMode = FriendListSortMode.RECENT
) {
    fun normalized(): FriendListState {
        return copy(
            filterTag = filterTag?.trim()?.takeIf { it.isNotEmpty() },
            keyword = keyword?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

object FriendListStateStore {
    private val states = ConcurrentHashMap<String, FriendListState>()

    fun get(uid: String): FriendListState {
        return states[uid]?.normalized() ?: FriendListState()
    }

    fun update(uid: String, updater: (FriendListState) -> FriendListState): FriendListState {
        val updated = updater(get(uid)).normalized()
        states[uid] = updated
        return updated
    }

    fun clear(uid: String) {
        states.remove(uid)
    }

    fun clearAll() {
        states.clear()
    }
}
