package org.cyuCBMclean.cyufriendsReload.modules.chat

import com.github.benmanes.caffeine.cache.Caffeine
import org.bukkit.Bukkit
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.api.event.CyuPrivateMessageSendEvent
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class ChatSendResult {
    SUCCESS,
    EMPTY,
    COOLDOWN
}

data class PreparedChatMessage(
    val result: ChatSendResult,
    val content: String? = null
)

class ChatManager(
    private val plugin: CyufriendsReload,
    private val repository: ChatRepository
) {

    private val unreadCache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<String, List<ChatMessage>>()
    private val conversationCache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<String, List<ChatMessage>>()
    private val conversationSummaryCache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<String, List<ChatConversationSummary>>()
    private val lastConversations = ConcurrentHashMap<String, String>()
    private val lastSentAt = ConcurrentHashMap<String, Long>()

    fun setReplyTarget(senderUid: String, targetUid: String) {
        lastConversations[senderUid] = targetUid
        lastConversations[targetUid] = senderUid
        DebugLogger.debug(2) { "私聊回复目标已更新: first=$senderUid second=$targetUid" }
    }

    fun getReplyTarget(senderUid: String): String? {
        return lastConversations[senderUid]
    }

    fun clearTarget(uid: String) {
        lastConversations.remove(uid)
        DebugLogger.debug(2) { "私聊回复目标已清理: uid=$uid" }
    }

    suspend fun sendOfflineMessage(sender: String, receiver: String, content: String) {
        repository.saveMessage(sender, receiver, content, System.currentTimeMillis(), false)
        invalidateConversationState(sender, receiver)
        DebugLogger.debug(1) { "离线私聊已存储: sender=$sender receiver=$receiver chars=${content.normalizedLength()}" }
    }

    fun sendOfflineMessageSync(sender: String, receiver: String, content: String) {
        repository.saveMessageSync(sender, receiver, content, System.currentTimeMillis(), false)
        invalidateConversationState(sender, receiver)
        DebugLogger.debug(1) { "离线私聊已存储: sender=$sender receiver=$receiver chars=${content.normalizedLength()} source=db-sync" }
    }

    suspend fun logOnlineMessage(sender: String, receiver: String, content: String) {
        repository.saveMessage(sender, receiver, content, System.currentTimeMillis(), true)
        invalidateConversationState(sender, receiver, unreadChanged = false)
        DebugLogger.debug(1) { "在线私聊已记录: sender=$sender receiver=$receiver chars=${content.normalizedLength()}" }
    }

    fun logOnlineMessageSync(sender: String, receiver: String, content: String) {
        repository.saveMessageSync(sender, receiver, content, System.currentTimeMillis(), true)
        invalidateConversationState(sender, receiver, unreadChanged = false)
        DebugLogger.debug(1) { "在线私聊已记录: sender=$sender receiver=$receiver chars=${content.normalizedLength()} source=db-sync" }
    }

    suspend fun sendPlayerMessage(sender: String, receiver: String, content: String, read: Boolean): ChatSendResult {
        val prepared = prepareOutgoing(sender, content)
        val clean = prepared.content ?: return prepared.result
        val now = System.currentTimeMillis()
        repository.saveMessage(sender, receiver, clean, now, read)
        markSent(sender, now)
        invalidateConversationState(sender, receiver, unreadChanged = !read)
        Bukkit.getPluginManager().callEvent(CyuPrivateMessageSendEvent(sender, receiver, clean, read, now))
        DebugLogger.debug(1) { "私聊消息已写入: sender=$sender receiver=$receiver chars=${clean.length} read=$read" }
        return ChatSendResult.SUCCESS
    }

    fun sendPlayerMessageSync(sender: String, receiver: String, content: String, read: Boolean): ChatSendResult {
        val prepared = prepareOutgoing(sender, content)
        val clean = prepared.content ?: return prepared.result
        val now = System.currentTimeMillis()
        repository.saveMessageSync(sender, receiver, clean, now, read)
        markSent(sender, now)
        invalidateConversationState(sender, receiver, unreadChanged = !read)
        Bukkit.getPluginManager().callEvent(CyuPrivateMessageSendEvent(sender, receiver, clean, read, now))
        DebugLogger.debug(1) { "私聊消息已写入: sender=$sender receiver=$receiver chars=${clean.length} read=$read source=db-sync" }
        return ChatSendResult.SUCCESS
    }

    fun prepareOutgoing(sender: String, content: String): PreparedChatMessage {
        val clean = content.trim()
        if (clean.isEmpty()) {
            DebugLogger.debug(1) { "私聊发送已拦截: sender=$sender reason=empty" }
            return PreparedChatMessage(ChatSendResult.EMPTY)
        }
        val cooldownRemaining = remainingCooldown(sender)
        if (cooldownRemaining > 0L) {
            DebugLogger.debug(1) { "私聊发送已拦截: sender=$sender reason=cooldown remaining=${cooldownRemaining}s" }
            return PreparedChatMessage(ChatSendResult.COOLDOWN)
        }
        return PreparedChatMessage(ChatSendResult.SUCCESS, clean)
    }

    fun markSent(sender: String, timestamp: Long = System.currentTimeMillis()) {
        lastSentAt[sender] = timestamp
        DebugLogger.debug(2) { "私聊冷却戳已更新: sender=$sender timestamp=$timestamp" }
    }

    suspend fun getUnread(receiver: String): List<ChatMessage> {
        val cached = unreadCache.getIfPresent(receiver)
        if (cached != null) return cached
        return repository.getUnreadMessages(receiver).also {
            unreadCache.put(receiver, it)
            DebugLogger.debug(2) { "未读私聊缓存回填: receiver=$receiver count=${it.size} source=db" }
        }
    }

    fun getUnreadSync(receiver: String): List<ChatMessage> {
        val cached = unreadCache.getIfPresent(receiver)
        if (cached != null) return cached
        return repository.getUnreadMessagesSync(receiver).also {
            unreadCache.put(receiver, it)
            DebugLogger.debug(2) { "未读私聊缓存回填: receiver=$receiver count=${it.size} source=db-sync" }
        }
    }

    suspend fun getConversation(uid1: String, uid2: String, limit: Int = 45): List<ChatMessage> {
        val key = conversationKey(uid1, uid2)
        val cached = conversationCache.getIfPresent(key)
        if (cached != null && cached.size <= limit) return cached
        return repository.getConversation(uid1, uid2, limit).also {
            conversationCache.put(key, it)
            DebugLogger.debug(2) { "私聊会话缓存回填: key=$key count=${it.size} limit=$limit source=db" }
        }
    }

    suspend fun getConversationSummaries(uid: String, limit: Int = 35): List<ChatConversationSummary> {
        conversationSummaryCache.getIfPresent(uid)?.let { return it.take(limit.coerceAtLeast(1)) }
        return repository.getConversationSummaries(uid, limit).also {
            conversationSummaryCache.put(uid, it)
            DebugLogger.debug(2) { "私聊摘要缓存回填: uid=$uid count=${it.size} limit=$limit source=db" }
        }
    }

    fun getConversationSummariesSync(uid: String, limit: Int = 35): List<ChatConversationSummary> {
        conversationSummaryCache.getIfPresent(uid)?.let { return it.take(limit.coerceAtLeast(1)) }
        return repository.getConversationSummariesSync(uid, limit).also {
            conversationSummaryCache.put(uid, it)
            DebugLogger.debug(2) { "私聊摘要缓存回填: uid=$uid count=${it.size} limit=$limit source=db-sync" }
        }
    }

    suspend fun markRead(ids: List<Int>) {
        repository.markAsRead(ids)
        unreadCache.invalidateAll()
        conversationSummaryCache.invalidateAll()
        conversationCache.invalidateAll()
        DebugLogger.debug(1) { "私聊消息已标记已读: count=${ids.size}" }
    }

    suspend fun clearUnreadFromSender(receiver: String, sender: String): Int {
        val changed = repository.markConversationUnreadAsRead(receiver, sender)
        unreadCache.invalidate(receiver)
        conversationSummaryCache.invalidate(receiver)
        conversationCache.invalidate(conversationKey(receiver, sender))
        DebugLogger.debug(1) { "单会话未读已清理: receiver=$receiver sender=$sender count=$changed" }
        return changed
    }

    fun clearUnreadFromSenderSync(receiver: String, sender: String): Int {
        val changed = repository.markConversationUnreadAsReadSync(receiver, sender)
        unreadCache.invalidate(receiver)
        conversationSummaryCache.invalidate(receiver)
        conversationCache.invalidate(conversationKey(receiver, sender))
        DebugLogger.debug(1) { "单会话未读已清理: receiver=$receiver sender=$sender count=$changed source=db-sync" }
        return changed
    }

    suspend fun clearUnread(receiver: String): Int {
        unreadCache.invalidate(receiver)
        conversationSummaryCache.invalidate(receiver)
        return repository.markUnreadAsRead(receiver).also { changed ->
            DebugLogger.debug(1) { "全部未读已清理: receiver=$receiver count=$changed" }
        }
    }

    fun clearUnreadSync(receiver: String): Int {
        unreadCache.invalidate(receiver)
        conversationSummaryCache.invalidate(receiver)
        return repository.markUnreadAsReadSync(receiver).also { changed ->
            DebugLogger.debug(1) { "全部未读已清理: receiver=$receiver count=$changed source=db-sync" }
        }
    }

    suspend fun clearExpiredUnread(threshold: Long) {
        repository.deleteExpiredUnread(threshold)
        unreadCache.invalidateAll()
        conversationSummaryCache.invalidateAll()
        DebugLogger.debug(1) { "过期未读私聊已清理: threshold=$threshold" }
    }

    fun clearExpiredUnreadSync(threshold: Long) {
        repository.deleteExpiredUnreadSync(threshold)
        unreadCache.invalidateAll()
        conversationSummaryCache.invalidateAll()
        DebugLogger.debug(1) { "过期未读私聊已清理: threshold=$threshold source=db-sync" }
    }

    fun unreadCountCached(receiver: String): Int {
        return unreadCache.getIfPresent(receiver)?.size ?: 0
    }

    fun unreadCountSync(receiver: String): Int {
        return unreadCache.getIfPresent(receiver)?.size ?: repository.countUnreadSync(receiver)
    }

    fun unreadCached(receiver: String): List<ChatMessage> {
        return unreadCache.getIfPresent(receiver) ?: emptyList()
    }

    fun conversationCached(uid1: String, uid2: String): List<ChatMessage> {
        return conversationCache.getIfPresent(conversationKey(uid1, uid2)) ?: emptyList()
    }

    fun conversationSummariesCached(uid: String): List<ChatConversationSummary> {
        return conversationSummaryCache.getIfPresent(uid) ?: emptyList()
    }

    fun remainingCooldown(uid: String): Long {
        val cooldown = plugin.config.getLong("messageCooldown", plugin.config.getLong("chat.messageCooldown", 60L)).coerceAtLeast(0L)
        if (cooldown <= 0L) return 0L
        val last = lastSentAt[uid] ?: return 0L
        val remaining = cooldown * 1000L - (System.currentTimeMillis() - last)
        return if (remaining <= 0L) 0L else ((remaining + 999L) / 1000L)
    }

    suspend fun updateUid(oldUid: String, newUid: String) {
        repository.updateUid(oldUid, newUid)
        lastSentAt.remove(oldUid)?.let { lastSentAt[newUid] = it }
        lastConversations.remove(oldUid)?.let { target ->
            lastConversations[newUid] = if (target == oldUid) newUid else target
        }
        lastConversations.replaceAll { _, value ->
            if (value == oldUid) newUid else value
        }
        unreadCache.invalidateAll()
        conversationCache.invalidateAll()
        conversationSummaryCache.invalidateAll()
        DebugLogger.debug(1) { "私聊数据 UID 已迁移: oldUid=$oldUid newUid=$newUid" }
    }

    private fun conversationKey(firstUid: String, secondUid: String): String {
        return if (firstUid <= secondUid) "$firstUid:$secondUid" else "$secondUid:$firstUid"
    }

    fun cachedUnreadOwnerCount(): Int = unreadCache.asMap().size

    fun cachedUnreadMessageCount(): Int = unreadCache.asMap().values.sumOf { it.size }

    fun cachedConversationCount(): Int = conversationCache.asMap().size

    fun cachedConversationSummaryOwnerCount(): Int = conversationSummaryCache.asMap().size

    fun replyTargetCount(): Int = lastConversations.size

    fun cooldownTrackerCount(): Int = lastSentAt.size

    fun invalidate(uid: String) {
        unreadCache.invalidate(uid)
        conversationSummaryCache.invalidate(uid)
        conversationCache.invalidateAll()
        DebugLogger.debug(2) { "私聊缓存已清理: uid=$uid reason=invalidate" }
    }

    private fun invalidateConversationState(sender: String, receiver: String, unreadChanged: Boolean = true) {
        if (unreadChanged) {
            unreadCache.invalidate(receiver)
        }
        conversationCache.invalidate(conversationKey(sender, receiver))
        conversationSummaryCache.invalidate(sender)
        conversationSummaryCache.invalidate(receiver)
        DebugLogger.debug(2) {
            "私聊状态缓存已失效: sender=$sender receiver=$receiver unreadChanged=$unreadChanged"
        }
    }

    private fun String?.normalizedLength(): Int {
        return this?.trim()?.length ?: 0
    }
}
