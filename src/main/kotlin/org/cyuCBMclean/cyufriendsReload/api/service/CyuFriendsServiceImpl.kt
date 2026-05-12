package org.cyuCBMclean.cyufriendsReload.api.service

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.command.dispatcher.DispatcherRegistry
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatManager
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatSendResult
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendDefaults
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRequestLimitResult
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialWallSubmitResult
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialWriteResult
import org.cyuCBMclean.cyufriendsReload.modules.social.StatusVisibility
import org.cyuCBMclean.cyufriendsReload.modules.social.WallVisibility
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CompletableFuture

class CyuFriendsServiceImpl(
    private val plugin: CyufriendsReload
) : CyuFriendsService {

    override fun uidByName(name: String): String? = CyuIdHook.getUidByName(name)

    override fun nameByUid(uid: String): String? = CyuIdHook.getName(uid)

    override fun isModuleEnabled(moduleId: String): Boolean = plugin.moduleManager.isEnabled(moduleId)

    override fun areFriends(firstUid: String, secondUid: String): Boolean {
        return friendModule()?.friendManager?.isFriendStable(firstUid, secondUid) == true
    }

    override fun friendsOf(uid: String): List<String> {
        return friendModule()
            ?.friendManager
            ?.getFriendEntriesStoredSync(uid)
            ?.map { it.friendUid }
            ?: emptyList()
    }

    override fun groupedFriends(uid: String): Map<String, List<String>> {
        return friendModule()
            ?.friendManager
            ?.getFriendEntriesStoredSync(uid)
            ?.groupBy { it.groupName }
            ?.mapValues { entry -> entry.value.map { it.friendUid } }
            ?.toSortedMap()
            ?: emptyMap()
    }

    override fun mutualFriends(firstUid: String, secondUid: String): List<String> {
        return friendModule()?.friendManager?.mutualFriendUidsStoredSync(firstUid, secondUid) ?: emptyList()
    }

    override fun recommendations(uid: String, limit: Int): List<RecommendationSnapshot> {
        return friendModule()
            ?.friendManager
            ?.recommendationsStoredSync(uid, limit.coerceAtLeast(1))
            ?.map { RecommendationSnapshot(it.candidateUid, it.mutualCount, it.latestSharedInteractionAt) }
            ?: emptyList()
    }

    override fun friendSnapshot(ownerUid: String, friendUid: String): FriendSnapshot? {
        val data = friendModule()
            ?.friendManager
            ?.getFriendDataStoredSync(ownerUid, friendUid)
            ?: return null
        return FriendSnapshot(
            ownerUid,
            friendUid,
            data.noteName,
            data.noteDetail,
            data.groupName,
            data.primaryTag(),
            data.tagNames.toList(),
            data.tagColors.toMap(),
            data.pinned,
            data.createdAt,
            data.lastInteractionAt
        )
    }

    override fun blockedUsers(uid: String): Set<String> {
        return friendModule()?.blockManager?.getBlocksStoredSync(uid) ?: emptySet()
    }

    override fun incomingRequests(uid: String): List<RequestSnapshot> {
        return friendModule()
            ?.requestManager
            ?.getRequestEntries(uid)
            ?.ifEmpty { runBlocking { friendModule()?.requestManager?.getRequestsFromDbForSync(uid) ?: emptyList() } }
            ?.map { RequestSnapshot(it.senderUid, it.receiverUid, it.note, it.createdAt) }
            ?: emptyList()
    }

    override fun outgoingRequests(uid: String): List<RequestSnapshot> {
        return friendModule()
            ?.requestManager
            ?.getSentRequestEntries(uid)
            ?.ifEmpty { runBlocking { friendModule()?.requestManager?.getSentRequestsFromDbForSync(uid) ?: emptyList() } }
            ?.map { RequestSnapshot(it.senderUid, it.receiverUid, it.note, it.createdAt) }
            ?: emptyList()
    }

    override fun requestCountReceived(uid: String): Int {
        return friendModule()?.requestManager?.countReceivedSync(uid) ?: 0
    }

    override fun requestCountSent(uid: String): Int {
        return friendModule()?.requestManager?.countSentSync(uid) ?: 0
    }

    override fun conversationSummaries(uid: String, limit: Int): List<ConversationSnapshot> {
        return chatManager()
            ?.getConversationSummariesSync(uid, limit.coerceAtLeast(1))
            ?.map { ConversationSnapshot(it.partnerUid, it.latestContent, it.latestAt, it.unreadCount, it.latestSenderUid) }
            ?: emptyList()
    }

    override fun unreadMessages(uid: String): List<ChatMessageSnapshot> {
        return chatManager()
            ?.getUnreadSync(uid)
            ?.map { ChatMessageSnapshot(it.id, it.senderUid, it.receiverUid, it.content, it.timestamp) }
            ?: emptyList()
    }

    override fun unreadMessageCount(uid: String): Int {
        return chatManager()?.unreadCountSync(uid) ?: 0
    }

    override fun profile(uid: String): ProfileSnapshot? {
        val profile = profileModule()?.manager?.getProfileStoredSync(uid) ?: return null
        return ProfileSnapshot(
            uid = uid,
            bio = profile.bio,
            birthday = profile.birthday.takeIf { it != "0000-00-00" && it.isNotBlank() },
            allowRequests = profile.allowRequests,
            allowPrivateMsg = profile.allowPrivateMsg,
            vanishMode = profile.vanishMode,
            onlineScope = plugin.onlineScope(uid),
            serverName = plugin.onlineServerName(uid)
        )
    }

    override fun statuses(ownerUid: String, viewerUid: String, limit: Int): List<StatusSnapshot> {
        return socialModule()
            ?.manager
            ?.getStatusesSync(ownerUid, viewerUid)
            ?.take(limit.coerceAtLeast(1))
            ?.map { StatusSnapshot(it.id, it.uid, it.content, it.visibility.id, it.pinned, it.timestamp) }
            ?: emptyList()
    }

    override fun latestStatus(ownerUid: String, viewerUid: String): StatusSnapshot? {
        return statuses(ownerUid, viewerUid, 1).firstOrNull()
    }

    override fun statusCount(uid: String): Int {
        return socialModule()?.manager?.getStatusCountCached(uid) ?: 0
    }

    override fun wall(ownerUid: String, viewerUid: String, includePending: Boolean, limit: Int): List<WallSnapshot> {
        return socialModule()
            ?.manager
            ?.getWallCommentsSync(ownerUid, viewerUid, includePending)
            ?.take(limit.coerceAtLeast(1))
            ?.map {
                WallSnapshot(
                    it.id,
                    it.ownerUid,
                    it.authorUid,
                    it.content,
                    it.visibility.id,
                    it.approved,
                    it.pinned,
                    it.likeCount,
                    it.commentCount,
                    it.pendingCommentCount,
                    it.timestamp
                )
            }
            ?: emptyList()
    }

    override fun createFriendship(firstUid: String, secondUid: String): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(firstUid, secondUid) ?: return invalidPairResult(firstUid, secondUid)
        val (first, second) = normalized
        if (module.friendManager.isFriendStable(first, second)) return fail(ApiResultCode.ALREADY_FRIENDS)
        runBlocking {
            module.requestManager.removeRequest(first, second)
            module.requestManager.removeRequest(second, first)
            module.friendManager.establishFriendship(first, second)
        }
        invalidateProxy(first, second)
        return ApiResult.success()
    }

    override fun removeFriendship(firstUid: String, secondUid: String): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(firstUid, secondUid) ?: return invalidPairResult(firstUid, secondUid)
        val (first, second) = normalized
        if (!module.friendManager.isFriendStable(first, second)) return fail(ApiResultCode.NOT_FRIENDS)
        runBlocking {
            module.friendManager.severFriendship(first, second)
            module.preferencesManager.clearPersonalBetween(first, second)
        }
        invalidateProxy(first, second)
        return ApiResult.success()
    }

    override fun sendFriendRequest(senderUid: String, receiverUid: String, note: String?): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(senderUid, receiverUid) ?: return invalidPairResult(senderUid, receiverUid)
        val (sender, receiver) = normalized
        if (module.friendManager.isFriendStable(sender, receiver)) return fail(ApiResultCode.ALREADY_FRIENDS)
        if (module.blockManager.isBlockedStable(receiver, sender)) return fail(ApiResultCode.BLOCKED)
        if (module.requestManager.hasRequestStable(sender, receiver) || module.requestManager.hasRequestStable(receiver, sender)) {
            return fail(ApiResultCode.REQUEST_EXISTS)
        }
        val result = runBlocking {
            when (module.requestManager.checkLimit(sender, requestCooldown(), requestDailyLimit(), todayStart())) {
                FriendRequestLimitResult.ALLOWED -> {
                    module.requestManager.addRequest(sender, receiver, normalizeNote(note))
                    ApiResult.success()
                }
                FriendRequestLimitResult.COOLDOWN -> fail(ApiResultCode.COOLDOWN)
                FriendRequestLimitResult.DAILY_LIMIT -> fail(ApiResultCode.LIMIT_REACHED)
            }
        }
        proxyModule()?.gateway?.sendFriendRequestNotify(sender, nameByUid(sender) ?: sender, receiver, normalizeNote(note))
        return result
    }

    override fun acceptFriendRequest(receiverUid: String, senderUid: String): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(receiverUid, senderUid) ?: return invalidPairResult(receiverUid, senderUid)
        val (receiver, sender) = normalized
        if (!module.requestManager.hasRequestStable(sender, receiver)) return fail(ApiResultCode.REQUEST_NOT_FOUND)
        runBlocking {
            module.requestManager.removeRequest(sender, receiver)
            module.requestManager.callAcceptEvent(sender, receiver)
            if (!module.friendManager.isFriendStable(sender, receiver)) {
                module.friendManager.establishFriendship(sender, receiver)
            }
        }
        invalidateProxy(sender, receiver)
        proxyModule()?.gateway?.sendFriendRequestAccepted(sender, nameByUid(receiver) ?: receiver)
        return ApiResult.success()
    }

    override fun denyFriendRequest(receiverUid: String, senderUid: String): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(receiverUid, senderUid) ?: return invalidPairResult(receiverUid, senderUid)
        val (receiver, sender) = normalized
        if (!module.requestManager.hasRequestStable(sender, receiver)) return fail(ApiResultCode.REQUEST_NOT_FOUND)
        runBlocking {
            module.requestManager.removeRequest(sender, receiver)
            module.requestManager.callDenyEvent(sender, receiver)
        }
        invalidateRequestProxy(sender, receiver)
        proxyModule()?.gateway?.sendFriendRequestDenied(sender, nameByUid(receiver) ?: receiver)
        return ApiResult.success()
    }

    override fun revokeFriendRequest(senderUid: String, receiverUid: String): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(senderUid, receiverUid) ?: return invalidPairResult(senderUid, receiverUid)
        val (sender, receiver) = normalized
        if (!module.requestManager.hasRequestStable(sender, receiver)) return fail(ApiResultCode.REQUEST_NOT_FOUND)
        runBlocking {
            module.requestManager.removeRequest(sender, receiver)
            module.requestManager.callRevokeEvent(sender, receiver)
        }
        invalidateRequestProxy(sender, receiver)
        proxyModule()?.gateway?.sendFriendRequestRevoked(receiver, nameByUid(sender) ?: sender)
        return ApiResult.success()
    }

    override fun blockUser(ownerUid: String, targetUid: String): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(ownerUid, targetUid) ?: return invalidPairResult(ownerUid, targetUid)
        val (owner, target) = normalized
        if (module.blockManager.isBlockedStable(owner, target)) return ApiResult.success()
        runBlocking {
            if (module.friendManager.isFriendStable(owner, target)) {
                module.friendManager.severFriendship(owner, target)
                module.preferencesManager.clearPersonalBetween(owner, target)
            }
            module.requestManager.removeRequest(owner, target)
            module.requestManager.removeRequest(target, owner)
            module.blockManager.addBlock(owner, target)
        }
        invalidateProxy(owner, target)
        return ApiResult.success()
    }

    override fun unblockUser(ownerUid: String, targetUid: String): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(ownerUid, targetUid) ?: return invalidPairResult(ownerUid, targetUid)
        val (owner, target) = normalized
        if (!module.blockManager.isBlockedStable(owner, target)) return fail(ApiResultCode.NOT_FOUND)
        runBlocking { module.blockManager.removeBlock(owner, target) }
        invalidateProxy(owner, target)
        return ApiResult.success()
    }

    override fun setFriendNote(ownerUid: String, friendUid: String, note: String?): ApiResult {
        return updateFriendData(ownerUid, friendUid) { owner, friend ->
            setNote(owner, friend, note?.trim()?.take(FriendDefaults.MAX_NOTE_NAME_LENGTH)?.ifBlank { null })
        }
    }

    override fun setFriendNoteDetail(ownerUid: String, friendUid: String, detail: String?): ApiResult {
        return updateFriendData(ownerUid, friendUid) { owner, friend ->
            setNoteDetail(owner, friend, detail?.trim()?.take(FriendDefaults.MAX_NOTE_DETAIL_LENGTH)?.ifBlank { null })
        }
    }

    override fun setFriendGroup(ownerUid: String, friendUid: String, groupName: String): ApiResult {
        val clean = groupName.trim().ifBlank { FriendDefaults.DEFAULT_GROUP_NAME }
        return updateFriendData(ownerUid, friendUid) { owner, friend -> setGroup(owner, friend, clean) }
    }

    override fun setFriendPinned(ownerUid: String, friendUid: String, pinned: Boolean): ApiResult {
        return updateFriendData(ownerUid, friendUid) { owner, friend -> setPinned(owner, friend, pinned) }
    }

    override fun addFriendTag(ownerUid: String, friendUid: String, tag: String): ApiResult {
        val clean = normalizeTag(tag) ?: return fail(ApiResultCode.INVALID_ARGUMENT, "tag is blank")
        return updateFriendData(ownerUid, friendUid) { owner, friend ->
            if (!addTag(owner, friend, clean)) throw ApiFailure(ApiResultCode.FAILED)
        }
    }

    override fun removeFriendTag(ownerUid: String, friendUid: String, tag: String): ApiResult {
        val clean = normalizeTag(tag) ?: return fail(ApiResultCode.INVALID_ARGUMENT, "tag is blank")
        return updateFriendData(ownerUid, friendUid) { owner, friend ->
            if (!removeTag(owner, friend, clean)) throw ApiFailure(ApiResultCode.NOT_FOUND)
        }
    }

    override fun clearFriendTags(ownerUid: String, friendUid: String): ApiResult {
        return updateFriendData(ownerUid, friendUid) { owner, friend -> clearTags(owner, friend) }
    }

    override fun setPrimaryFriendTag(ownerUid: String, friendUid: String, tag: String): ApiResult {
        val clean = normalizeTag(tag) ?: return fail(ApiResultCode.INVALID_ARGUMENT, "tag is blank")
        return updateFriendData(ownerUid, friendUid) { owner, friend ->
            if (!setPrimaryTag(owner, friend, clean)) throw ApiFailure(ApiResultCode.NOT_FOUND)
        }
    }

    override fun setFriendTagColor(ownerUid: String, friendUid: String, tag: String, color: String): ApiResult {
        val cleanTag = normalizeTag(tag) ?: return fail(ApiResultCode.INVALID_ARGUMENT, "tag is blank")
        val cleanColor = color.trim().takeIf { it.isNotBlank() } ?: return fail(ApiResultCode.INVALID_ARGUMENT, "color is blank")
        return updateFriendData(ownerUid, friendUid) { owner, friend ->
            if (!setTagColor(owner, friend, cleanTag, cleanColor)) throw ApiFailure(ApiResultCode.NOT_FOUND)
        }
    }

    override fun clearFriendTagColor(ownerUid: String, friendUid: String, tag: String): ApiResult {
        val cleanTag = normalizeTag(tag) ?: return fail(ApiResultCode.INVALID_ARGUMENT, "tag is blank")
        return updateFriendData(ownerUid, friendUid) { owner, friend ->
            if (!clearTagColor(owner, friend, cleanTag)) throw ApiFailure(ApiResultCode.NOT_FOUND)
        }
    }

    override fun sendPrivateMessage(senderUid: String, receiverUid: String, content: String, markRead: Boolean): ApiResult {
        val manager = chatManager() ?: return moduleDisabled("chat")
        val normalized = validPair(senderUid, receiverUid) ?: return invalidPairResult(senderUid, receiverUid)
        val (sender, receiver) = normalized
        return when (manager.sendPlayerMessageSync(sender, receiver, content, markRead)) {
            ChatSendResult.SUCCESS -> ApiResult.success()
            ChatSendResult.EMPTY -> fail(ApiResultCode.EMPTY_CONTENT)
            ChatSendResult.COOLDOWN -> fail(ApiResultCode.COOLDOWN)
        }
    }

    override fun clearUnreadMessages(receiverUid: String, senderUid: String?): ApiResult {
        val manager = chatManager() ?: return moduleDisabled("chat")
        val receiver = receiverUid.trim()
        if (receiver.isBlank()) return fail(ApiResultCode.INVALID_ARGUMENT, "receiver uid is blank")
        val changed = senderUid?.trim()?.takeIf { it.isNotBlank() }
            ?.let { manager.clearUnreadFromSenderSync(receiver, it) }
            ?: manager.clearUnreadSync(receiver)
        return ApiResult.success(changed.toString())
    }

    override fun publishStatus(player: Player, uid: String, content: String, visibility: String): ApiResult {
        val manager = socialModule()?.manager ?: return moduleDisabled("social")
        val parsed = StatusVisibility.fromValue(visibility) ?: return fail(ApiResultCode.INVALID_ARGUMENT, "unknown visibility")
        return when (manager.publishStatusSync(player, uid.trim(), content, parsed)) {
            SocialWriteResult.SUCCESS -> ApiResult.success()
            SocialWriteResult.EMPTY -> fail(ApiResultCode.EMPTY_CONTENT)
            SocialWriteResult.COOLDOWN -> fail(ApiResultCode.COOLDOWN)
        }
    }

    override fun postWall(player: Player, ownerUid: String, authorUid: String, content: String, visibility: String): ApiResult {
        val manager = socialModule()?.manager ?: return moduleDisabled("social")
        val parsed = WallVisibility.fromValue(visibility) ?: return fail(ApiResultCode.INVALID_ARGUMENT, "unknown visibility")
        return when (manager.postWallCommentSync(player, ownerUid.trim(), authorUid.trim(), content, parsed)) {
            SocialWallSubmitResult.SUCCESS -> ApiResult.success()
            SocialWallSubmitResult.PENDING -> ApiResult.pending()
            SocialWallSubmitResult.EMPTY -> fail(ApiResultCode.EMPTY_CONTENT)
            SocialWallSubmitResult.COOLDOWN -> fail(ApiResultCode.COOLDOWN)
        }
    }

    override fun friendsOfAsync(uid: String): CompletableFuture<List<String>> {
        return async { friendsOf(uid) }
    }

    override fun createFriendshipAsync(firstUid: String, secondUid: String): CompletableFuture<ApiResult> {
        return async { createFriendship(firstUid, secondUid) }
    }

    override fun removeFriendshipAsync(firstUid: String, secondUid: String): CompletableFuture<ApiResult> {
        return async { removeFriendship(firstUid, secondUid) }
    }

    override fun sendFriendRequestAsync(senderUid: String, receiverUid: String, note: String?): CompletableFuture<ApiResult> {
        return async { sendFriendRequest(senderUid, receiverUid, note) }
    }

    override fun acceptFriendRequestAsync(receiverUid: String, senderUid: String): CompletableFuture<ApiResult> {
        return async { acceptFriendRequest(receiverUid, senderUid) }
    }

    override fun sendPrivateMessageAsync(
        senderUid: String,
        receiverUid: String,
        content: String,
        markRead: Boolean
    ): CompletableFuture<ApiResult> {
        return async { sendPrivateMessage(senderUid, receiverUid, content, markRead) }
    }

    override fun publishStatusAsync(
        player: Player,
        uid: String,
        content: String,
        visibility: String
    ): CompletableFuture<ApiResult> {
        return async { publishStatus(player, uid, content, visibility) }
    }

    override fun postWallAsync(
        player: Player,
        ownerUid: String,
        authorUid: String,
        content: String,
        visibility: String
    ): CompletableFuture<ApiResult> {
        return async { postWall(player, ownerUid, authorUid, content, visibility) }
    }

    override fun rebuildCaches(uid: String): Boolean {
        val friendModule = friendModule() ?: return false
        val profileModule = profileModule()
        val socialModule = socialModule()

        friendModule.friendManager.invalidate(uid)
        friendModule.requestManager.invalidate(uid)
        friendModule.blockManager.invalidate(uid)
        friendModule.preferencesManager.invalidate(uid)
        profileModule?.manager?.invalidate(uid)
        socialModule?.manager?.invalidateStatusCache(uid)
        socialModule?.manager?.invalidateWallCache(uid)

        if (!CyuIdHook.isOnlineLocally(uid)) return true
        friendModule.friendManager.loadPlayerSync(uid)
        friendModule.requestManager.loadPlayerSync(uid)
        friendModule.blockManager.loadPlayerSync(uid)
        friendModule.preferencesManager.loadPlayerSync(uid)
        profileModule?.manager?.loadProfileSync(uid)
        return true
    }

    override fun rebuildCachesAsync(uid: String): CompletableFuture<Boolean> {
        return async { rebuildCaches(uid) }
    }

    override fun registeredCommands(): List<CommandSnapshot> {
        return DispatcherRegistry.all().map { CommandSnapshot(it.rootName, it.subCommands) }
    }

    private fun friendModule(): FriendModule? = plugin.moduleManager.getModule<FriendModule>("friend")

    private fun chatManager(): ChatManager? = plugin.moduleManager.getModule<ChatModule>("chat")?.manager

    private fun profileModule(): ProfileModule? = plugin.moduleManager.getModule<ProfileModule>("profile")

    private fun socialModule(): SocialModule? = plugin.moduleManager.getModule<SocialModule>("social")

    private fun proxyModule(): ProxyModule? = plugin.moduleManager.getModule<ProxyModule>("proxy")

    private fun moduleDisabled(moduleId: String): ApiResult = fail(ApiResultCode.MODULE_DISABLED, "$moduleId module is disabled")

    private fun fail(code: ApiResultCode, message: String = "", value: String? = null): ApiResult {
        return ApiResult.fail(code, message, value)
    }

    private fun validPair(firstUid: String, secondUid: String): Pair<String, String>? {
        val first = firstUid.trim()
        val second = secondUid.trim()
        if (first.isBlank() || second.isBlank() || first == second) return null
        return first to second
    }

    private fun invalidPairResult(firstUid: String, secondUid: String): ApiResult {
        return if (firstUid.trim() == secondUid.trim() && firstUid.isNotBlank()) {
            fail(ApiResultCode.SAME_PLAYER)
        } else {
            fail(ApiResultCode.INVALID_ARGUMENT, "uid is blank")
        }
    }

    private fun updateFriendData(
        ownerUid: String,
        friendUid: String,
        update: suspend org.cyuCBMclean.cyufriendsReload.modules.friend.FriendManager.(String, String) -> Unit
    ): ApiResult {
        val module = friendModule() ?: return moduleDisabled("friend")
        val normalized = validPair(ownerUid, friendUid) ?: return invalidPairResult(ownerUid, friendUid)
        val (owner, friend) = normalized
        if (!module.friendManager.isFriendStable(owner, friend)) return fail(ApiResultCode.NOT_FRIENDS)
        return try {
            runBlocking { module.friendManager.update(owner, friend) }
            invalidateSettingsProxy(owner, friend)
            ApiResult.success()
        } catch (failure: ApiFailure) {
            fail(failure.code)
        }
    }

    private fun normalizeTag(tag: String): String? {
        return tag.trim().takeIf { it.isNotBlank() }?.take(FriendDefaults.MAX_TAG_LENGTH)
    }

    private fun normalizeNote(note: String?): String? {
        val max = plugin.config.getInt("requestNotes.max-length", 48).coerceAtLeast(1)
        return note?.trim()?.take(max)?.ifBlank { null }
    }

    private fun requestCooldown(): Long {
        return plugin.config.getLong(
            "requestLimits.default.cooldown",
            plugin.config.getLong("settings.request-cooldown", 60L)
        ).coerceAtLeast(0L)
    }

    private fun requestDailyLimit(): Int {
        return plugin.config.getInt("requestLimits.default.daily", 20).coerceAtLeast(0)
    }

    private fun todayStart(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun invalidateProxy(firstUid: String, secondUid: String) {
        proxyModule()?.gateway?.invalidateRelation(firstUid, secondUid)
        proxyModule()?.gateway?.invalidateRequest(firstUid, secondUid)
        proxyModule()?.gateway?.invalidateSettings(firstUid, secondUid)
    }

    private fun invalidateRequestProxy(firstUid: String, secondUid: String) {
        proxyModule()?.gateway?.invalidateRequest(firstUid, secondUid)
    }

    private fun invalidateSettingsProxy(firstUid: String, secondUid: String) {
        proxyModule()?.gateway?.invalidateSettings(firstUid, secondUid)
    }

    private fun <T> async(block: () -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(block)
    }

    private class ApiFailure(val code: ApiResultCode) : RuntimeException()
}
