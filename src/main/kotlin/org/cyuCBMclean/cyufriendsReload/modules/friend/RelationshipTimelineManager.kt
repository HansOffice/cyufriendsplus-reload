package org.cyuCBMclean.cyufriendsReload.modules.friend

import com.github.benmanes.caffeine.cache.Caffeine
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import java.util.concurrent.TimeUnit

class RelationshipTimelineManager(
    private val plugin: CyufriendsReload,
    private val repository: RelationshipTimelineRepository
) {

    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(20, TimeUnit.MINUTES)
        .build<String, List<RelationshipTimelineEntry>>()

    fun entriesSync(ownerUid: String, friendUid: String, limit: Int = perFriendLimit()): List<RelationshipTimelineEntry> {
        val key = cacheKey(ownerUid, friendUid)
        val cached = cache.getIfPresent(key)
        if (cached != null && cached.size <= limit) {
            return cached.take(limit.coerceAtLeast(1))
        }
        return repository.getEntriesSync(ownerUid, friendUid, limit).also { cache.put(key, it) }
    }

    fun recordInteractionSync(
        firstUid: String,
        secondUid: String,
        actorUid: String,
        type: RelationshipTimelineType,
        rawPreview: String? = null,
        referenceId: Int? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (firstUid == secondUid) return

        val preview = normalizePreview(rawPreview, type)
        val entries = listOf(
            RelationshipTimelineEntry(0, firstUid, secondUid, actorUid, type, preview, referenceId, timestamp),
            RelationshipTimelineEntry(0, secondUid, firstUid, actorUid, type, preview, referenceId, timestamp)
        )
        entries.forEach(repository::addEntrySync)

        val keep = perFriendLimit()
        repository.trimSync(firstUid, secondUid, keep)
        repository.trimSync(secondUid, firstUid, keep)
        invalidate(firstUid, secondUid)
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        repository.updateUid(oldUid, newUid)
        cache.invalidateAll()
    }

    fun invalidate(ownerUid: String, friendUid: String) {
        cache.invalidate(cacheKey(ownerUid, friendUid))
        cache.invalidate(cacheKey(friendUid, ownerUid))
    }

    private fun normalizePreview(rawPreview: String?, type: RelationshipTimelineType): String {
        val normalized = rawPreview
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.trim()
            .orEmpty()
        if (normalized.isBlank()) return type.emptyPreview
        val limit = plugin.config.getInt("timeline.preview-length", 32).coerceIn(8, 160)
        return if (normalized.length <= limit) normalized else normalized.take(limit).trimEnd() + "..."
    }

    private fun perFriendLimit(): Int {
        return plugin.config.getInt("timeline.keep-per-friend", 32).coerceIn(8, 256)
    }

    private fun cacheKey(ownerUid: String, friendUid: String): String {
        return "$ownerUid:$friendUid"
    }
}
