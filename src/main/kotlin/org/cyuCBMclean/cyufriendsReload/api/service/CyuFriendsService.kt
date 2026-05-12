package org.cyuCBMclean.cyufriendsReload.api.service

import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

interface CyuFriendsService {
    fun uidByName(name: String): String?
    fun nameByUid(uid: String): String?
    fun isModuleEnabled(moduleId: String): Boolean

    fun areFriends(firstUid: String, secondUid: String): Boolean
    fun friendsOf(uid: String): List<String>
    fun groupedFriends(uid: String): Map<String, List<String>>
    fun mutualFriends(firstUid: String, secondUid: String): List<String>
    fun recommendations(uid: String, limit: Int = 21): List<RecommendationSnapshot>
    fun friendSnapshot(ownerUid: String, friendUid: String): FriendSnapshot?
    fun blockedUsers(uid: String): Set<String>
    fun incomingRequests(uid: String): List<RequestSnapshot>
    fun outgoingRequests(uid: String): List<RequestSnapshot>
    fun requestCountReceived(uid: String): Int
    fun requestCountSent(uid: String): Int
    fun conversationSummaries(uid: String, limit: Int = 35): List<ConversationSnapshot>
    fun unreadMessages(uid: String): List<ChatMessageSnapshot>
    fun unreadMessageCount(uid: String): Int
    fun profile(uid: String): ProfileSnapshot?
    fun statuses(ownerUid: String, viewerUid: String = ownerUid, limit: Int = 20): List<StatusSnapshot>
    fun latestStatus(ownerUid: String, viewerUid: String = ownerUid): StatusSnapshot?
    fun statusCount(uid: String): Int
    fun wall(ownerUid: String, viewerUid: String = ownerUid, includePending: Boolean = false, limit: Int = 20): List<WallSnapshot>

    fun createFriendship(firstUid: String, secondUid: String): ApiResult
    fun removeFriendship(firstUid: String, secondUid: String): ApiResult
    fun sendFriendRequest(senderUid: String, receiverUid: String, note: String? = null): ApiResult
    fun acceptFriendRequest(receiverUid: String, senderUid: String): ApiResult
    fun denyFriendRequest(receiverUid: String, senderUid: String): ApiResult
    fun revokeFriendRequest(senderUid: String, receiverUid: String): ApiResult
    fun blockUser(ownerUid: String, targetUid: String): ApiResult
    fun unblockUser(ownerUid: String, targetUid: String): ApiResult

    fun setFriendNote(ownerUid: String, friendUid: String, note: String?): ApiResult
    fun setFriendNoteDetail(ownerUid: String, friendUid: String, detail: String?): ApiResult
    fun setFriendGroup(ownerUid: String, friendUid: String, groupName: String): ApiResult
    fun setFriendPinned(ownerUid: String, friendUid: String, pinned: Boolean): ApiResult
    fun addFriendTag(ownerUid: String, friendUid: String, tag: String): ApiResult
    fun removeFriendTag(ownerUid: String, friendUid: String, tag: String): ApiResult
    fun clearFriendTags(ownerUid: String, friendUid: String): ApiResult
    fun setPrimaryFriendTag(ownerUid: String, friendUid: String, tag: String): ApiResult
    fun setFriendTagColor(ownerUid: String, friendUid: String, tag: String, color: String): ApiResult
    fun clearFriendTagColor(ownerUid: String, friendUid: String, tag: String): ApiResult

    fun sendPrivateMessage(senderUid: String, receiverUid: String, content: String, markRead: Boolean = false): ApiResult
    fun clearUnreadMessages(receiverUid: String, senderUid: String? = null): ApiResult
    fun publishStatus(player: Player, uid: String, content: String, visibility: String = "PUBLIC"): ApiResult
    fun postWall(player: Player, ownerUid: String, authorUid: String, content: String, visibility: String = "PUBLIC"): ApiResult

    fun friendsOfAsync(uid: String): CompletableFuture<List<String>>
    fun createFriendshipAsync(firstUid: String, secondUid: String): CompletableFuture<ApiResult>
    fun removeFriendshipAsync(firstUid: String, secondUid: String): CompletableFuture<ApiResult>
    fun sendFriendRequestAsync(senderUid: String, receiverUid: String, note: String? = null): CompletableFuture<ApiResult>
    fun acceptFriendRequestAsync(receiverUid: String, senderUid: String): CompletableFuture<ApiResult>
    fun sendPrivateMessageAsync(senderUid: String, receiverUid: String, content: String, markRead: Boolean = false): CompletableFuture<ApiResult>
    fun publishStatusAsync(player: Player, uid: String, content: String, visibility: String = "PUBLIC"): CompletableFuture<ApiResult>
    fun postWallAsync(player: Player, ownerUid: String, authorUid: String, content: String, visibility: String = "PUBLIC"): CompletableFuture<ApiResult>

    fun rebuildCaches(uid: String): Boolean
    fun rebuildCachesAsync(uid: String): CompletableFuture<Boolean>
    fun registeredCommands(): List<CommandSnapshot>
}
