package org.cyuCBMclean.cyufriendsReload.modules.group

import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendDefaults
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendManager

class GroupManager(private val friendManager: FriendManager) {

    fun groups(userUid: String): Set<String> {
        return friendManager.getGroups(userUid).map(::normalize).toSet()
    }

    fun groupedFriends(userUid: String): Map<String, List<String>> {
        return friendManager.getGroupedFriends(userUid)
            .mapKeys { normalize(it.key) }
            .toSortedMap()
    }

    suspend fun groupedFriendsStored(userUid: String): Map<String, List<String>> {
        return friendManager.getGroupedFriendsStored(userUid)
            .mapKeys { normalize(it.key) }
            .toSortedMap()
    }

    fun friendsInGroup(userUid: String, groupName: String): Set<String> {
        return friendManager.getFriendsInGroup(userUid, normalize(groupName))
    }

    fun friendData(userUid: String, friendUid: String): FriendData? {
        return friendManager.getFriendData(userUid, friendUid)
    }

    suspend fun moveFriend(userUid: String, friendUid: String, groupName: String) {
        val normalizedGroup = normalize(groupName)
        friendManager.setGroup(userUid, friendUid, normalizedGroup)
        DebugLogger.debug(1) { "好友分组移动已执行: owner=$userUid friend=$friendUid groupChars=${normalizedGroup.normalizedLength()}" }
    }

    suspend fun moveGroup(userUid: String, sourceGroup: String, targetGroup: String): Int {
        val normalizedSource = normalize(sourceGroup)
        val normalizedTarget = normalize(targetGroup)
        if (normalizedSource == normalizedTarget) {
            DebugLogger.debug(1) { "好友整组移动已跳过: owner=$userUid reason=same-group groupChars=${normalizedSource.normalizedLength()}" }
            return 0
        }
        val members = friendManager.getFriendsInGroup(userUid, normalizedSource)
        members.forEach { friendUid ->
            friendManager.setGroup(userUid, friendUid, normalizedTarget)
        }
        DebugLogger.debug(1) {
            "好友整组移动已执行: owner=$userUid sourceChars=${normalizedSource.normalizedLength()} targetChars=${normalizedTarget.normalizedLength()} count=${members.size}"
        }
        return members.size
    }

    fun normalize(groupName: String?): String {
        return groupName?.trim()?.takeIf { it.isNotEmpty() } ?: FriendDefaults.DEFAULT_GROUP_NAME
    }

    private fun String?.normalizedLength(): Int {
        return this?.trim()?.length ?: 0
    }
}
