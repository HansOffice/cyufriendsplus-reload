package org.cyuCBMclean.cyufriendsReload.modules.friend

import com.github.benmanes.caffeine.cache.Caffeine
import org.bukkit.Bukkit
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestAcceptEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestDenyEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestRevokeEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestSendEvent
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class FriendRequestLimitResult {
    ALLOWED,
    COOLDOWN,
    DAILY_LIMIT
}

/**
 * 好友申请单独管，别和正式好友关系混在一起
 */
class RequestManager(private val repository: RequestRepository) {

    private val incomingRequests = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, ConcurrentHashMap<String, FriendRequestEntry>>()
    private val outgoingRequests = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, ConcurrentHashMap<String, FriendRequestEntry>>()
    private val sentCounts = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, Int>()
    private val sentTodayCounts = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)
        .build<String, TimedRequestCount>()
    private val lastSentAt = ConcurrentHashMap<String, Long>()

    suspend fun loadPlayer(uid: String) {
        val incoming = repository.getRequests(uid)
        val outgoing = repository.getSentRequests(uid)
        incomingRequests.put(uid, incoming.associateByTo(ConcurrentHashMap(), FriendRequestEntry::senderUid))
        outgoingRequests.put(uid, outgoing.associateByTo(ConcurrentHashMap(), FriendRequestEntry::receiverUid))
        sentCounts.put(uid, repository.countSent(uid))
        sentTodayCounts.put(uid, TimedRequestCount(todayStart(), repository.countSentSince(uid, todayStart())))
        DebugLogger.debug(1) { "好友申请缓存载入: uid=$uid incoming=${incoming.size} outgoing=${outgoing.size}" }
    }

    fun loadPlayerSync(uid: String) {
        val incoming = repository.getRequestsSync(uid)
        val outgoing = repository.getSentRequestsSync(uid)
        incomingRequests.put(uid, incoming.associateByTo(ConcurrentHashMap(), FriendRequestEntry::senderUid))
        outgoingRequests.put(uid, outgoing.associateByTo(ConcurrentHashMap(), FriendRequestEntry::receiverUid))
        sentCounts.put(uid, repository.countSentSync(uid))
        val start = todayStart()
        sentTodayCounts.put(uid, TimedRequestCount(start, repository.countSentSinceSync(uid, start)))
        DebugLogger.debug(1) { "好友申请缓存载入: uid=$uid incoming=${incoming.size} outgoing=${outgoing.size} source=db-sync" }
    }

    fun unloadPlayer(uid: String) {
        incomingRequests.invalidate(uid)
        outgoingRequests.invalidate(uid)
        sentCounts.invalidate(uid)
        sentTodayCounts.invalidate(uid)
        DebugLogger.debug(2) { "好友申请缓存已清理: uid=$uid reason=unload" }
    }

    fun hasRequest(sender: String, receiver: String): Boolean {
        return incomingRequests.getIfPresent(receiver)?.containsKey(sender) == true
    }

    fun hasRequestStable(sender: String, receiver: String): Boolean {
        if (hasRequest(sender, receiver)) return true
        return repository.hasRequestSync(sender, receiver)
    }

    fun getRequests(receiver: String): Set<String> {
        return incomingRequests.getIfPresent(receiver)?.keys?.toSet() ?: emptySet()
    }

    fun getRequestEntries(receiver: String): List<FriendRequestEntry> {
        return incomingRequests.getIfPresent(receiver)
            ?.values
            ?.sortedByDescending(FriendRequestEntry::createdAt)
            ?: emptyList()
    }

    fun getRequestEntry(receiver: String, sender: String): FriendRequestEntry? {
        return incomingRequests.getIfPresent(receiver)?.get(sender)
    }

    fun getSentRequests(sender: String): Set<String> {
        return outgoingRequests.getIfPresent(sender)?.keys?.toSet() ?: emptySet()
    }

    fun getSentRequestEntries(sender: String): List<FriendRequestEntry> {
        return outgoingRequests.getIfPresent(sender)
            ?.values
            ?.sortedByDescending(FriendRequestEntry::createdAt)
            ?: emptyList()
    }

    fun getSentRequestEntry(sender: String, receiver: String): FriendRequestEntry? {
        return outgoingRequests.getIfPresent(sender)?.get(receiver)
    }

    fun countReceivedCached(receiver: String): Int {
        return incomingRequests.getIfPresent(receiver)?.size ?: 0
    }

    fun countSentCached(sender: String): Int {
        return sentCounts.getIfPresent(sender) ?: 0
    }

    fun countSentSinceCached(sender: String, since: Long): Int? {
        val cached = sentTodayCounts.getIfPresent(sender) ?: return null
        return cached.takeIf { it.since == since }?.count
    }

    suspend fun hasRequestStored(sender: String, receiver: String): Boolean {
        if (hasRequest(sender, receiver)) return true
        return repository.hasRequest(sender, receiver)
    }

    suspend fun addRequest(sender: String, receiver: String, note: String?) {
        val now = System.currentTimeMillis()
        val entry = FriendRequestEntry(sender, receiver, note, now)
        repository.saveRequest(sender, receiver, note, now)
        lastSentAt[sender] = now
        incomingRequests.getIfPresent(receiver)?.put(sender, entry)
        outgoingRequests.getIfPresent(sender)?.put(receiver, entry)
        sentCounts.put(sender, countSentCached(sender) + 1)
        val todayStart = todayStart()
        val todayCached = sentTodayCounts.getIfPresent(sender)
        if (todayCached != null && todayCached.since == todayStart) {
            sentTodayCounts.put(sender, todayCached.copy(count = todayCached.count + 1))
        } else {
            sentTodayCounts.put(sender, TimedRequestCount(todayStart, repository.countSentSince(sender, todayStart)))
        }
        Bukkit.getPluginManager().callEvent(CyuFriendRequestSendEvent(sender, receiver, now))
        DebugLogger.debug(1) { "好友申请已创建: sender=$sender receiver=$receiver noteChars=${note.normalizedLength()} createdAt=$now" }
    }

    suspend fun removeRequest(sender: String, receiver: String): FriendRequestEntry? {
        val removed = incomingRequests.getIfPresent(receiver)?.remove(sender)
            ?: outgoingRequests.getIfPresent(sender)?.get(receiver)
            ?: repository.getSentRequests(sender).firstOrNull { it.receiverUid == receiver }
        outgoingRequests.getIfPresent(sender)?.remove(receiver)
        repository.deleteRequest(sender, receiver)
        sentCounts.getIfPresent(sender)?.let { sentCounts.put(sender, (it - 1).coerceAtLeast(0)) }
        val todayStart = todayStart()
        sentTodayCounts.getIfPresent(sender)?.takeIf { it.since == todayStart }?.let {
            sentTodayCounts.put(sender, it.copy(count = (it.count - 1).coerceAtLeast(0)))
        }
        DebugLogger.debug(1) { "好友申请已移除: sender=$sender receiver=$receiver existed=${removed != null}" }
        return removed
    }

    fun callAcceptEvent(sender: String, receiver: String) {
        Bukkit.getPluginManager().callEvent(CyuFriendRequestAcceptEvent(sender, receiver, System.currentTimeMillis()))
    }

    fun callDenyEvent(sender: String, receiver: String) {
        Bukkit.getPluginManager().callEvent(CyuFriendRequestDenyEvent(sender, receiver, System.currentTimeMillis()))
    }

    fun callRevokeEvent(sender: String, receiver: String) {
        Bukkit.getPluginManager().callEvent(CyuFriendRequestRevokeEvent(sender, receiver, System.currentTimeMillis()))
    }

    suspend fun clearExpiredCache(thresholdTime: Long) {
        repository.clearExpired(thresholdTime)
        DebugLogger.debug(1) { "好友申请过期清理已执行: threshold=$thresholdTime" }
    }

    suspend fun getRequestsFromDbForSync(receiver: String): List<FriendRequestEntry> {
        return repository.getRequests(receiver)
    }

    suspend fun getSentRequestsFromDbForSync(sender: String): List<FriendRequestEntry> {
        return repository.getSentRequests(sender)
    }

    suspend fun countReceived(receiver: String): Int {
        return incomingRequests.getIfPresent(receiver)?.size ?: repository.countReceived(receiver)
    }

    fun countReceivedSync(receiver: String): Int {
        return incomingRequests.getIfPresent(receiver)?.size ?: repository.countReceivedSync(receiver)
    }

    suspend fun countSent(sender: String): Int {
        sentCounts.getIfPresent(sender)?.let { return it }
        return repository.countSent(sender).also { sentCounts.put(sender, it) }
    }

    fun countSentSync(sender: String): Int {
        sentCounts.getIfPresent(sender)?.let { return it }
        return repository.countSentSync(sender).also { sentCounts.put(sender, it) }
    }

    suspend fun countSentSince(sender: String, since: Long): Int {
        countSentSinceCached(sender, since)?.let { return it }
        return repository.countSentSince(sender, since).also { sentTodayCounts.put(sender, TimedRequestCount(since, it)) }
    }

    fun countSentSinceSync(sender: String, since: Long): Int {
        countSentSinceCached(sender, since)?.let { return it }
        return repository.countSentSinceSync(sender, since).also { sentTodayCounts.put(sender, TimedRequestCount(since, it)) }
    }

    suspend fun checkLimit(sender: String, cooldownSeconds: Long, dailyLimit: Int, todayStart: Long): FriendRequestLimitResult {
        val cooldownRemaining = remainingCooldown(sender, cooldownSeconds)
        if (cooldownRemaining > 0L) {
            DebugLogger.debug(1) { "好友申请限流命中: sender=$sender reason=cooldown remaining=${cooldownRemaining}s" }
            return FriendRequestLimitResult.COOLDOWN
        }
        if (dailyLimit > 0) {
            val sentToday = countSentSince(sender, todayStart)
            if (sentToday >= dailyLimit) {
                DebugLogger.debug(1) { "好友申请限流命中: sender=$sender reason=daily-limit used=$sentToday limit=$dailyLimit" }
                return FriendRequestLimitResult.DAILY_LIMIT
            }
        }
        return FriendRequestLimitResult.ALLOWED
    }

    fun remainingCooldown(sender: String, cooldownSeconds: Long): Long {
        if (cooldownSeconds <= 0L) return 0L
        val last = lastSentAt[sender] ?: return 0L
        val remaining = cooldownSeconds * 1000L - (System.currentTimeMillis() - last)
        return if (remaining <= 0L) 0L else ((remaining + 999L) / 1000L)
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        repository.updateUid(oldUid, newUid)
        lastSentAt.remove(oldUid)?.let { lastSentAt[newUid] = it }
        incomingRequests.invalidate(oldUid)
        incomingRequests.invalidate(newUid)
        outgoingRequests.invalidate(oldUid)
        outgoingRequests.invalidate(newUid)
        sentCounts.invalidate(oldUid)
        sentCounts.invalidate(newUid)
        sentTodayCounts.invalidate(oldUid)
        sentTodayCounts.invalidate(newUid)
        incomingRequests.asMap().values.forEach { requests ->
            requests.remove(oldUid)?.let { entry ->
                requests[newUid] = entry.copy(senderUid = newUid)
            }
        }
        outgoingRequests.asMap().values.forEach { requests ->
            requests.remove(oldUid)?.let { entry ->
                requests[newUid] = entry.copy(receiverUid = newUid)
            }
        }
        DebugLogger.debug(1) { "好友申请 UID 已迁移: oldUid=$oldUid newUid=$newUid" }
    }

    fun invalidate(uid: String) {
        incomingRequests.invalidate(uid)
        outgoingRequests.invalidate(uid)
        sentCounts.invalidate(uid)
        sentTodayCounts.invalidate(uid)
        DebugLogger.debug(2) { "好友申请缓存已清理: uid=$uid reason=invalidate" }
    }

    fun cachedReceiverCount(): Int = incomingRequests.asMap().size

    fun cachedSenderCount(): Int = outgoingRequests.asMap().size

    fun cachedRequestCount(): Int = incomingRequests.asMap().values.sumOf { it.size }

    fun cooldownTrackerCount(): Int = lastSentAt.size

    private fun todayStart(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private data class TimedRequestCount(
        val since: Long,
        val count: Int
    )

    private fun String?.normalizedLength(): Int {
        return this?.trim()?.length ?: 0
    }
}
