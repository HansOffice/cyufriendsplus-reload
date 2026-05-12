package org.cyuCBMclean.cyufriendsReload.ui.view

object ViewTitles {

    fun unreadMessages() = "§b§l消息会话 §f| §7列表"

    fun birthdays() = "§e§l好友生日 §f| §7近期提醒"

    fun friendGroups() = "§b§l好友分组 §f| §7列表"

    fun groupMembers(groupName: String) = "§b§l$groupName §f| §7成员"

    fun profileHome(playerName: String) = "§b§l个人主页 §f| §7$playerName"

    fun friendsList(filterTag: String? = null) =
        if (filterTag.isNullOrBlank()) "§b§l我的好友 §f| §7列表" else "§b§l标签筛选 §f| §7$filterTag"

    fun friendTagFilters(currentFilter: String? = null) =
        if (currentFilter.isNullOrBlank()) "§b§l标签筛选 §f| §7选择标签" else "§b§l标签筛选 §f| §7$currentFilter"

    fun friendTagManage(targetName: String) = "§b§l标签管理 §f| §7$targetName"

    fun friendTagColors(targetName: String, tagName: String) = "§b§l标签颜色 §f| §7$targetName · $tagName"

    fun requestsList() = "§b§l好友申请 §f| §7列表"

    fun sentRequestsList() = "§b§l发出申请 §f| §7列表"

    fun friendProfile(targetName: String) = "§b§l好友资料 §f| §7$targetName"

    fun friendProfileDetails(targetName: String) = "§b§l好友详情 §f| §7$targetName"

    fun friendProfileSocial(targetName: String) = "§b§l特别提醒 §f| §7$targetName"

    fun friendTimeline(targetName: String) = "§b§l关系时间线 §f| §7$targetName"

    fun friendRemoveConfirm(targetName: String) = "§c§l确认删除 §f| §7$targetName"

    fun groupMove(targetName: String) = "§b§l移动分组 §f| §7$targetName"

    fun privateChat(targetName: String) = "§b§l私聊会话 §f| §7$targetName"

    fun blacklist() = "§8§l黑名单 §f| §7列表"

    fun onlinePlayers() = "§a§l在线玩家 §f| §7发现"

    fun addFriend(targetName: String) = "§b§l添加好友 §f| §7$targetName"

    fun settings() = "§b§l个人设置 §f| §7偏好"

    fun socialSettings() = "§b§l互动提醒 §f| §7设置"

    fun notificationCenter() = "§e§l通知中心 §f| §7总览"

    fun friendRecommendations() = "§a§l推荐好友 §f| §7发现"

    fun statusFeed() = "§b§l朋友动态 §f| §7全服动态"

    fun myStatus(playerName: String) = "§b§l我的动态 §f| §7$playerName"

    fun statusOf(targetName: String) = "§b§l$targetName §f| §7动态"

    fun wall(targetName: String) = "§b§l留言墙 §f| §7$targetName"

    fun wallPending(targetName: String) = "§b§l待审留言 §f| §7$targetName"

    fun wallCommentPending(wallId: Int) = "§b§l待审评论 §f| §7#$wallId"

    fun wallComments(wallId: Int) = "§b§l评论列表 §f| §7#$wallId"

    fun statusComments(statusId: Int) = "§b§l动态评论 §f| §7#$statusId"

    fun groupRules(groupName: String) = "§b§l分组规则 §f| §7$groupName"

    fun groupBatchMove(groupName: String) = "§b§l批量移动 §f| §7$groupName"
}
