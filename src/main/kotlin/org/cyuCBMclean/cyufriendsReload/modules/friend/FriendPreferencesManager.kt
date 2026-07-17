package org.cyuCBMclean.cyufriendsReload.modules.friend

import com.github.benmanes.caffeine.cache.Caffeine
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialInteractionNoticeType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FriendPreferencesManager(
    private val repository: FriendPreferencesRepository,
    private val friendManager: FriendManager
) {

    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, FriendPreferences>()
    private val personalCache = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, ConcurrentHashMap<String, FriendPersonalPreferences>>()
    private val groupCache = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, ConcurrentHashMap<String, FriendGroupPreferences>>()

    suspend fun loadPlayer(uid: String) {
        cache.put(uid, repository.find(uid) ?: FriendPreferences(uid))
        personalCache.put(uid, ConcurrentHashMap(repository.findPersonalByOwner(uid)))
        groupCache.put(uid, ConcurrentHashMap(repository.findGroupsByOwner(uid)))
    }

    fun loadPlayerSync(uid: String) {
        cache.put(uid, repository.findSync(uid) ?: FriendPreferences(uid))
        personalCache.put(uid, ConcurrentHashMap(repository.findPersonalByOwnerSync(uid)))
        groupCache.put(uid, ConcurrentHashMap(repository.findGroupsByOwnerSync(uid)))
    }

    fun unloadPlayer(uid: String) {
        cache.invalidate(uid)
        personalCache.invalidate(uid)
        groupCache.invalidate(uid)
    }

    fun snapshotCached(uid: String): FriendPreferences {
        return cache.getIfPresent(uid) ?: FriendPreferences(uid)
    }

    fun snapshotPersonalCached(ownerUid: String, friendUid: String): FriendPersonalPreferences {
        return personalCache.getIfPresent(ownerUid)?.get(friendUid)
            ?: FriendPersonalPreferences(ownerUid, friendUid)
    }

    fun snapshotGroupCached(ownerUid: String, groupName: String): FriendGroupPreferences {
        return groupCache.getIfPresent(ownerUid)?.get(normalizeGroup(groupName))
            ?: FriendGroupPreferences(ownerUid, normalizeGroup(groupName))
    }

    fun isGroupPinnedCached(ownerUid: String, groupName: String): Boolean {
        return snapshotGroupCached(ownerUid, groupName).pinned
    }

    fun lastOnlineCached(uid: String): Long {
        return cache.getIfPresent(uid)?.lastOnline ?: 0L
    }

    fun canReceiveTeleportCached(uid: String): Boolean {
        return teleportModeCached(uid) != FriendTeleportMode.DENY
    }

    fun canReceiveTeleportCached(receiverUid: String, requesterUid: String): Boolean {
        return resolveTeleportModeCached(receiverUid, requesterUid) != FriendTeleportMode.DENY
    }

    fun teleportModeCached(uid: String): FriendTeleportMode {
        return cache.getIfPresent(uid)?.teleportMode ?: FriendTeleportMode.DIRECT
    }

    fun resolveTeleportModeCached(receiverUid: String, requesterUid: String): FriendTeleportMode {
        val personal = snapshotPersonal(receiverUid, requesterUid).teleport
        if (personal != FriendPersonalState.DEFAULT) {
            return personal.resolve(teleportModeCached(receiverUid))
        }
        val group = snapshotGroupForFriend(receiverUid, requesterUid).teleport
        return group.resolve(teleportModeCached(receiverUid))
    }

    fun snapshot(uid: String): FriendPreferences {
        return cache.getIfPresent(uid)
            ?: (repository.findSync(uid) ?: FriendPreferences(uid)).also { cache.put(uid, it) }
    }

    fun snapshotStoredSync(uid: String): FriendPreferences {
        return cache.getIfPresent(uid)
            ?: (repository.findSync(uid) ?: FriendPreferences(uid)).also { cache.put(uid, it) }
    }

    fun teleportModeStoredSync(uid: String): FriendTeleportMode {
        return snapshotStoredSync(uid).teleportMode
    }

    fun resolveTeleportModeStoredSync(receiverUid: String, requesterUid: String): FriendTeleportMode {
        val personal = snapshotPersonalStoredSync(receiverUid, requesterUid).teleport
        if (personal != FriendPersonalState.DEFAULT) {
            return personal.resolve(teleportModeStoredSync(receiverUid))
        }
        val group = snapshotGroupForFriendStoredSync(receiverUid, requesterUid).teleport
        return group.resolve(teleportModeStoredSync(receiverUid))
    }

    fun snapshotPersonal(ownerUid: String, friendUid: String): FriendPersonalPreferences {
        personalCache.getIfPresent(ownerUid)?.get(friendUid)?.let { return it }
        val personal = repository.findPersonalSync(ownerUid, friendUid) ?: FriendPersonalPreferences(ownerUid, friendUid)
        personalCache.getIfPresent(ownerUid)?.put(friendUid, personal)
            ?: ConcurrentHashMap<String, FriendPersonalPreferences>().also {
                it[friendUid] = personal
                personalCache.put(ownerUid, it)
            }
        return personal
    }

    fun snapshotPersonalStoredSync(ownerUid: String, friendUid: String): FriendPersonalPreferences {
        personalCache.getIfPresent(ownerUid)?.get(friendUid)?.let { return it }
        val personal = repository.findPersonalSync(ownerUid, friendUid) ?: FriendPersonalPreferences(ownerUid, friendUid)
        personalCache.getIfPresent(ownerUid)?.put(friendUid, personal)
            ?: ConcurrentHashMap<String, FriendPersonalPreferences>().also {
                it[friendUid] = personal
                personalCache.put(ownerUid, it)
            }
        return personal
    }

    suspend fun personalPreferences(ownerUid: String, friendUid: String): FriendPersonalPreferences {
        return personal(ownerUid, friendUid)
    }

    fun snapshotGroup(ownerUid: String, groupName: String): FriendGroupPreferences {
        groupCache.getIfPresent(ownerUid)?.get(groupName)?.let { return it }
        val group = repository.findGroupSync(ownerUid, groupName) ?: FriendGroupPreferences(ownerUid, groupName)
        groupMapSync(ownerUid)[groupName] = group
        return group
    }

    fun snapshotGroupStoredSync(ownerUid: String, groupName: String): FriendGroupPreferences {
        groupCache.getIfPresent(ownerUid)?.get(groupName)?.let { return it }
        val group = repository.findGroupSync(ownerUid, groupName) ?: FriendGroupPreferences(ownerUid, groupName)
        groupMapSync(ownerUid)[groupName] = group
        return group
    }

    fun snapshotGroupForFriend(ownerUid: String, friendUid: String): FriendGroupPreferences {
        val groupName = friendManager.getFriendData(ownerUid, friendUid)?.groupName ?: FriendDefaults.DEFAULT_GROUP_NAME
        return snapshotGroup(ownerUid, groupName)
    }

    fun snapshotGroupForFriendStoredSync(ownerUid: String, friendUid: String): FriendGroupPreferences {
        val groupName = friendManager.getFriendDataStoredSync(ownerUid, friendUid)?.groupName ?: FriendDefaults.DEFAULT_GROUP_NAME
        return snapshotGroupStoredSync(ownerUid, groupName)
    }

    suspend fun canReceiveJoinNotice(uid: String): Boolean {
        return preferences(uid).notifyOnJoin
    }

    fun canReceiveJoinNoticeStoredSync(uid: String): Boolean {
        return snapshotStoredSync(uid).notifyOnJoin
    }

    suspend fun canReceiveJoinNoticeFrom(receiverUid: String, friendUid: String): Boolean {
        return when (personal(receiverUid, friendUid).notifyReceive) {
            FriendPersonalState.ALLOW -> true
            FriendPersonalState.DENY -> false
            FriendPersonalState.DEFAULT,
            FriendPersonalState.CONFIRM -> when (group(receiverUid, resolveFriendGroup(receiverUid, friendUid)).notifyReceive) {
                FriendPersonalState.ALLOW -> true
                FriendPersonalState.DENY -> false
                FriendPersonalState.DEFAULT,
                FriendPersonalState.CONFIRM -> canReceiveJoinNotice(receiverUid)
            }
        }
    }

    fun canReceiveJoinNoticeFromStoredSync(receiverUid: String, friendUid: String): Boolean {
        return when (snapshotPersonalStoredSync(receiverUid, friendUid).notifyReceive) {
            FriendPersonalState.ALLOW -> true
            FriendPersonalState.DENY -> false
            FriendPersonalState.DEFAULT,
            FriendPersonalState.CONFIRM -> when (snapshotGroupForFriendStoredSync(receiverUid, friendUid).notifyReceive) {
                FriendPersonalState.ALLOW -> true
                FriendPersonalState.DENY -> false
                FriendPersonalState.DEFAULT,
                FriendPersonalState.CONFIRM -> canReceiveJoinNoticeStoredSync(receiverUid)
            }
        }
    }

    suspend fun canReceiveTeleport(uid: String): Boolean {
        return teleportMode(uid) != FriendTeleportMode.DENY
    }

    fun canReceiveTeleportStoredSync(uid: String): Boolean {
        return teleportModeStoredSync(uid) != FriendTeleportMode.DENY
    }

    suspend fun resolveTeleportMode(receiverUid: String, requesterUid: String): FriendTeleportMode {
        val global = teleportMode(receiverUid)
        val personal = personal(receiverUid, requesterUid).teleport
        if (personal != FriendPersonalState.DEFAULT) {
            return personal.resolve(global)
        }
        return group(receiverUid, resolveFriendGroup(receiverUid, requesterUid)).teleport.resolve(global)
    }

    suspend fun canReceiveTeleportFrom(receiverUid: String, requesterUid: String): Boolean {
        return resolveTeleportMode(receiverUid, requesterUid) != FriendTeleportMode.DENY
    }

    fun canReceiveTeleportFromStoredSync(receiverUid: String, requesterUid: String): Boolean {
        return resolveTeleportModeStoredSync(receiverUid, requesterUid) != FriendTeleportMode.DENY
    }

    suspend fun lastOnline(uid: String): Long {
        return preferences(uid).lastOnline
    }

    fun lastOnlineSync(uid: String): Long {
        return snapshotStoredSync(uid).lastOnline
    }

    suspend fun canBroadcastJoinNotice(uid: String): Boolean {
        return preferences(uid).notifyOwnFriends
    }

    fun canBroadcastJoinNoticeStoredSync(uid: String): Boolean {
        return snapshotStoredSync(uid).notifyOwnFriends
    }

    suspend fun canBroadcastJoinNoticeTo(ownerUid: String, friendUid: String): Boolean {
        return when (personal(ownerUid, friendUid).notifyBroadcast) {
            FriendPersonalState.ALLOW -> true
            FriendPersonalState.DENY -> false
            FriendPersonalState.DEFAULT,
            FriendPersonalState.CONFIRM -> when (group(ownerUid, resolveFriendGroup(ownerUid, friendUid)).notifyBroadcast) {
                FriendPersonalState.ALLOW -> true
                FriendPersonalState.DENY -> false
                FriendPersonalState.DEFAULT,
                FriendPersonalState.CONFIRM -> canBroadcastJoinNotice(ownerUid)
            }
        }
    }

    fun canBroadcastJoinNoticeToStoredSync(ownerUid: String, friendUid: String): Boolean {
        return when (snapshotPersonalStoredSync(ownerUid, friendUid).notifyBroadcast) {
            FriendPersonalState.ALLOW -> true
            FriendPersonalState.DENY -> false
            FriendPersonalState.DEFAULT,
            FriendPersonalState.CONFIRM -> when (snapshotGroupForFriendStoredSync(ownerUid, friendUid).notifyBroadcast) {
                FriendPersonalState.ALLOW -> true
                FriendPersonalState.DENY -> false
                FriendPersonalState.DEFAULT,
                FriendPersonalState.CONFIRM -> canBroadcastJoinNoticeStoredSync(ownerUid)
            }
        }
    }

    fun canReceiveSocialNoticeFromStoredSync(ownerUid: String, friendUid: String, type: SocialInteractionNoticeType, globalEnabled: Boolean): Boolean {
        if (!globalEnabled) return false
        return when (socialState(snapshotPersonalStoredSync(ownerUid, friendUid), type)) {
            FriendPersonalState.ALLOW -> true
            FriendPersonalState.DENY -> false
            FriendPersonalState.DEFAULT,
            FriendPersonalState.CONFIRM -> globalEnabled
        }
    }

    suspend fun toggleNotifyOnJoin(uid: String): Boolean {
        return update(uid) { it.copy(notifyOnJoin = !it.notifyOnJoin) }.notifyOnJoin
    }

    suspend fun toggleNotifyOwnFriends(uid: String): Boolean {
        return update(uid) { it.copy(notifyOwnFriends = !it.notifyOwnFriends) }.notifyOwnFriends
    }

    suspend fun cycleTeleportMode(uid: String): FriendTeleportMode {
        return update(uid) { it.copy(teleportMode = it.teleportMode.next()) }.teleportMode
    }

    suspend fun recordLastOnline(uid: String, timestamp: Long) {
        update(uid) { it.copy(lastOnline = timestamp) }
    }

    suspend fun togglePersonal(ownerUid: String, friendUid: String, type: FriendPersonalType): FriendPersonalPreferences {
        val current = personal(ownerUid, friendUid)
        val updated = when (type) {
            FriendPersonalType.TELEPORT -> current.copy(teleport = current.teleport.next(type))
            FriendPersonalType.NOTIFY_RECEIVE -> current.copy(notifyReceive = current.notifyReceive.next(type))
            FriendPersonalType.NOTIFY_BROADCAST -> current.copy(notifyBroadcast = current.notifyBroadcast.next(type))
            FriendPersonalType.STATUS_LIKE_NOTICE -> current.copy(statusLikeNotice = current.statusLikeNotice.next(type))
            FriendPersonalType.STATUS_COMMENT_NOTICE -> current.copy(statusCommentNotice = current.statusCommentNotice.next(type))
            FriendPersonalType.WALL_POST_NOTICE -> current.copy(wallPostNotice = current.wallPostNotice.next(type))
            FriendPersonalType.WALL_LIKE_NOTICE -> current.copy(wallLikeNotice = current.wallLikeNotice.next(type))
            FriendPersonalType.WALL_COMMENT_NOTICE -> current.copy(wallCommentNotice = current.wallCommentNotice.next(type))
        }
        repository.savePersonal(updated)
        personalMap(ownerUid)[friendUid] = updated
        return updated
    }

    suspend fun toggleGroup(ownerUid: String, groupName: String, type: FriendPersonalType): FriendGroupPreferences {
        val normalizedGroup = normalizeGroup(groupName)
        val current = group(ownerUid, normalizedGroup)
        val updated = when (type) {
            FriendPersonalType.TELEPORT -> current.copy(teleport = current.teleport.next(type))
            FriendPersonalType.NOTIFY_RECEIVE -> current.copy(notifyReceive = current.notifyReceive.next(type))
            FriendPersonalType.NOTIFY_BROADCAST -> current.copy(notifyBroadcast = current.notifyBroadcast.next(type))
            FriendPersonalType.STATUS_LIKE_NOTICE,
            FriendPersonalType.STATUS_COMMENT_NOTICE,
            FriendPersonalType.WALL_POST_NOTICE,
            FriendPersonalType.WALL_LIKE_NOTICE,
            FriendPersonalType.WALL_COMMENT_NOTICE -> current
        }
        repository.saveGroup(updated)
        groupMap(ownerUid)[normalizedGroup] = updated
        return updated
    }

    suspend fun setGroup(ownerUid: String, groupName: String, type: FriendPersonalType, state: FriendPersonalState): FriendGroupPreferences {
        val normalizedGroup = normalizeGroup(groupName)
        val current = group(ownerUid, normalizedGroup)
        val updated = when (type) {
            FriendPersonalType.TELEPORT -> current.copy(teleport = state)
            FriendPersonalType.NOTIFY_RECEIVE -> current.copy(notifyReceive = state)
            FriendPersonalType.NOTIFY_BROADCAST -> current.copy(notifyBroadcast = state)
            FriendPersonalType.STATUS_LIKE_NOTICE,
            FriendPersonalType.STATUS_COMMENT_NOTICE,
            FriendPersonalType.WALL_POST_NOTICE,
            FriendPersonalType.WALL_LIKE_NOTICE,
            FriendPersonalType.WALL_COMMENT_NOTICE -> current
        }
        repository.saveGroup(updated)
        groupMap(ownerUid)[normalizedGroup] = updated
        return updated
    }

    suspend fun toggleGroupPinned(ownerUid: String, groupName: String): FriendGroupPreferences {
        val normalizedGroup = normalizeGroup(groupName)
        val updated = group(ownerUid, normalizedGroup).copy(pinned = !group(ownerUid, normalizedGroup).pinned)
        repository.saveGroup(updated)
        groupMap(ownerUid)[normalizedGroup] = updated
        return updated
    }

    suspend fun setGroupPinned(ownerUid: String, groupName: String, pinned: Boolean): FriendGroupPreferences {
        val normalizedGroup = normalizeGroup(groupName)
        val updated = group(ownerUid, normalizedGroup).copy(pinned = pinned)
        repository.saveGroup(updated)
        groupMap(ownerUid)[normalizedGroup] = updated
        return updated
    }

    fun isGroupPinnedStoredSync(ownerUid: String, groupName: String): Boolean {
        return snapshotGroupStoredSync(ownerUid, normalizeGroup(groupName)).pinned
    }

    suspend fun clearPersonalBetween(uid1: String, uid2: String) {
        repository.deletePersonal(uid1, uid2)
        repository.deletePersonal(uid2, uid1)
        personalCache.getIfPresent(uid1)?.remove(uid2)
        personalCache.getIfPresent(uid2)?.remove(uid1)
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        repository.updateUid(oldUid, newUid)
        repository.updatePersonalUid(oldUid, newUid)
        cache.invalidate(oldUid)
        cache.invalidate(newUid)
        personalCache.invalidate(oldUid)
        personalCache.invalidate(newUid)
        groupCache.invalidate(oldUid)
        groupCache.invalidate(newUid)
        personalCache.asMap().values.forEach { settings ->
            settings.remove(oldUid)?.let { setting ->
                settings[newUid] = setting.copy(friendUid = newUid)
            }
        }
    }

    fun invalidate(uid: String) {
        cache.invalidate(uid)
        personalCache.invalidate(uid)
        groupCache.invalidate(uid)
    }

    fun cachedPreferenceCount(): Int = cache.asMap().size

    fun cachedPersonalOwnerCount(): Int = personalCache.asMap().size

    fun cachedPersonalRelationCount(): Int = personalCache.asMap().values.sumOf { it.size }

    fun cachedGroupOwnerCount(): Int = groupCache.asMap().size

    fun cachedGroupRuleCount(): Int = groupCache.asMap().values.sumOf { it.size }

    private suspend fun update(uid: String, updater: (FriendPreferences) -> FriendPreferences): FriendPreferences {
        val updated = updater(preferences(uid))
        repository.save(updated)
        cache.put(uid, updated)
        return updated
    }

    private suspend fun preferences(uid: String): FriendPreferences {
        return cache.getIfPresent(uid) ?: (repository.find(uid) ?: FriendPreferences(uid)).also { cache.put(uid, it) }
    }

    private suspend fun teleportMode(uid: String): FriendTeleportMode {
        return preferences(uid).teleportMode
    }

    private suspend fun personal(ownerUid: String, friendUid: String): FriendPersonalPreferences {
        return personalMap(ownerUid)[friendUid]
            ?: (repository.findPersonal(ownerUid, friendUid) ?: FriendPersonalPreferences(ownerUid, friendUid)).also {
                personalMap(ownerUid)[friendUid] = it
            }
    }

    private suspend fun personalMap(ownerUid: String): ConcurrentHashMap<String, FriendPersonalPreferences> {
        return personalCache.getIfPresent(ownerUid)
            ?: ConcurrentHashMap(repository.findPersonalByOwner(ownerUid)).also { personalCache.put(ownerUid, it) }
    }

    private suspend fun group(ownerUid: String, groupName: String): FriendGroupPreferences {
        val normalizedGroup = normalizeGroup(groupName)
        return groupMap(ownerUid)[normalizedGroup]
            ?: (repository.findGroup(ownerUid, normalizedGroup) ?: FriendGroupPreferences(ownerUid, normalizedGroup)).also {
                groupMap(ownerUid)[normalizedGroup] = it
            }
    }

    private suspend fun groupMap(ownerUid: String): ConcurrentHashMap<String, FriendGroupPreferences> {
        return groupCache.getIfPresent(ownerUid)
            ?: ConcurrentHashMap(repository.findGroupsByOwner(ownerUid)).also { groupCache.put(ownerUid, it) }
    }

    private fun groupMapSync(ownerUid: String): ConcurrentHashMap<String, FriendGroupPreferences> {
        return groupCache.getIfPresent(ownerUid)
            ?: ConcurrentHashMap(repository.findGroupsByOwnerSync(ownerUid)).also { groupCache.put(ownerUid, it) }
    }

    private fun resolveFriendGroup(ownerUid: String, friendUid: String): String {
        return friendManager.getFriendDataStoredSync(ownerUid, friendUid)?.groupName ?: FriendDefaults.DEFAULT_GROUP_NAME
    }

    private fun normalizeGroup(groupName: String): String {
        val trimmed = groupName.trim()
        return if (trimmed.isBlank()) FriendDefaults.DEFAULT_GROUP_NAME else trimmed
    }

    private fun socialState(preferences: FriendPersonalPreferences, type: SocialInteractionNoticeType): FriendPersonalState {
        return when (type) {
            SocialInteractionNoticeType.STATUS_LIKE -> preferences.statusLikeNotice
            SocialInteractionNoticeType.STATUS_COMMENT -> preferences.statusCommentNotice
            SocialInteractionNoticeType.WALL_POST -> preferences.wallPostNotice
            SocialInteractionNoticeType.WALL_LIKE -> preferences.wallLikeNotice
            SocialInteractionNoticeType.WALL_COMMENT -> preferences.wallCommentNotice
        }
    }
}
