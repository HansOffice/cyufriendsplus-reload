package org.cyuCBMclean.cyufriendsReload.modules.friend

import com.github.benmanes.caffeine.cache.Caffeine
import org.bukkit.Bukkit
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendMetaUpdateEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendshipCreateEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendshipRemoveEvent
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class FriendTagSummary(
    val name: String,
    val color: String,
    val count: Int,
    val primaryCount: Int
)

/**
 * 好友关系的缓存层，数据库只在缓存缺口和写入时碰
 */
class FriendManager(private val repository: FriendRepository) {

    private val friendCache = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, ConcurrentHashMap<String, FriendData>>()

    suspend fun loadPlayer(uid: String) {
        val friends = repository.getFriends(uid)
        val concurrentMap = ConcurrentHashMap<String, FriendData>()
        friends.forEach { concurrentMap[it.friendUid] = it }
        friendCache.put(uid, concurrentMap)
        DebugLogger.debug(1) { "好友缓存载入: uid=$uid count=${friends.size} source=db" }
    }

    fun loadPlayerSync(uid: String) {
        val friends = repository.getFriendsSync(uid)
        val concurrentMap = ConcurrentHashMap<String, FriendData>()
        friends.forEach { concurrentMap[it.friendUid] = it }
        friendCache.put(uid, concurrentMap)
        DebugLogger.debug(1) { "好友缓存载入: uid=$uid count=${friends.size} source=db-sync" }
    }

    fun unloadPlayer(uid: String) {
        friendCache.invalidate(uid)
        DebugLogger.debug(2) { "好友缓存已清理: uid=$uid reason=unload" }
    }

    fun isFriend(uid1: String, uid2: String): Boolean {
        val first = friendCache.getIfPresent(uid1)
        if (first != null) return first.containsKey(uid2)

        val second = friendCache.getIfPresent(uid2)
        if (second != null) return second.containsKey(uid1)

        return repository.areFriendsSync(uid1, uid2)
    }

    fun isFriendStable(uid1: String, uid2: String): Boolean {
        return isFriend(uid1, uid2)
    }

    suspend fun isFriendStored(uid1: String, uid2: String): Boolean {
        if (isFriend(uid1, uid2)) return true
        return repository.areFriends(uid1, uid2)
    }

    fun getOnlineFriends(uid: String): Set<String> {
        return friendCache.getIfPresent(uid)?.keys?.toSet() ?: emptySet()
    }

    suspend fun getFriends(uid: String): Set<String> {
        return friendCache.getIfPresent(uid)?.keys?.toSet()
            ?: repository.getFriends(uid).map { it.friendUid }.toSet()
    }

    suspend fun getFriendCount(uid: String): Int {
        return getFriends(uid).size
    }

    fun getFriendCountSync(uid: String): Int {
        return getFriendEntriesStoredSync(uid).size
    }

    fun getFriendEntries(uid: String): List<FriendData> {
        val cached = friendCache.getIfPresent(uid)
        if (cached != null) return sort(cached.values)
        val stored = repository.getFriendsSync(uid)
        if (stored.isNotEmpty()) {
            val concurrentMap = ConcurrentHashMap<String, FriendData>()
            stored.forEach { concurrentMap[it.friendUid] = it }
            friendCache.put(uid, concurrentMap)
            DebugLogger.debug(2) { "好友缓存回填: uid=$uid count=${stored.size} source=db-sync" }
        }
        return sort(stored)
    }

    suspend fun getFriendEntriesStored(uid: String): List<FriendData> {
        val cached = getFriendEntries(uid)
        if (cached.isNotEmpty()) return cached
        return sort(repository.getFriends(uid))
    }

    fun getFriendEntriesStoredSync(uid: String): List<FriendData> {
        val cached = getFriendEntries(uid)
        if (cached.isNotEmpty()) return cached
        return sort(repository.getFriendsSync(uid))
    }

    suspend fun getFriendByIndex(uid: String, index: Int): String? {
        if (index < 0) return null
        return getFriendEntriesStored(uid).getOrNull(index)?.friendUid
    }

    fun getFriendData(userUid: String, friendUid: String): FriendData? {
        friendCache.getIfPresent(userUid)?.get(friendUid)?.let { return it }
        val stored = repository.getFriendSync(userUid, friendUid) ?: return null
        friendCache.getIfPresent(userUid)?.put(friendUid, stored)
            ?: ConcurrentHashMap<String, FriendData>().also {
                it[friendUid] = stored
                friendCache.put(userUid, it)
            }
        DebugLogger.debug(2) { "好友缓存单条回填: owner=$userUid friend=$friendUid source=db-sync" }
        return stored
    }

    suspend fun getFriendDataStored(userUid: String, friendUid: String): FriendData? {
        return getFriendData(userUid, friendUid) ?: repository.getFriend(userUid, friendUid)
    }

    fun getFriendDataStoredSync(userUid: String, friendUid: String): FriendData? {
        return getFriendData(userUid, friendUid) ?: repository.getFriendSync(userUid, friendUid)
    }

    fun getFriendsInGroup(userUid: String, groupName: String): Set<String> {
        return LinkedHashSet(
            getFriendEntries(userUid)
                .filter { it.groupName == groupName }
                .map { it.friendUid }
        )
    }

    fun getGroups(userUid: String): Set<String> {
        return friendCache.getIfPresent(userUid)?.values?.map { it.groupName }?.toSet() ?: emptySet()
    }

    fun getGroupedFriends(userUid: String): Map<String, List<String>> {
        return getFriendEntries(userUid)
            .groupBy { it.groupName }
            .mapValues { entry -> entry.value.map { it.friendUid } }
            .toSortedMap()
    }

    suspend fun getGroupedFriendsStored(userUid: String): Map<String, List<String>> {
        val cached = getGroupedFriends(userUid)
        if (cached.isNotEmpty()) return cached
        return sort(repository.getFriends(userUid))
            .groupBy { it.groupName }
            .mapValues { entry -> entry.value.map { it.friendUid } }
            .toSortedMap()
    }

    suspend fun establishFriendship(uid1: String, uid2: String) {
        val time = System.currentTimeMillis()
        repository.addFriend(uid1, uid2, time)
        friendCache.getIfPresent(uid1)?.put(uid2, FriendData(uid2, groupName = FriendDefaults.DEFAULT_GROUP_NAME, createdAt = time, lastInteractionAt = time))
        friendCache.getIfPresent(uid2)?.put(uid1, FriendData(uid1, groupName = FriendDefaults.DEFAULT_GROUP_NAME, createdAt = time, lastInteractionAt = time))
        Bukkit.getPluginManager().callEvent(CyuFriendshipCreateEvent(uid1, uid2, time))
        DebugLogger.debug(1) { "好友关系已建立: first=$uid1 second=$uid2 createdAt=$time" }
    }

    suspend fun severFriendship(uid1: String, uid2: String) {
        repository.removeFriend(uid1, uid2)
        friendCache.getIfPresent(uid1)?.remove(uid2)
        friendCache.getIfPresent(uid2)?.remove(uid1)
        Bukkit.getPluginManager().callEvent(CyuFriendshipRemoveEvent(uid1, uid2, System.currentTimeMillis()))
        DebugLogger.debug(1) { "好友关系已解除: first=$uid1 second=$uid2" }
    }

    suspend fun setNote(userUid: String, friendUid: String, note: String?) {
        repository.updateNote(userUid, friendUid, note)
        friendCache.getIfPresent(userUid)?.get(friendUid)?.noteName = note
        callMetaUpdate(userUid, friendUid, "note")
        DebugLogger.debug(1) { "好友备注已更新: owner=$userUid friend=$friendUid chars=${note.normalizedLength()}" }
    }

    suspend fun setNoteDetail(userUid: String, friendUid: String, detail: String?) {
        repository.updateNoteDetail(userUid, friendUid, detail)
        friendCache.getIfPresent(userUid)?.get(friendUid)?.noteDetail = detail
        callMetaUpdate(userUid, friendUid, "note_detail")
        DebugLogger.debug(1) { "好友备注详情已更新: owner=$userUid friend=$friendUid chars=${detail.normalizedLength()}" }
    }

    suspend fun setGroup(userUid: String, friendUid: String, group: String) {
        repository.updateGroup(userUid, friendUid, group)
        friendCache.getIfPresent(userUid)?.get(friendUid)?.groupName = group
        callMetaUpdate(userUid, friendUid, "group")
        DebugLogger.debug(1) { "好友分组已更新: owner=$userUid friend=$friendUid chars=${group.normalizedLength()}" }
    }

    suspend fun addTag(userUid: String, friendUid: String, tag: String): Boolean {
        val added = repository.addTag(userUid, friendUid, tag)
        if (!added) {
            DebugLogger.debug(1) { "好友标签添加未生效: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
            return false
        }
        friendCache.getIfPresent(userUid)?.get(friendUid)?.let { data ->
            if (tag !in data.tagNames) {
                data.tagNames.add(tag)
            }
            if (data.tagName.isNullOrBlank()) {
                data.tagName = tag
            }
        }
        callMetaUpdate(userUid, friendUid, "tag")
        DebugLogger.debug(1) { "好友标签已添加: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
        return true
    }

    suspend fun setPrimaryTag(userUid: String, friendUid: String, tag: String): Boolean {
        val updated = repository.setPrimaryTag(userUid, friendUid, tag)
        if (!updated) {
            DebugLogger.debug(1) { "好友主标签设置未生效: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
            return false
        }
        friendCache.getIfPresent(userUid)?.get(friendUid)?.let { data ->
            data.tagName = tag
            data.tagNames.remove(tag)
            data.tagNames.add(0, tag)
        }
        callMetaUpdate(userUid, friendUid, "primary_tag")
        DebugLogger.debug(1) { "好友主标签已设置: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
        return true
    }

    suspend fun setTagColor(userUid: String, friendUid: String, tag: String, color: String): Boolean {
        val updated = repository.setTagColor(userUid, friendUid, tag, color)
        if (!updated) {
            DebugLogger.debug(1) { "好友标签颜色设置未生效: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()} color=$color" }
            return false
        }
        friendCache.getIfPresent(userUid)?.get(friendUid)?.tagColors?.set(tag, color)
        callMetaUpdate(userUid, friendUid, "tag_color")
        DebugLogger.debug(1) { "好友标签颜色已设置: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()} color=$color" }
        return true
    }

    suspend fun clearTagColor(userUid: String, friendUid: String, tag: String): Boolean {
        val updated = repository.clearTagColor(userUid, friendUid, tag)
        if (!updated) {
            DebugLogger.debug(1) { "好友标签颜色清除未生效: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
            return false
        }
        friendCache.getIfPresent(userUid)?.get(friendUid)?.tagColors?.remove(tag)
        callMetaUpdate(userUid, friendUid, "tag_color")
        DebugLogger.debug(1) { "好友标签颜色已清除: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
        return true
    }

    suspend fun removeTag(userUid: String, friendUid: String, tag: String): Boolean {
        val removed = repository.removeTag(userUid, friendUid, tag)
        if (!removed) {
            DebugLogger.debug(1) { "好友标签移除未生效: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
            return false
        }
        friendCache.getIfPresent(userUid)?.get(friendUid)?.let { data ->
            data.tagNames.removeIf { it.equals(tag, ignoreCase = false) }
            data.tagColors.remove(tag)
            data.tagName = data.tagNames.firstOrNull()
        }
        callMetaUpdate(userUid, friendUid, "tag")
        DebugLogger.debug(1) { "好友标签已移除: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
        return true
    }

    suspend fun clearTags(userUid: String, friendUid: String): Boolean {
        val cleared = repository.clearTags(userUid, friendUid)
        friendCache.getIfPresent(userUid)?.get(friendUid)?.let { data ->
            data.tagNames.clear()
            data.tagColors.clear()
            data.tagName = null
        }
        callMetaUpdate(userUid, friendUid, "tag")
        DebugLogger.debug(1) { "好友标签已清空: owner=$userUid friend=$friendUid result=$cleared" }
        return cleared
    }

    suspend fun setTag(userUid: String, friendUid: String, tag: String?) {
        repository.updateTag(userUid, friendUid, tag)
        friendCache.getIfPresent(userUid)?.get(friendUid)?.let { data ->
            data.tagNames.clear()
            data.tagColors.clear()
            if (!tag.isNullOrBlank()) {
                data.tagNames.add(tag)
            }
            data.tagName = tag
        }
        callMetaUpdate(userUid, friendUid, "tag")
        DebugLogger.debug(1) { "好友标签已覆盖: owner=$userUid friend=$friendUid tagChars=${tag.normalizedLength()}" }
    }

    suspend fun getTagsStored(userUid: String, friendUid: String): List<String> {
        val cached = getFriendData(userUid, friendUid)
        if (cached != null) {
            return cached.tagNames.toList()
        }
        return repository.getTags(userUid, friendUid)
    }

    suspend fun getTagColorsStored(userUid: String, friendUid: String): Map<String, String> {
        val cached = getFriendData(userUid, friendUid)
        if (cached != null) {
            return cached.tagColors.toMap()
        }
        return repository.getTagColors(userUid, friendUid)
    }

    fun tagSummaries(userUid: String): List<FriendTagSummary> {
        val counters = linkedMapOf<String, Int>()
        val primaryCounters = linkedMapOf<String, Int>()
        val colors = linkedMapOf<String, String>()
        getFriendEntries(userUid).forEach { data ->
            data.orderedTags().forEach { tag ->
                counters[tag] = (counters[tag] ?: 0) + 1
                colors.putIfAbsent(tag, data.tagColor(tag))
            }
            data.primaryTag()?.let { primary ->
                primaryCounters[primary] = (primaryCounters[primary] ?: 0) + 1
            }
        }
        return counters.entries
            .map { entry ->
                FriendTagSummary(
                    name = entry.key,
                    color = colors[entry.key] ?: FriendDefaults.TAG_COLOR_PALETTE.first(),
                    count = entry.value,
                    primaryCount = primaryCounters[entry.key] ?: 0
                )
            }
            .sortedWith(
                compareByDescending<FriendTagSummary> { it.primaryCount }
                    .thenByDescending { it.count }
                    .thenBy { it.name.lowercase() }
            )
    }

    fun findOwnedTag(userUid: String, input: String): String? {
        val normalized = input.trim()
        if (normalized.isBlank()) return null
        return tagSummaries(userUid).firstOrNull { it.name.equals(normalized, ignoreCase = true) }?.name
    }

    fun recentTagColors(userUid: String, limit: Int = 4): List<String> {
        val ordered = linkedSetOf<String>()
        getFriendEntries(userUid).forEach { data ->
            data.orderedTags().forEach { tag ->
                data.tagColors[tag]?.takeIf { it.isNotBlank() }?.let { color ->
                    if (ordered.size < limit) {
                        ordered.add(color)
                    }
                }
            }
        }
        return ordered.take(limit)
    }

    fun commonTagColors(userUid: String, friendUid: String, limit: Int = 4): List<String> {
        val data = getFriendData(userUid, friendUid) ?: return emptyList()
        return data.orderedTags()
            .mapNotNull { tag -> data.tagColors[tag]?.takeIf { it.isNotBlank() } }
            .distinct()
            .take(limit)
    }

    fun mutualFriendUidsStoredSync(userUid: String, targetUid: String): List<String> {
        val mine = getFriendEntriesStoredSync(userUid).mapTo(linkedSetOf(), FriendData::friendUid)
        if (mine.isEmpty()) return emptyList()
        return getFriendEntriesStoredSync(targetUid)
            .map(FriendData::friendUid)
            .filter { it in mine }
            .distinct()
            .sortedBy { (CyuIdHook.getName(it) ?: it).lowercase() }
    }

    fun mutualFriendCountStoredSync(userUid: String, targetUid: String): Int {
        return mutualFriendUidsStoredSync(userUid, targetUid).size
    }

    fun recommendationsStoredSync(userUid: String, limit: Int = 21): List<FriendRecommendation> {
        return repository.getRecommendationsSync(userUid, limit)
    }

    fun ignoreRecommendationSync(userUid: String, candidateUid: String, expiresAt: Long = 0L) {
        repository.ignoreRecommendationSync(userUid, candidateUid, expiresAt)
        DebugLogger.debug(1) { "推荐好友已忽略: owner=$userUid candidate=$candidateUid expiresAt=$expiresAt" }
    }

    fun clearRecommendationIgnoreSync(userUid: String, candidateUid: String): Boolean {
        return repository.clearRecommendationIgnoreSync(userUid, candidateUid).also { cleared ->
            DebugLogger.debug(1) { "推荐好友忽略已清除: owner=$userUid candidate=$candidateUid result=$cleared" }
        }
    }

    fun filterFriendEntries(userUid: String, tag: String?): List<FriendData> {
        val normalized = tag?.trim()?.takeIf { it.isNotBlank() }
        val filtered = (friendCache.getIfPresent(userUid)?.values ?: emptyList()).filter { data ->
            normalized == null || data.tagNames.any { it.equals(normalized, ignoreCase = true) }
        }
        if (normalized == null) {
            return sort(filtered)
        }
        return filtered.sortedWith(
            compareByDescending<FriendData> { it.pinned }
                .thenByDescending { it.primaryTag()?.equals(normalized, ignoreCase = true) == true }
                .thenByDescending { it.lastInteractionAt }
                .thenBy { (it.noteName ?: CyuIdHook.getName(it.friendUid) ?: it.friendUid).lowercase() }
                .thenBy { it.friendUid.lowercase() }
        )
    }

    suspend fun setPinned(userUid: String, friendUid: String, pinned: Boolean) {
        repository.updatePinned(userUid, friendUid, pinned)
        friendCache.getIfPresent(userUid)?.get(friendUid)?.pinned = pinned
        callMetaUpdate(userUid, friendUid, "pinned")
        DebugLogger.debug(1) { "好友置顶状态已更新: owner=$userUid friend=$friendUid pinned=$pinned" }
    }

    suspend fun touchInteraction(firstUid: String, secondUid: String, timestamp: Long = System.currentTimeMillis()) {
        repository.updateLastInteraction(firstUid, secondUid, timestamp)
        repository.updateLastInteraction(secondUid, firstUid, timestamp)
        friendCache.getIfPresent(firstUid)?.get(secondUid)?.lastInteractionAt = timestamp
        friendCache.getIfPresent(secondUid)?.get(firstUid)?.lastInteractionAt = timestamp
    }

    fun touchInteractionSync(firstUid: String, secondUid: String, timestamp: Long = System.currentTimeMillis()) {
        repository.updateLastInteractionSync(firstUid, secondUid, timestamp)
        repository.updateLastInteractionSync(secondUid, firstUid, timestamp)
        friendCache.getIfPresent(firstUid)?.get(secondUid)?.lastInteractionAt = timestamp
        friendCache.getIfPresent(secondUid)?.get(firstUid)?.lastInteractionAt = timestamp
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        repository.updateUid(oldUid, newUid)
        friendCache.invalidate(oldUid)
        friendCache.invalidate(newUid)
        friendCache.asMap().values.forEach { friends ->
            friends.remove(oldUid)?.let { data ->
                friends[newUid] = data.copy(
                    friendUid = newUid,
                    tagNames = data.tagNames.toMutableList(),
                    tagColors = data.tagColors.toMutableMap()
                )
            }
        }
        DebugLogger.debug(1) { "好友数据 UID 已迁移: oldUid=$oldUid newUid=$newUid" }
    }

    fun invalidate(uid: String) {
        friendCache.invalidate(uid)
        DebugLogger.debug(2) { "好友缓存已清理: uid=$uid reason=invalidate" }
    }

    fun cachedPlayerCount(): Int = friendCache.asMap().size

    fun cachedFriendRelationCount(): Int = friendCache.asMap().values.sumOf { it.size }

    private fun sort(entries: Collection<FriendData>): List<FriendData> {
        return entries.sortedWith(
            compareByDescending<FriendData> { it.pinned }
                .thenByDescending { it.lastInteractionAt }
                .thenBy { (it.noteName ?: CyuIdHook.getName(it.friendUid) ?: it.friendUid).lowercase() }
                .thenBy { it.friendUid.lowercase() }
        )
    }

    private fun callMetaUpdate(ownerUid: String, friendUid: String, field: String) {
        Bukkit.getPluginManager().callEvent(CyuFriendMetaUpdateEvent(ownerUid, friendUid, field, System.currentTimeMillis()))
    }

    private fun String?.normalizedLength(): Int {
        return this?.trim()?.length ?: 0
    }
}
