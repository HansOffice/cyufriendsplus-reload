package org.cyuCBMclean.cyufriendsReload.modules.social

import com.github.benmanes.caffeine.cache.Caffeine
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.api.event.CyuStatusCommentEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuStatusPublishEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuStatusReactionEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuWallPostEvent
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class SocialWriteResult {
    SUCCESS,
    EMPTY,
    COOLDOWN
}

enum class SocialDeleteResult {
    SUCCESS,
    NOT_FOUND,
    FORBIDDEN
}

enum class SocialReactionResult {
    SUCCESS,
    NOT_FOUND,
    ALREADY_REACTED,
    NOT_REACTED
}

enum class SocialWallSubmitResult {
    SUCCESS,
    PENDING,
    EMPTY,
    COOLDOWN
}

enum class SocialModerationResult {
    SUCCESS,
    EMPTY,
    NOT_FOUND,
    FORBIDDEN,
    ALREADY_APPROVED
}

/**
 * 动态和留言墙的业务入口，GUI 和命令都别直接碰仓库
 */
class SocialManager(
    private val plugin: CyufriendsReload,
    private val statusRepo: StatusRepository,
    private val wallRepo: WallRepository,
    private val seenRepo: SocialSeenRepository
) {

    private var cachedGlobalStatuses: List<StatusEntry> = emptyList()
    private var lastStatusFetch: Long = 0
    private val statusCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<String, List<StatusEntry>>()
    private val statusCountCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<String, Int>()
    private val statusCommentCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<Int, List<StatusComment>>()
    private val lastStatusPublishAt = ConcurrentHashMap<String, Long>()
    private val lastStatusCommentAt = ConcurrentHashMap<String, Long>()
    private val lastWallPostAt = ConcurrentHashMap<String, Long>()
    private val lastWallReplyAt = ConcurrentHashMap<String, Long>()

    private val wallCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<String, List<WallEntry>>()
    private val wallCommentCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<Int, List<WallComment>>()

    fun statusSeenAtSync(ownerUid: String, viewerUid: String): Long {
        return if (ownerUid == viewerUid) Long.MAX_VALUE else seenRepo.getStatusSeenAtSync(ownerUid, viewerUid)
    }

    fun wallSeenAtSync(ownerUid: String, viewerUid: String): Long {
        return seenRepo.getWallSeenAtSync(ownerUid, viewerUid)
    }

    fun unreadStatusIdsSync(ownerUid: String, viewerUid: String, entries: List<StatusEntry>? = null): Set<Int> {
        if (ownerUid == viewerUid) return emptySet()
        val seenAt = statusSeenAtSync(ownerUid, viewerUid)
        val statuses = entries ?: getStatusesCached(ownerUid, viewerUid)
        return statuses.asSequence()
            .filter { it.timestamp > seenAt }
            .map(StatusEntry::id)
            .toCollection(linkedSetOf())
    }

    fun unreadStatusCountSync(ownerUid: String, viewerUid: String): Int {
        return unreadStatusIdsSync(ownerUid, viewerUid).size
    }

    fun unreadWallIdsSync(ownerUid: String, viewerUid: String, entries: List<WallEntry>? = null): Set<Int> {
        val seenAt = wallSeenAtSync(ownerUid, viewerUid)
        val wallEntries = entries ?: getWallCommentsCached(ownerUid, viewerUid)
        return wallEntries.asSequence()
            .filter { it.timestamp > seenAt }
            .map(WallEntry::id)
            .toCollection(linkedSetOf())
    }

    fun unreadWallCountSync(ownerUid: String, viewerUid: String): Int {
        return unreadWallIdsSync(ownerUid, viewerUid).size
    }

    fun markStatusSeenSync(ownerUid: String, viewerUid: String, entries: List<StatusEntry>? = null) {
        if (ownerUid == viewerUid) return
        val latestSeen = (entries ?: getStatusesCached(ownerUid, viewerUid))
            .maxOfOrNull(StatusEntry::timestamp)
            ?: return
        seenRepo.markStatusSeenSync(ownerUid, viewerUid, latestSeen)
    }

    fun markWallSeenSync(ownerUid: String, viewerUid: String, entries: List<WallEntry>? = null) {
        val latestSeen = (entries ?: getWallCommentsCached(ownerUid, viewerUid))
            .maxOfOrNull(WallEntry::timestamp)
            ?: return
        seenRepo.markWallSeenSync(ownerUid, viewerUid, latestSeen)
    }

    suspend fun publishStatus(player: Player, uid: String, content: String, visibility: StatusVisibility): SocialWriteResult {
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWriteResult.EMPTY

        val now = System.currentTimeMillis()
        if (cooling(lastStatusPublishAt[uid], statusPublishCooldown(player), now)) return SocialWriteResult.COOLDOWN

        statusRepo.trim(uid, (statusMax(player) - 1).coerceAtLeast(0))
        statusRepo.publish(uid, clean, visibility, now)
        lastStatusPublishAt[uid] = now
        invalidateStatusCache(uid)
        Bukkit.getPluginManager().callEvent(CyuStatusPublishEvent(uid, clean, visibility.id, now))
        return SocialWriteResult.SUCCESS
    }

    fun publishStatusSync(player: Player, uid: String, content: String, visibility: StatusVisibility): SocialWriteResult {
        return publishStatusSync(uid, content, visibility, statusMax(player), statusPublishCooldown(player))
    }

    fun publishStatusSync(uid: String, content: String, visibility: StatusVisibility, maxStatuses: Int, cooldownSeconds: Long): SocialWriteResult {
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWriteResult.EMPTY

        val now = System.currentTimeMillis()
        if (cooling(lastStatusPublishAt[uid], cooldownSeconds, now)) return SocialWriteResult.COOLDOWN

        statusRepo.trimSync(uid, (maxStatuses - 1).coerceAtLeast(0))
        statusRepo.publishSync(uid, clean, visibility, now)
        lastStatusPublishAt[uid] = now
        invalidateStatusCache(uid)
        Bukkit.getPluginManager().callEvent(CyuStatusPublishEvent(uid, clean, visibility.id, now))
        return SocialWriteResult.SUCCESS
    }

    suspend fun getGlobalStatuses(viewerUid: String): List<StatusEntry> {
        val now = System.currentTimeMillis()
        if (now - lastStatusFetch > 10000) {
            cachedGlobalStatuses = statusRepo.getRecent(200)
            lastStatusFetch = now
        }
        return cachedGlobalStatuses.filter { canViewStatus(viewerUid, it) }
    }

    fun getGlobalStatusesSync(viewerUid: String): List<StatusEntry> {
        val now = System.currentTimeMillis()
        if (now - lastStatusFetch > 10000) {
            cachedGlobalStatuses = statusRepo.getRecentSync(200)
            lastStatusFetch = now
        }
        return cachedGlobalStatuses.filter { canViewStatusSync(viewerUid, it) }
    }

    fun getGlobalStatusesCachedSync(viewerUid: String): List<StatusEntry> {
        return cachedGlobalStatuses.filter { canViewStatusSync(viewerUid, it) }
    }

    suspend fun getStatuses(uid: String, viewerUid: String): List<StatusEntry> {
        val statuses = statusCache.getIfPresent(uid) ?: statusRepo.getByUid(uid, 100).also {
            statusCache.put(uid, it)
            statusCountCache.put(uid, it.size)
        }
        return statuses.filter { canViewStatus(viewerUid, it) }
    }

    fun getStatusesSync(uid: String, viewerUid: String): List<StatusEntry> {
        val statuses = statusCache.getIfPresent(uid) ?: statusRepo.getByUidSync(uid, 100).also {
            statusCache.put(uid, it)
            statusCountCache.put(uid, it.size)
        }
        return statuses.filter { canViewStatusSync(viewerUid, it) }
    }

    suspend fun getStatusCount(uid: String): Int {
        statusCountCache.getIfPresent(uid)?.let { return it }
        statusCache.getIfPresent(uid)?.let {
            statusCountCache.put(uid, it.size)
            return it.size
        }
        return statusRepo.countByUid(uid).also { statusCountCache.put(uid, it) }
    }

    fun getStatusCountSync(uid: String): Int {
        statusCountCache.getIfPresent(uid)?.let { return it }
        statusCache.getIfPresent(uid)?.let {
            statusCountCache.put(uid, it.size)
            return it.size
        }
        return statusRepo.countByUidSync(uid).also { statusCountCache.put(uid, it) }
    }

    suspend fun getLatestStatus(uid: String, viewerUid: String = uid): String? {
        return getStatuses(uid, viewerUid).firstOrNull()?.content
    }

    fun getLatestStatusSync(uid: String, viewerUid: String = uid): String? {
        return getStatusesSync(uid, viewerUid).firstOrNull()?.content
    }

    suspend fun getStatusByIndex(uid: String, viewerUid: String, index: Int): String? {
        if (index < 0) return null
        return getStatuses(uid, viewerUid).getOrNull(index)?.content
    }

    fun getStatusByIndexSync(uid: String, viewerUid: String, index: Int): String? {
        if (index < 0) return null
        return getStatusesSync(uid, viewerUid).getOrNull(index)?.content
    }

    suspend fun deleteStatus(uid: String, id: Int, force: Boolean): SocialDeleteResult {
        val entry = statusRepo.getById(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.uid != uid) return SocialDeleteResult.FORBIDDEN
        statusRepo.delete(id)
        invalidateStatusCache(entry.uid)
        return SocialDeleteResult.SUCCESS
    }

    fun deleteStatusSync(uid: String, id: Int, force: Boolean): SocialDeleteResult {
        val entry = statusRepo.getByIdSync(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.uid != uid) return SocialDeleteResult.FORBIDDEN
        statusRepo.deleteSync(id)
        invalidateStatusCache(entry.uid)
        return SocialDeleteResult.SUCCESS
    }

    suspend fun setStatusPinned(uid: String, id: Int, pinned: Boolean, force: Boolean): SocialDeleteResult {
        val entry = statusRepo.getById(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.uid != uid) return SocialDeleteResult.FORBIDDEN
        if (pinned) {
            statusRepo.clearPinned(entry.uid)
        }
        statusRepo.updatePinned(id, pinned)
        invalidateStatusCache(entry.uid)
        return SocialDeleteResult.SUCCESS
    }

    fun setStatusPinnedSync(uid: String, id: Int, pinned: Boolean, force: Boolean): SocialDeleteResult {
        val entry = statusRepo.getByIdSync(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.uid != uid) return SocialDeleteResult.FORBIDDEN
        if (pinned) {
            statusRepo.clearPinnedSync(entry.uid)
        }
        statusRepo.updatePinnedSync(id, pinned)
        invalidateStatusCache(entry.uid)
        return SocialDeleteResult.SUCCESS
    }

    suspend fun getStatusOwner(id: Int): String? {
        return statusRepo.getById(id)?.uid
    }

    fun getStatusOwnerSync(id: Int): String? {
        return statusRepo.getByIdSync(id)?.uid
    }

    suspend fun getVisibleStatusEntry(viewerUid: String, id: Int): StatusEntry? {
        val entry = statusRepo.getById(id) ?: return null
        return entry.takeIf { canViewStatus(viewerUid, it) }
    }

    fun getVisibleStatusEntrySync(viewerUid: String, id: Int): StatusEntry? {
        val entry = statusRepo.getByIdSync(id) ?: return null
        return entry.takeIf { canViewStatusSync(viewerUid, it) }
    }

    fun explainStatusVisibilitySync(viewerUid: String, id: Int): String {
        val entry = statusRepo.getByIdSync(id) ?: return "not-found"
        if (viewerUid == entry.uid) return "owner"
        return when (entry.visibility) {
            StatusVisibility.PUBLIC -> "visible-public"
            StatusVisibility.PRIVATE -> "hidden-private"
            StatusVisibility.FRIENDS -> {
                val friend = plugin.moduleManager.getModule<FriendModule>("friend")
                when {
                    friend == null -> "hidden-friends-no-friend-module"
                    friend.friendManager.isFriendStable(viewerUid, entry.uid) -> "visible-friends"
                    else -> "hidden-friends-not-friend"
                }
            }
        }
    }

    suspend fun setStatusLiked(uid: String, id: Int, liked: Boolean): SocialReactionResult {
        val entry = statusRepo.getById(id) ?: return SocialReactionResult.NOT_FOUND
        val changed = if (liked) {
            statusRepo.likeStatus(id, uid, System.currentTimeMillis())
        } else {
            statusRepo.unlikeStatus(id, uid)
        }
        if (!changed) return if (liked) SocialReactionResult.ALREADY_REACTED else SocialReactionResult.NOT_REACTED
        invalidateStatusCache(entry.uid)
        Bukkit.getPluginManager().callEvent(CyuStatusReactionEvent(id, entry.uid, uid, liked, System.currentTimeMillis()))
        return SocialReactionResult.SUCCESS
    }

    fun setStatusLikedSync(uid: String, id: Int, liked: Boolean): SocialReactionResult {
        val entry = statusRepo.getByIdSync(id) ?: return SocialReactionResult.NOT_FOUND
        val changed = if (liked) {
            statusRepo.likeStatusSync(id, uid, System.currentTimeMillis())
        } else {
            statusRepo.unlikeStatusSync(id, uid)
        }
        if (!changed) return if (liked) SocialReactionResult.ALREADY_REACTED else SocialReactionResult.NOT_REACTED
        invalidateStatusCache(entry.uid)
        Bukkit.getPluginManager().callEvent(CyuStatusReactionEvent(id, entry.uid, uid, liked, System.currentTimeMillis()))
        return SocialReactionResult.SUCCESS
    }

    suspend fun addStatusComment(player: Player, id: Int, authorUid: String, content: String): SocialWriteResult {
        val entry = statusRepo.getById(id) ?: return SocialWriteResult.EMPTY
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWriteResult.EMPTY
        val now = System.currentTimeMillis()
        if (cooling(lastStatusCommentAt[authorUid], statusCommentCooldown(player), now)) return SocialWriteResult.COOLDOWN
        statusRepo.addComment(id, authorUid, clean, now)
        lastStatusCommentAt[authorUid] = now
        invalidateStatusComments(id)
        invalidateStatusCache(entry.uid)
        Bukkit.getPluginManager().callEvent(CyuStatusCommentEvent(id, entry.uid, authorUid, clean, now))
        return SocialWriteResult.SUCCESS
    }

    fun addStatusCommentSync(player: Player, id: Int, authorUid: String, content: String): SocialWriteResult {
        return addStatusCommentSync(id, authorUid, content, statusCommentCooldown(player))
    }

    fun addStatusCommentSync(id: Int, authorUid: String, content: String, cooldownSeconds: Long): SocialWriteResult {
        val entry = statusRepo.getByIdSync(id) ?: return SocialWriteResult.EMPTY
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWriteResult.EMPTY
        val now = System.currentTimeMillis()
        if (cooling(lastStatusCommentAt[authorUid], cooldownSeconds, now)) return SocialWriteResult.COOLDOWN
        statusRepo.addCommentSync(id, authorUid, clean, now)
        lastStatusCommentAt[authorUid] = now
        invalidateStatusComments(id)
        invalidateStatusCache(entry.uid)
        Bukkit.getPluginManager().callEvent(CyuStatusCommentEvent(id, entry.uid, authorUid, clean, now))
        return SocialWriteResult.SUCCESS
    }

    suspend fun getStatusComments(id: Int, limit: Int = 20): List<StatusComment> {
        val cached = statusCommentCache.getIfPresent(id)
        if (cached != null) return cached
        return statusRepo.getComments(id, limit).also { statusCommentCache.put(id, it) }
    }

    fun getStatusCommentsSync(id: Int, limit: Int = 20): List<StatusComment> {
        val cached = statusCommentCache.getIfPresent(id)
        if (cached != null) return cached
        return statusRepo.getCommentsSync(id, limit).also { statusCommentCache.put(id, it) }
    }

    fun getLikedStatusIdsSync(userUid: String, statusIds: Collection<Int>): Set<Int> {
        return statusRepo.getLikedStatusIdsSync(userUid, statusIds)
    }

    suspend fun getStatusComment(id: Int): StatusComment? {
        return statusRepo.getCommentById(id)
    }

    fun getStatusCommentSync(id: Int): StatusComment? {
        return statusRepo.getCommentByIdSync(id)
    }

    suspend fun deleteStatusComment(actorUid: String, id: Int, force: Boolean): SocialDeleteResult {
        val entry = statusRepo.getCommentById(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.authorUid != actorUid) return SocialDeleteResult.FORBIDDEN
        val statusOwner = statusRepo.getById(entry.statusId)?.uid
        statusRepo.deleteComment(id)
        invalidateStatusComments(entry.statusId)
        invalidateStatusCache(statusOwner)
        return SocialDeleteResult.SUCCESS
    }

    fun deleteStatusCommentSync(actorUid: String, id: Int, force: Boolean): SocialDeleteResult {
        val entry = statusRepo.getCommentByIdSync(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.authorUid != actorUid) return SocialDeleteResult.FORBIDDEN
        val statusOwner = statusRepo.getByIdSync(entry.statusId)?.uid
        statusRepo.deleteCommentSync(id)
        invalidateStatusComments(entry.statusId)
        invalidateStatusCache(statusOwner)
        return SocialDeleteResult.SUCCESS
    }

    suspend fun postWallComment(player: Player, owner: String, author: String, content: String, visibility: WallVisibility): SocialWallSubmitResult {
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWallSubmitResult.EMPTY

        val now = System.currentTimeMillis()
        if (cooling(lastWallPostAt[author], wallPostCooldown(player), now)) return SocialWallSubmitResult.COOLDOWN

        val approved = wallAutoApproved(owner, author, player)
        wallRepo.trim(owner, (wallMax(player) - 1).coerceAtLeast(0))
        wallRepo.addComment(owner, author, clean, visibility, approved, now)
        lastWallPostAt[author] = now
        wallCache.invalidate(owner)
        Bukkit.getPluginManager().callEvent(CyuWallPostEvent(owner, author, clean, visibility.id, approved, now))
        return if (approved) SocialWallSubmitResult.SUCCESS else SocialWallSubmitResult.PENDING
    }

    fun postWallCommentSync(player: Player, owner: String, author: String, content: String, visibility: WallVisibility): SocialWallSubmitResult {
        return postWallCommentSync(
            owner,
            author,
            content,
            visibility,
            wallMax(player),
            wallPostCooldown(player),
            wallAutoApproved(owner, author, player.hasPermission("cyufriends.admin"))
        )
    }

    fun postWallCommentSync(
        owner: String,
        author: String,
        content: String,
        visibility: WallVisibility,
        maxMessages: Int,
        cooldownSeconds: Long,
        approved: Boolean
    ): SocialWallSubmitResult {
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWallSubmitResult.EMPTY

        val now = System.currentTimeMillis()
        if (cooling(lastWallPostAt[author], cooldownSeconds, now)) return SocialWallSubmitResult.COOLDOWN

        wallRepo.trimSync(owner, (maxMessages - 1).coerceAtLeast(0))
        wallRepo.addCommentSync(owner, author, clean, visibility, approved, now)
        lastWallPostAt[author] = now
        wallCache.invalidate(owner)
        Bukkit.getPluginManager().callEvent(CyuWallPostEvent(owner, author, clean, visibility.id, approved, now))
        return if (approved) SocialWallSubmitResult.SUCCESS else SocialWallSubmitResult.PENDING
    }

    suspend fun getWallComments(owner: String, viewerUid: String = owner, includePending: Boolean = false): List<WallEntry> {
        val cached = wallCache.getIfPresent(owner)
        val wall = cached ?: wallRepo.getWall(owner).also { wallCache.put(owner, it) }
        return filterWallEntries(wall, viewerUid, includePending)
    }

    fun getWallCommentsSync(owner: String, viewerUid: String = owner, includePending: Boolean = false): List<WallEntry> {
        val cached = wallCache.getIfPresent(owner)
        val wall = cached ?: wallRepo.getWallSync(owner).also { wallCache.put(owner, it) }
        return filterWallEntriesSync(wall, viewerUid, includePending)
    }

    suspend fun getWallCount(owner: String, viewerUid: String = owner): Int {
        return getWallComments(owner, viewerUid).size
    }

    fun getVisibleWallCountSync(owner: String, viewerUid: String = owner): Int {
        return getWallCommentsSync(owner, viewerUid).size
    }

    fun getWallCountSync(owner: String): Int {
        wallCache.getIfPresent(owner)?.let { return it.size }
        return wallRepo.countWallSync(owner)
    }

    suspend fun getWallComment(owner: String, index: Int, viewerUid: String = owner): WallEntry? {
        if (index < 0) return null
        return getWallComments(owner, viewerUid).getOrNull(index)
    }

    suspend fun deleteWallComment(actor: String, id: Int, force: Boolean): SocialDeleteResult {
        val entry = wallRepo.getById(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.ownerUid != actor && entry.authorUid != actor) return SocialDeleteResult.FORBIDDEN
        wallRepo.delete(id)
        invalidateWallCommentReplies(id)
        wallCache.invalidate(entry.ownerUid)
        return SocialDeleteResult.SUCCESS
    }

    fun deleteWallCommentSync(actor: String, id: Int, force: Boolean): SocialDeleteResult {
        val entry = wallRepo.getByIdSync(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.ownerUid != actor && entry.authorUid != actor) return SocialDeleteResult.FORBIDDEN
        wallRepo.deleteSync(id)
        invalidateWallCommentReplies(id)
        wallCache.invalidate(entry.ownerUid)
        return SocialDeleteResult.SUCCESS
    }

    suspend fun getWallOwner(id: Int): String? {
        return wallRepo.getById(id)?.ownerUid
    }

    fun getWallOwnerSync(id: Int): String? {
        return wallRepo.getByIdSync(id)?.ownerUid
    }

    suspend fun getWallEntry(id: Int): WallEntry? {
        return wallRepo.getById(id)
    }

    fun getWallEntrySync(id: Int): WallEntry? {
        return wallRepo.getByIdSync(id)
    }

    suspend fun getPendingWallEntries(ownerUid: String): List<WallEntry> {
        return wallRepo.pending(ownerUid)
    }

    fun getPendingWallEntriesSync(ownerUid: String): List<WallEntry> {
        return wallRepo.pendingSync(ownerUid)
    }

    suspend fun pendingWallCount(ownerUid: String): Int {
        return wallRepo.countPendingWalls(ownerUid)
    }

    fun pendingWallCountSync(ownerUid: String): Int {
        return wallRepo.countPendingWallsSync(ownerUid)
    }

    suspend fun globalPendingWallCount(): Int {
        return wallRepo.countPendingWalls()
    }

    fun globalPendingWallCountSync(): Int {
        return wallRepo.countPendingWallsSync()
    }

    suspend fun globalPendingReplyCount(): Int {
        return wallRepo.countPendingReplies()
    }

    fun globalPendingReplyCountSync(): Int {
        return wallRepo.countPendingRepliesSync()
    }

    suspend fun pendingWallReplyCount(ownerUid: String): Int {
        return wallRepo.countPendingReplies(ownerUid)
    }

    fun pendingWallReplyCountSync(ownerUid: String): Int {
        return wallRepo.countPendingRepliesSync(ownerUid)
    }

    suspend fun recentPendingWalls(limit: Int = 5): List<WallEntry> {
        return wallRepo.recentPendingWalls(limit)
    }

    fun recentPendingWallsSync(limit: Int = 5): List<WallEntry> {
        return wallRepo.recentPendingWallsSync(limit)
    }

    suspend fun recentPendingReplies(limit: Int = 5): List<PendingWallReplyEntry> {
        return wallRepo.recentPendingReplies(limit)
    }

    fun recentPendingRepliesSync(limit: Int = 5): List<PendingWallReplyEntry> {
        return wallRepo.recentPendingRepliesSync(limit)
    }

    suspend fun recentPendingReplies(ownerUid: String, limit: Int = 5): List<PendingWallReplyEntry> {
        return wallRepo.recentPendingReplies(ownerUid, limit)
    }

    fun recentPendingRepliesSync(ownerUid: String, limit: Int = 5): List<PendingWallReplyEntry> {
        return wallRepo.recentPendingRepliesSync(ownerUid, limit)
    }

    suspend fun reviewWallEntry(actorUid: String, wallId: Int, approve: Boolean, force: Boolean): SocialModerationResult {
        val entry = wallRepo.getById(wallId) ?: return SocialModerationResult.NOT_FOUND
        if (!force && actorUid != entry.ownerUid) return SocialModerationResult.FORBIDDEN
        if (approve) {
            if (entry.approved) return SocialModerationResult.ALREADY_APPROVED
            wallRepo.updateApproved(wallId, true)
        } else {
            wallRepo.delete(wallId)
            invalidateWallCommentReplies(wallId)
        }
        wallCache.invalidate(entry.ownerUid)
        return SocialModerationResult.SUCCESS
    }

    fun reviewWallEntrySync(actorUid: String, wallId: Int, approve: Boolean, force: Boolean): SocialModerationResult {
        val entry = wallRepo.getByIdSync(wallId) ?: return SocialModerationResult.NOT_FOUND
        if (!force && actorUid != entry.ownerUid) return SocialModerationResult.FORBIDDEN
        if (approve) {
            if (entry.approved) return SocialModerationResult.ALREADY_APPROVED
            wallRepo.updateApprovedSync(wallId, true)
        } else {
            wallRepo.deleteSync(wallId)
            invalidateWallCommentReplies(wallId)
        }
        wallCache.invalidate(entry.ownerUid)
        return SocialModerationResult.SUCCESS
    }

    suspend fun reviewAllWallEntries(actorUid: String, ownerUid: String, approve: Boolean, force: Boolean): Pair<SocialModerationResult, Int> {
        if (!force && actorUid != ownerUid) return SocialModerationResult.FORBIDDEN to 0
        val pending = wallRepo.pending(ownerUid)
        if (pending.isEmpty()) return SocialModerationResult.EMPTY to 0
        if (approve) {
            pending.forEach { wallRepo.updateApproved(it.id, true) }
        } else {
            pending.forEach {
                wallRepo.delete(it.id)
                invalidateWallCommentReplies(it.id)
            }
        }
        wallCache.invalidate(ownerUid)
        return SocialModerationResult.SUCCESS to pending.size
    }

    fun reviewAllWallEntriesSync(actorUid: String, ownerUid: String, approve: Boolean, force: Boolean): Pair<SocialModerationResult, Int> {
        if (!force && actorUid != ownerUid) return SocialModerationResult.FORBIDDEN to 0
        val pending = wallRepo.pendingSync(ownerUid)
        if (pending.isEmpty()) return SocialModerationResult.EMPTY to 0
        if (approve) {
            pending.forEach { wallRepo.updateApprovedSync(it.id, true) }
        } else {
            pending.forEach {
                wallRepo.deleteSync(it.id)
                invalidateWallCommentReplies(it.id)
            }
        }
        wallCache.invalidate(ownerUid)
        return SocialModerationResult.SUCCESS to pending.size
    }

    suspend fun setWallPinned(uid: String, id: Int, pinned: Boolean, force: Boolean): SocialDeleteResult {
        val entry = wallRepo.getById(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.ownerUid != uid) return SocialDeleteResult.FORBIDDEN
        if (pinned) {
            wallRepo.clearPinned(entry.ownerUid)
        }
        wallRepo.updatePinned(id, pinned)
        wallCache.invalidate(entry.ownerUid)
        return SocialDeleteResult.SUCCESS
    }

    fun setWallPinnedSync(uid: String, id: Int, pinned: Boolean, force: Boolean): SocialDeleteResult {
        val entry = wallRepo.getByIdSync(id) ?: return SocialDeleteResult.NOT_FOUND
        if (!force && entry.ownerUid != uid) return SocialDeleteResult.FORBIDDEN
        if (pinned) {
            wallRepo.clearPinnedSync(entry.ownerUid)
        }
        wallRepo.updatePinnedSync(id, pinned)
        wallCache.invalidate(entry.ownerUid)
        return SocialDeleteResult.SUCCESS
    }

    suspend fun setWallLiked(uid: String, id: Int, liked: Boolean): SocialReactionResult {
        val entry = wallRepo.getById(id) ?: return SocialReactionResult.NOT_FOUND
        val changed = if (liked) {
            wallRepo.likeWall(id, uid, System.currentTimeMillis())
        } else {
            wallRepo.unlikeWall(id, uid)
        }
        if (!changed) return if (liked) SocialReactionResult.ALREADY_REACTED else SocialReactionResult.NOT_REACTED
        wallCache.invalidate(entry.ownerUid)
        return SocialReactionResult.SUCCESS
    }

    fun setWallLikedSync(uid: String, id: Int, liked: Boolean): SocialReactionResult {
        val entry = wallRepo.getByIdSync(id) ?: return SocialReactionResult.NOT_FOUND
        val changed = if (liked) {
            wallRepo.likeWallSync(id, uid, System.currentTimeMillis())
        } else {
            wallRepo.unlikeWallSync(id, uid)
        }
        if (!changed) return if (liked) SocialReactionResult.ALREADY_REACTED else SocialReactionResult.NOT_REACTED
        wallCache.invalidate(entry.ownerUid)
        return SocialReactionResult.SUCCESS
    }

    suspend fun addWallReply(player: Player, wallId: Int, authorUid: String, content: String): SocialWallSubmitResult {
        val entry = wallRepo.getById(wallId) ?: return SocialWallSubmitResult.EMPTY
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWallSubmitResult.EMPTY
        val now = System.currentTimeMillis()
        if (cooling(lastWallReplyAt[authorUid], wallReplyCooldown(player), now)) return SocialWallSubmitResult.COOLDOWN
        val approved = wallReplyAutoApproved(entry.ownerUid, authorUid, player)
        wallRepo.addReply(wallId, authorUid, clean, approved, now)
        lastWallReplyAt[authorUid] = now
        invalidateWallCommentReplies(wallId)
        wallCache.invalidate(entry.ownerUid)
        return if (approved) SocialWallSubmitResult.SUCCESS else SocialWallSubmitResult.PENDING
    }

    fun addWallReplySync(player: Player, wallId: Int, authorUid: String, content: String): SocialWallSubmitResult {
        return addWallReplySync(wallId, authorUid, content, wallReplyCooldown(player), player.hasPermission("cyufriends.admin"))
    }

    fun addWallReplySync(wallId: Int, authorUid: String, content: String, cooldownSeconds: Long, adminApproved: Boolean): SocialWallSubmitResult {
        val entry = wallRepo.getByIdSync(wallId) ?: return SocialWallSubmitResult.EMPTY
        val clean = content.trim()
        if (clean.isEmpty()) return SocialWallSubmitResult.EMPTY
        val now = System.currentTimeMillis()
        if (cooling(lastWallReplyAt[authorUid], cooldownSeconds, now)) return SocialWallSubmitResult.COOLDOWN
        val approved = wallReplyAutoApproved(entry.ownerUid, authorUid, adminApproved)
        wallRepo.addReplySync(wallId, authorUid, clean, approved, now)
        lastWallReplyAt[authorUid] = now
        invalidateWallCommentReplies(wallId)
        wallCache.invalidate(entry.ownerUid)
        return if (approved) SocialWallSubmitResult.SUCCESS else SocialWallSubmitResult.PENDING
    }

    suspend fun getWallReplies(wallId: Int, viewerUid: String? = null, limit: Int = 20, includePending: Boolean = false): List<WallComment> {
        val cached = wallCommentCache.getIfPresent(wallId)
        val replies = cached ?: wallRepo.getReplies(wallId, limit, includePending = true).also { wallCommentCache.put(wallId, it) }
        val ownerUid = if (viewerUid != null && includePending) wallRepo.getById(wallId)?.ownerUid else null
        return filterWallReplies(replies, viewerUid, includePending, ownerUid)
    }

    fun getWallRepliesSync(wallId: Int, viewerUid: String? = null, limit: Int = 20, includePending: Boolean = false): List<WallComment> {
        val cached = wallCommentCache.getIfPresent(wallId)
        val replies = cached ?: wallRepo.getRepliesSync(wallId, limit, includePending = true).also { wallCommentCache.put(wallId, it) }
        val ownerUid = if (viewerUid != null && includePending) wallRepo.getByIdSync(wallId)?.ownerUid else null
        return filterWallReplies(replies, viewerUid, includePending, ownerUid)
    }

    fun getLikedWallIdsSync(userUid: String, wallIds: Collection<Int>): Set<Int> {
        return wallRepo.getLikedWallIdsSync(userUid, wallIds)
    }

    suspend fun getWallReply(id: Int): WallComment? {
        return wallRepo.getReplyById(id)
    }

    fun getWallReplySync(id: Int): WallComment? {
        return wallRepo.getReplyByIdSync(id)
    }

    suspend fun getPendingWallReplies(wallId: Int): List<WallComment> {
        return wallRepo.getPendingReplies(wallId)
    }

    fun getPendingWallRepliesSync(wallId: Int): List<WallComment> {
        return wallRepo.getPendingRepliesSync(wallId)
    }

    suspend fun pendingWallReplyCount(wallId: Int): Int {
        return wallRepo.getPendingReplies(wallId).size
    }

    fun pendingWallReplyCountSync(wallId: Int): Int {
        return wallRepo.getPendingRepliesSync(wallId).size
    }

    suspend fun reviewWallReply(actorUid: String, replyId: Int, approve: Boolean, force: Boolean): SocialModerationResult {
        val reply = wallRepo.getReplyById(replyId) ?: return SocialModerationResult.NOT_FOUND
        val wallOwner = wallRepo.getById(reply.wallId)?.ownerUid ?: return SocialModerationResult.NOT_FOUND
        if (!force && wallOwner != actorUid) return SocialModerationResult.FORBIDDEN
        if (approve) {
            if (reply.approved) return SocialModerationResult.ALREADY_APPROVED
            wallRepo.updateReplyApproved(replyId, true)
        } else {
            wallRepo.deleteReply(replyId)
        }
        invalidateWallCommentReplies(reply.wallId)
        wallCache.invalidate(wallOwner)
        return SocialModerationResult.SUCCESS
    }

    fun reviewWallReplySync(actorUid: String, replyId: Int, approve: Boolean, force: Boolean): SocialModerationResult {
        val reply = wallRepo.getReplyByIdSync(replyId) ?: return SocialModerationResult.NOT_FOUND
        val wallOwner = wallRepo.getByIdSync(reply.wallId)?.ownerUid ?: return SocialModerationResult.NOT_FOUND
        if (!force && wallOwner != actorUid) return SocialModerationResult.FORBIDDEN
        if (approve) {
            if (reply.approved) return SocialModerationResult.ALREADY_APPROVED
            wallRepo.updateReplyApprovedSync(replyId, true)
        } else {
            wallRepo.deleteReplySync(replyId)
        }
        invalidateWallCommentReplies(reply.wallId)
        wallCache.invalidate(wallOwner)
        return SocialModerationResult.SUCCESS
    }

    suspend fun reviewAllWallReplies(actorUid: String, wallId: Int, approve: Boolean, force: Boolean): Pair<SocialModerationResult, Int> {
        val entry = wallRepo.getById(wallId) ?: return SocialModerationResult.NOT_FOUND to 0
        if (!force && entry.ownerUid != actorUid) return SocialModerationResult.FORBIDDEN to 0
        val pending = wallRepo.getPendingReplies(wallId)
        if (pending.isEmpty()) return SocialModerationResult.EMPTY to 0
        if (approve) {
            pending.forEach { wallRepo.updateReplyApproved(it.id, true) }
        } else {
            pending.forEach { wallRepo.deleteReply(it.id) }
        }
        invalidateWallCommentReplies(wallId)
        wallCache.invalidate(entry.ownerUid)
        return SocialModerationResult.SUCCESS to pending.size
    }

    fun reviewAllWallRepliesSync(actorUid: String, wallId: Int, approve: Boolean, force: Boolean): Pair<SocialModerationResult, Int> {
        val entry = wallRepo.getByIdSync(wallId) ?: return SocialModerationResult.NOT_FOUND to 0
        if (!force && entry.ownerUid != actorUid) return SocialModerationResult.FORBIDDEN to 0
        val pending = wallRepo.getPendingRepliesSync(wallId)
        if (pending.isEmpty()) return SocialModerationResult.EMPTY to 0
        if (approve) {
            pending.forEach { wallRepo.updateReplyApprovedSync(it.id, true) }
        } else {
            pending.forEach { wallRepo.deleteReplySync(it.id) }
        }
        invalidateWallCommentReplies(wallId)
        wallCache.invalidate(entry.ownerUid)
        return SocialModerationResult.SUCCESS to pending.size
    }

    suspend fun deleteWallReply(actorUid: String, id: Int, force: Boolean): SocialDeleteResult {
        val reply = wallRepo.getReplyById(id) ?: return SocialDeleteResult.NOT_FOUND
        val wallOwner = wallRepo.getById(reply.wallId)?.ownerUid
        if (!force && reply.authorUid != actorUid && wallOwner != actorUid) return SocialDeleteResult.FORBIDDEN
        wallRepo.deleteReply(id)
        invalidateWallCommentReplies(reply.wallId)
        wallOwner?.let { wallCache.invalidate(it) }
        return SocialDeleteResult.SUCCESS
    }

    fun deleteWallReplySync(actorUid: String, id: Int, force: Boolean): SocialDeleteResult {
        val reply = wallRepo.getReplyByIdSync(id) ?: return SocialDeleteResult.NOT_FOUND
        val wallOwner = wallRepo.getByIdSync(reply.wallId)?.ownerUid
        if (!force && reply.authorUid != actorUid && wallOwner != actorUid) return SocialDeleteResult.FORBIDDEN
        wallRepo.deleteReplySync(id)
        invalidateWallCommentReplies(reply.wallId)
        wallOwner?.let { wallCache.invalidate(it) }
        return SocialDeleteResult.SUCCESS
    }

    fun invalidateWallCache(owner: String) {
        wallCache.invalidate(owner)
    }

    fun invalidateStatusCache(uid: String? = null) {
        lastStatusFetch = 0
        cachedGlobalStatuses = emptyList()
        if (uid == null) {
            statusCache.invalidateAll()
            statusCountCache.invalidateAll()
            return
        }
        statusCache.invalidate(uid)
        statusCountCache.invalidate(uid)
    }

    fun invalidateStatusComments(statusId: Int) {
        statusCommentCache.invalidate(statusId)
    }

    fun invalidateWallCommentReplies(wallId: Int) {
        wallCommentCache.invalidate(wallId)
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        statusRepo.updateUid(oldUid, newUid)
        wallRepo.updateUid(oldUid, newUid)
        seenRepo.updateUid(oldUid, newUid)
        invalidateStatusCache()
        statusCommentCache.invalidateAll()
        wallCommentCache.invalidateAll()
        transferCooldownState(lastStatusPublishAt, oldUid, newUid)
        transferCooldownState(lastStatusCommentAt, oldUid, newUid)
        transferCooldownState(lastWallPostAt, oldUid, newUid)
        transferCooldownState(lastWallReplyAt, oldUid, newUid)
        wallCache.invalidate(oldUid)
        wallCache.invalidate(newUid)
    }

    fun getStatusesCached(uid: String, viewerUid: String): List<StatusEntry> {
        val statuses = statusCache.getIfPresent(uid) ?: statusRepo.getByUidSync(uid, 100).also {
            statusCache.put(uid, it)
            statusCountCache.put(uid, it.size)
        }
        return statuses.filter { canViewStatusSync(viewerUid, it) }
    }

    fun getStatusCountCached(uid: String): Int {
        return statusCountCache.getIfPresent(uid)
            ?: statusCache.getIfPresent(uid)?.size
            ?: statusRepo.countByUidSync(uid).also { statusCountCache.put(uid, it) }
    }

    fun getLatestStatusCached(uid: String, viewerUid: String = uid): String? {
        return getStatusesCached(uid, viewerUid).firstOrNull()?.content
    }

    fun getStatusByIndexCached(uid: String, viewerUid: String, index: Int): String? {
        if (index < 0) return null
        return getStatusesCached(uid, viewerUid).getOrNull(index)?.content
    }

    fun getWallCommentsCached(owner: String, viewerUid: String = owner, includePending: Boolean = false): List<WallEntry> {
        val wall = wallCache.getIfPresent(owner) ?: wallRepo.getWallSync(owner).also { wallCache.put(owner, it) }
        return filterWallEntriesSync(wall, viewerUid, includePending)
    }

    fun getWallCountCached(owner: String, viewerUid: String = owner): Int {
        return getWallCommentsCached(owner, viewerUid).size
    }

    fun getWallRepliesCached(wallId: Int, viewerUid: String? = null, includePending: Boolean = false): List<WallComment> {
        val replies = wallCommentCache.getIfPresent(wallId) ?: wallRepo.getRepliesSync(wallId, 20, includePending = true).also { wallCommentCache.put(wallId, it) }
        val ownerUid = wallCache.asMap().values.flatten().firstOrNull { it.id == wallId }?.ownerUid
            ?: wallRepo.getByIdSync(wallId)?.ownerUid
        return filterWallReplies(replies, viewerUid, includePending, ownerUid)
    }

    fun pendingWallReplyIdsCached(ownerUid: String): List<Int> {
        return getWallCommentsCached(ownerUid, ownerUid, true)
            .flatMap { entry -> getWallRepliesCached(entry.id, ownerUid, true).filter { !it.approved }.map { it.id } }
            .distinct()
    }

    fun pendingWallReplyIdsSync(ownerUid: String): List<Int> {
        return getWallCommentsSync(ownerUid, ownerUid, true)
            .flatMap { entry -> getWallRepliesSync(entry.id, ownerUid, includePending = true).filter { !it.approved }.map { it.id } }
            .distinct()
    }

    fun pendingWallReplyWallIdsCached(ownerUid: String): List<Int> {
        return getWallCommentsCached(ownerUid, ownerUid, true)
            .map { it.id }
            .filter { getWallRepliesCached(it, ownerUid, true).any { reply -> !reply.approved } }
    }

    fun pendingWallReplyWallIdsSync(ownerUid: String): List<Int> {
        return getWallCommentsSync(ownerUid, ownerUid, true)
            .map { it.id }
            .filter { getWallRepliesSync(it, ownerUid, includePending = true).any { reply -> !reply.approved } }
    }

    fun remainingStatusPublishCooldown(uid: String, player: Player): Long {
        return remaining(lastStatusPublishAt[uid], statusPublishCooldown(player))
    }

    fun remainingStatusPublishCooldown(uid: String, cooldownSeconds: Long): Long {
        return remaining(lastStatusPublishAt[uid], cooldownSeconds)
    }

    fun remainingStatusCommentCooldown(uid: String, player: Player): Long {
        return remaining(lastStatusCommentAt[uid], statusCommentCooldown(player))
    }

    fun remainingStatusCommentCooldown(uid: String, cooldownSeconds: Long): Long {
        return remaining(lastStatusCommentAt[uid], cooldownSeconds)
    }

    fun remainingWallPostCooldown(uid: String, player: Player): Long {
        return remaining(lastWallPostAt[uid], wallPostCooldown(player))
    }

    fun remainingWallPostCooldown(uid: String, cooldownSeconds: Long): Long {
        return remaining(lastWallPostAt[uid], cooldownSeconds)
    }

    fun remainingWallReplyCooldown(uid: String, player: Player): Long {
        return remaining(lastWallReplyAt[uid], wallReplyCooldown(player))
    }

    fun remainingWallReplyCooldown(uid: String, cooldownSeconds: Long): Long {
        return remaining(lastWallReplyAt[uid], cooldownSeconds)
    }

    fun remainingStatusCooldown(uid: String, player: Player): Long {
        return remainingStatusPublishCooldown(uid, player)
    }

    fun remainingWallCooldown(uid: String, player: Player): Long {
        return remainingWallPostCooldown(uid, player)
    }

    private suspend fun canViewStatus(viewerUid: String, entry: StatusEntry): Boolean {
        if (viewerUid == entry.uid) return true
        return when (entry.visibility) {
            StatusVisibility.PUBLIC -> true
            StatusVisibility.PRIVATE -> false
            StatusVisibility.FRIENDS -> plugin.moduleManager.getModule<FriendModule>("friend")?.friendManager?.isFriendStored(viewerUid, entry.uid) == true
        }
    }

    private fun canViewStatusSync(viewerUid: String, entry: StatusEntry): Boolean {
        if (viewerUid == entry.uid) return true
        return when (entry.visibility) {
            StatusVisibility.PUBLIC -> true
            StatusVisibility.PRIVATE -> false
            StatusVisibility.FRIENDS -> plugin.moduleManager.getModule<FriendModule>("friend")?.friendManager?.isFriendStable(viewerUid, entry.uid) == true
        }
    }

    private fun canViewStatusCached(viewerUid: String, entry: StatusEntry): Boolean {
        if (viewerUid == entry.uid) return true
        return when (entry.visibility) {
            StatusVisibility.PUBLIC -> true
            StatusVisibility.PRIVATE -> false
            StatusVisibility.FRIENDS -> plugin.moduleManager.getModule<FriendModule>("friend")?.friendManager?.isFriend(viewerUid, entry.uid) == true
        }
    }

    private fun cooling(last: Long?, cooldownSeconds: Long, now: Long): Boolean {
        if (cooldownSeconds <= 0L || last == null) return false
        return now - last < cooldownSeconds * 1000L
    }

    private fun remaining(last: Long?, cooldownSeconds: Long): Long {
        if (cooldownSeconds <= 0L || last == null) return 0L
        val remaining = cooldownSeconds * 1000L - (System.currentTimeMillis() - last)
        return if (remaining <= 0L) 0L else ((remaining + 999L) / 1000L)
    }

    private fun statusMax(player: Player): Int {
        return permissionInt(player, "statusLimits", "maxStatuses", "cyufriends.status.", 20)
    }

    fun statusMaxLimit(player: Player): Int = statusMax(player)

    private fun statusPublishCooldown(player: Player): Long {
        return permissionLong(player, "statusLimits", listOf("publishCooldown", "cooldown"), "cyufriends.status.", 60L)
    }

    fun statusPublishCooldownSeconds(player: Player): Long = statusPublishCooldown(player)

    private fun statusCommentCooldown(player: Player): Long {
        return permissionLong(player, "statusLimits", listOf("commentCooldown", "publishCooldown", "cooldown"), "cyufriends.status.", 60L)
    }

    fun statusCommentCooldownSeconds(player: Player): Long = statusCommentCooldown(player)

    private fun wallMax(player: Player): Int {
        return permissionInt(player, "wallLimits", "maxMessages", "cyufriends.wall.", 20)
    }

    fun wallMaxLimit(player: Player): Int = wallMax(player)

    private fun wallPostCooldown(player: Player): Long {
        return permissionLong(player, "wallLimits", listOf("postCooldown", "cooldown"), "cyufriends.wall.", 60L)
    }

    fun wallPostCooldownSeconds(player: Player): Long = wallPostCooldown(player)

    private fun wallReplyCooldown(player: Player): Long {
        return permissionLong(player, "wallLimits", listOf("replyCooldown", "postCooldown", "cooldown"), "cyufriends.wall.", 60L)
    }

    fun wallReplyCooldownSeconds(player: Player): Long = wallReplyCooldown(player)

    private fun permissionInt(player: Player, sectionName: String, key: String, prefix: String, fallback: Int): Int {
        val section = plugin.config.getConfigurationSection(sectionName) ?: return fallback
        var value = section.getConfigurationSection("default")?.getInt(key, fallback) ?: fallback
        section.getKeys(false).forEach { group ->
            if (group != "default" && player.hasPermission(prefix + group)) {
                value = maxOf(value, section.getConfigurationSection(group)?.getInt(key, value) ?: value)
            }
        }
        return value.coerceAtLeast(1)
    }

    private fun permissionLong(player: Player, sectionName: String, keys: List<String>, prefix: String, fallback: Long): Long {
        val section = plugin.config.getConfigurationSection(sectionName) ?: return fallback
        var value = readLong(section.getConfigurationSection("default"), keys, fallback)
        section.getKeys(false).forEach { group ->
            if (group != "default" && player.hasPermission(prefix + group)) {
                value = minOf(value, readLong(section.getConfigurationSection(group), keys, value))
            }
        }
        return value.coerceAtLeast(0L)
    }

    private fun readLong(section: ConfigurationSection?, keys: List<String>, fallback: Long): Long {
        if (section == null) return fallback
        keys.firstOrNull { section.contains(it) }?.let { key ->
            return section.getLong(key, fallback)
        }
        return fallback
    }

    private fun transferCooldownState(state: ConcurrentHashMap<String, Long>, oldUid: String, newUid: String) {
        state.remove(oldUid)?.let { state[newUid] = it }
    }

    fun cachedStatusOwnerCount(): Int = statusCache.asMap().size

    fun cachedStatusCountEntryCount(): Int = statusCountCache.asMap().size

    fun cachedStatusCommentEntryCount(): Int = statusCommentCache.asMap().size

    fun cachedWallOwnerCount(): Int = wallCache.asMap().size

    fun cachedWallCommentEntryCount(): Int = wallCommentCache.asMap().size

    fun cachedGlobalStatusCount(): Int = cachedGlobalStatuses.size

    suspend fun canViewWallEntry(viewerUid: String, entry: WallEntry, force: Boolean = false): Boolean {
        if (force) return true
        if (viewerUid == entry.ownerUid || viewerUid == entry.authorUid) return true
        if (!entry.approved) return false
        return when (entry.visibility) {
            WallVisibility.PUBLIC -> true
            WallVisibility.FRIENDS -> plugin.moduleManager.getModule<FriendModule>("friend")?.friendManager?.isFriendStored(viewerUid, entry.ownerUid) == true
            WallVisibility.PRIVATE -> false
        }
    }

    fun canViewWallEntrySync(viewerUid: String, entry: WallEntry, force: Boolean = false): Boolean {
        if (force) return true
        if (viewerUid == entry.ownerUid || viewerUid == entry.authorUid) return true
        if (!entry.approved) return false
        return when (entry.visibility) {
            WallVisibility.PUBLIC -> true
            WallVisibility.FRIENDS -> plugin.moduleManager.getModule<FriendModule>("friend")?.friendManager?.isFriendStable(viewerUid, entry.ownerUid) == true
            WallVisibility.PRIVATE -> false
        }
    }

    fun canViewWallEntryCached(viewerUid: String, entry: WallEntry, force: Boolean = false): Boolean {
        return canViewWallEntryInternal(viewerUid, entry, force)
    }

    private fun filterWallEntries(entries: List<WallEntry>, viewerUid: String, includePending: Boolean): List<WallEntry> {
        return entries.filter {
            val canSeePending = includePending && (viewerUid == it.ownerUid || viewerUid == it.authorUid)
            canViewWallEntryCached(viewerUid, it, canSeePending)
        }
    }

    private fun filterWallEntriesSync(entries: List<WallEntry>, viewerUid: String, includePending: Boolean): List<WallEntry> {
        return entries.filter {
            val canSeePending = includePending && (viewerUid == it.ownerUid || viewerUid == it.authorUid)
            canViewWallEntrySync(viewerUid, it, canSeePending)
        }
    }

    private fun filterWallReplies(replies: List<WallComment>, viewerUid: String?, includePending: Boolean, ownerUid: String?): List<WallComment> {
        if (viewerUid == null) return if (includePending) replies else replies.filter { it.approved }
        val canSeePending = includePending && viewerUid == ownerUid
        return replies.filter { it.approved || it.authorUid == viewerUid || canSeePending }
    }

    private fun canViewWallEntryInternal(viewerUid: String, entry: WallEntry, force: Boolean): Boolean {
        if (force) return true
        if (viewerUid == entry.ownerUid || viewerUid == entry.authorUid) return true
        if (!entry.approved) return false
        return when (entry.visibility) {
            WallVisibility.PUBLIC -> true
            WallVisibility.FRIENDS -> plugin.moduleManager.getModule<FriendModule>("friend")?.friendManager?.isFriend(viewerUid, entry.ownerUid) == true
            WallVisibility.PRIVATE -> false
        }
    }

    private fun wallAutoApproved(ownerUid: String, authorUid: String, player: Player): Boolean {
        return wallAutoApproved(ownerUid, authorUid, player.hasPermission("cyufriends.admin"))
    }

    fun wallAutoApproved(ownerUid: String, authorUid: String, adminApproved: Boolean): Boolean {
        if (!plugin.config.getBoolean("wallModeration.enabled", false)) return true
        if (adminApproved) return true
        if (ownerUid == authorUid && plugin.config.getBoolean("wallModeration.auto-approve-self", true)) return true
        return false
    }

    private fun wallReplyAutoApproved(ownerUid: String, authorUid: String, player: Player): Boolean {
        return wallReplyAutoApproved(ownerUid, authorUid, player.hasPermission("cyufriends.admin"))
    }

    fun wallReplyAutoApproved(ownerUid: String, authorUid: String, adminApproved: Boolean): Boolean {
        val enabled = plugin.config.getBoolean("wallModeration.comment-enabled", plugin.config.getBoolean("wallModeration.enabled", false))
        if (!enabled) return true
        if (adminApproved) return true
        if (ownerUid == authorUid && plugin.config.getBoolean("wallModeration.comment-auto-approve-self", plugin.config.getBoolean("wallModeration.auto-approve-self", true))) return true
        return false
    }
}
