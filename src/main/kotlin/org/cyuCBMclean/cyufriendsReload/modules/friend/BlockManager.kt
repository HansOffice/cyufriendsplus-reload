package org.cyuCBMclean.cyufriendsReload.modules.friend

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BlockManager(private val repository: BlockRepository) {

    private val blockCache = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, MutableSet<String>>()

    suspend fun loadPlayer(uid: String) {
        val blocks = repository.getBlocks(uid)
        blockCache.put(uid, ConcurrentHashMap.newKeySet<String>().apply { addAll(blocks) })
    }

    fun loadPlayerSync(uid: String) {
        val blocks = repository.getBlocksSync(uid)
        blockCache.put(uid, ConcurrentHashMap.newKeySet<String>().apply { addAll(blocks) })
    }

    fun unloadPlayer(uid: String) {
        blockCache.invalidate(uid)
    }

    fun isBlocked(user: String, target: String): Boolean {
        blockCache.getIfPresent(user)?.let { return it.contains(target) }
        val blocked = repository.isBlockedSync(user, target)
        if (blocked) {
            blockCache.put(user, ConcurrentHashMap.newKeySet<String>().apply { add(target) })
        }
        return blocked
    }

    fun isBlockedStable(user: String, target: String): Boolean {
        return isBlocked(user, target)
    }

    fun isBlockedCached(user: String, target: String): Boolean {
        return blockCache.getIfPresent(user)?.contains(target) ?: false
    }

    fun getBlocksCached(user: String): Set<String> {
        return blockCache.getIfPresent(user)?.toSet() ?: emptySet()
    }

    suspend fun isBlockedStored(user: String, target: String): Boolean {
        if (isBlocked(user, target)) return true
        return repository.isBlocked(user, target)
    }

    fun getBlocks(user: String): Set<String> {
        blockCache.getIfPresent(user)?.let { return it.toSet() }
        val stored = repository.getBlocksSync(user)
        blockCache.put(user, ConcurrentHashMap.newKeySet<String>().apply { addAll(stored) })
        return stored
    }

    suspend fun getBlocksStored(user: String): Set<String> {
        return blockCache.getIfPresent(user)?.toSet() ?: repository.getBlocks(user)
    }

    fun getBlocksStoredSync(user: String): Set<String> {
        return blockCache.getIfPresent(user)?.toSet() ?: repository.getBlocksSync(user)
    }

    suspend fun addBlock(user: String, blocked: String) {
        repository.saveBlock(user, blocked)
        blockCache.getIfPresent(user)?.add(blocked)
    }

    suspend fun removeBlock(user: String, blocked: String) {
        repository.deleteBlock(user, blocked)
        blockCache.getIfPresent(user)?.remove(blocked)
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        repository.updateUid(oldUid, newUid)
        blockCache.invalidate(oldUid)
        blockCache.invalidate(newUid)
        blockCache.asMap().values.forEach { blocks ->
            if (blocks.remove(oldUid)) blocks.add(newUid)
        }
    }

    fun invalidate(uid: String) {
        blockCache.invalidate(uid)
    }

    fun cachedPlayerCount(): Int = blockCache.asMap().size

    fun cachedBlockCount(): Int = blockCache.asMap().values.sumOf { it.size }
}
