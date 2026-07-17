package org.cyuCBMclean.cyufriendsReload.modules.profile.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatConversationSummary
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRecommendation
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRequestEntry
import org.cyuCBMclean.cyufriendsReload.modules.profile.BirthdayReminderEntry
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.social.PendingWallReplyEntry
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialInteractionNoticeType
import org.cyuCBMclean.cyufriendsReload.modules.social.WallEntry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

enum class NotificationTaskType {
    RECEIVED_REQUEST,
    SENT_REQUEST,
    UNREAD_CONVERSATION,
    PENDING_WALL,
    PENDING_WALL_REPLY,
    BIRTHDAY,
    RECOMMENDATION
}

data class NotificationTask(
    val type: NotificationTaskType,
    val sortTime: Long,
    val uid: String? = null,
    val label: String,
    val preview: String,
    val state: String,
    val actionHint: String,
    val request: FriendRequestEntry? = null,
    val conversation: ChatConversationSummary? = null,
    val wallEntry: WallEntry? = null,
    val pendingReply: PendingWallReplyEntry? = null,
    val birthday: BirthdayReminderEntry? = null,
    val recommendation: FriendRecommendation? = null
)

class NotificationCenterView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val plugin: CyufriendsReload,
    private val profileModule: ProfileModule,
    title: String
) : PaginatedView<NotificationTask>(player, title, pattern, itemsMap, 'E', 'P', 'N') {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm")
    private var cachedTasks: List<NotificationTask> = emptyList()

    override fun onRender() {
        super.onRender()
        if (!plugin.moduleManager.isEnabled("friend")) {
            hideStaticSymbols('R', 'Y')
        }
        if (!plugin.moduleManager.isEnabled("chat")) {
            hideStaticSymbol('M')
        }
        if (!plugin.moduleManager.isEnabled("social")) {
            hideStaticSymbol('W')
        }
    }

    override suspend fun prepareData() {
        cachedTasks = loadTasks()
    }

    override fun getSource(): List<NotificationTask> = cachedTasks

    private fun loadTasks(): List<NotificationTask> {
        val uid = player.uid
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
        val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
        val friendEntries = friendModule?.friendManager?.getFriendEntriesCached(uid).orEmpty()
        val friendUids = friendEntries.mapTo(linkedSetOf(), org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData::friendUid)

        val tasks = buildList {
            if (friendModule != null) {
                val requests = friendModule.requestManager.getRequestEntries(uid).ifEmpty {
                    runBlocking { friendModule.requestManager.getRequestsFromDbForSync(uid) }
                }
                val sentRequests = friendModule.requestManager.getSentRequestEntries(uid).ifEmpty {
                    runBlocking { friendModule.requestManager.getSentRequestsFromDbForSync(uid) }
                }
                val recommendations = friendModule.friendManager.recommendationsStoredSync(uid, 12)
                val birthdays = profileModule.manager.birthdayEntriesSync(friendUids).take(10)

                requests.forEach { request ->
                    val senderName = CyuIdHook.getName(request.senderUid) ?: request.senderUid
                    add(
                        NotificationTask(
                            type = NotificationTaskType.RECEIVED_REQUEST,
                            sortTime = request.createdAt,
                            uid = request.senderUid,
                            label = "收到申请 · $senderName",
                            preview = request.note?.takeIf { it.isNotBlank() } ?: "这位玩家想添加你为好友",
                            state = timeFormat.format(Date(request.createdAt)),
                            actionHint = "左键同意 / 右键拒绝",
                            request = request
                        )
                    )
                }
                sentRequests.forEach { request ->
                    val receiverName = CyuIdHook.getName(request.receiverUid) ?: request.receiverUid
                    add(
                        NotificationTask(
                            type = NotificationTaskType.SENT_REQUEST,
                            sortTime = request.createdAt,
                            uid = request.receiverUid,
                            label = "发出申请 · $receiverName",
                            preview = request.note?.takeIf { it.isNotBlank() } ?: "等待对方处理",
                            state = timeFormat.format(Date(request.createdAt)),
                            actionHint = "左键撤回 / 右键联系",
                            request = request
                        )
                    )
                }
                birthdays.forEach { birthday ->
                    val friendName = CyuIdHook.getName(birthday.uid) ?: birthday.uid
                    add(
                        NotificationTask(
                            type = NotificationTaskType.BIRTHDAY,
                            sortTime = Long.MAX_VALUE - birthday.daysAhead,
                            uid = birthday.uid,
                            label = "生日提醒 · $friendName",
                            preview = birthday.birthday,
                            state = if (birthday.daysAhead == 0) "今天生日" else "${birthday.daysAhead} 天后生日",
                            actionHint = "左键资料 / 右键联系",
                            birthday = birthday
                        )
                    )
                }
                recommendations.forEach { recommendation ->
                    val candidateName = CyuIdHook.getName(recommendation.candidateUid) ?: recommendation.candidateUid
                    val mutualPreview = friendModule.friendManager.mutualFriendUidsCached(uid, recommendation.candidateUid)
                        .take(2)
                        .map { CyuIdHook.getName(it) ?: it }
                        .joinToString("、")
                        .ifBlank { "暂无共同好友预览" }
                    add(
                        NotificationTask(
                            type = NotificationTaskType.RECOMMENDATION,
                            sortTime = recommendation.latestSharedInteractionAt,
                            uid = recommendation.candidateUid,
                            label = "推荐好友 · $candidateName",
                            preview = "${recommendation.mutualCount} 位共同好友：$mutualPreview",
                            state = recommendationReason(recommendation),
                            actionHint = "左键联系 / 右键添加 / Shift 右键暂不推荐",
                            recommendation = recommendation
                        )
                    )
                }
            }
            if (chatModule != null) {
                chatModule.manager.getConversationSummariesSync(uid, 16)
                    .filter { it.unreadCount > 0 }
                    .forEach { conversation ->
                        val partnerName = CyuIdHook.getName(conversation.partnerUid) ?: conversation.partnerUid
                        add(
                            NotificationTask(
                                type = NotificationTaskType.UNREAD_CONVERSATION,
                                sortTime = conversation.latestAt,
                                uid = conversation.partnerUid,
                                label = "未读会话 · $partnerName",
                                preview = preview(conversation.latestContent),
                                state = "${conversation.unreadCount} 条未读",
                                actionHint = "左键打开 / 右键已读",
                                conversation = conversation
                            )
                        )
                    }
            }
            if (socialModule != null) {
                socialModule.manager.getPendingWallEntriesSync(uid)
                    .take(6)
                    .forEach { wall ->
                        val authorName = CyuIdHook.getName(wall.authorUid) ?: wall.authorUid
                        add(
                            NotificationTask(
                                type = NotificationTaskType.PENDING_WALL,
                                sortTime = wall.timestamp,
                                uid = wall.authorUid,
                                label = "待审留言 · $authorName",
                                preview = preview(wall.content),
                                state = "留言 #${wall.id}",
                                actionHint = "左键通过 / 右键拒绝",
                                wallEntry = wall
                            )
                        )
                    }
                socialModule.manager.recentPendingRepliesSync(uid, 6)
                    .forEach { reply ->
                        val authorName = CyuIdHook.getName(reply.authorUid) ?: reply.authorUid
                        add(
                            NotificationTask(
                                type = NotificationTaskType.PENDING_WALL_REPLY,
                                sortTime = reply.timestamp,
                                uid = reply.authorUid,
                                label = "待审评论 · $authorName",
                                preview = preview(reply.content),
                                state = "评论 #${reply.id} / 留言 #${reply.wallId}",
                                actionHint = "左键通过 / 右键拒绝",
                                pendingReply = reply
                            )
                        )
                    }
            }
        }.sortedWith(
            compareByDescending<NotificationTask> { priority(it.type) }
                .thenByDescending { it.sortTime }
                .thenBy { it.label }
        )

        cachedTasks = tasks
        return tasks
    }

    override fun viewReplacements(): Map<String, String> {
        val uid = player.uid
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
        val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
        val receivedRequests = friendModule?.requestManager?.countReceivedSync(uid) ?: 0
        val sentRequests = friendModule?.requestManager?.countSentSync(uid) ?: 0
        val unreadMessages = chatModule?.manager?.unreadCountSync(uid) ?: 0
        val pendingWalls = socialModule?.manager?.pendingWallCountSync(uid) ?: 0
        val pendingReplies = socialModule?.manager?.pendingWallReplyCountSync(uid) ?: 0
        val recommendCount = cachedTasks.count { it.type == NotificationTaskType.RECOMMENDATION }
        val friendUids = friendModule?.friendManager?.getFriendEntriesCached(uid)?.map { it.friendUid }?.toSet() ?: emptySet()
        val birthdayCounts = profileModule.manager.birthdayReminderCountsSync(friendUids)
        val socialSummary = if (socialModule == null) {
            "模块已关闭"
        } else {
            val enabledCount = listOf(
                profileModule.manager.canReceiveSocialNoticeSync(uid, SocialInteractionNoticeType.STATUS_LIKE),
                profileModule.manager.canReceiveSocialNoticeSync(uid, SocialInteractionNoticeType.STATUS_COMMENT),
                profileModule.manager.canReceiveSocialNoticeSync(uid, SocialInteractionNoticeType.WALL_POST),
                profileModule.manager.canReceiveSocialNoticeSync(uid, SocialInteractionNoticeType.WALL_LIKE),
                profileModule.manager.canReceiveSocialNoticeSync(uid, SocialInteractionNoticeType.WALL_COMMENT)
            ).count { it }
            "$enabledCount/5 已开启"
        }

        return mapOf(
            "%request_count%" to receivedRequests.toString(),
            "%sent_request_count%" to sentRequests.toString(),
            "%unread_count%" to unreadMessages.toString(),
            "%pending_wall_count%" to pendingWalls.toString(),
            "%pending_reply_count%" to pendingReplies.toString(),
            "%recommend_count%" to recommendCount.toString(),
            "%birthday_today_count%" to birthdayCounts.today.toString(),
            "%birthday_upcoming_count%" to birthdayCounts.upcoming.toString(),
            "%social_notice_summary%" to socialSummary,
            "%task_count%" to cachedTasks.size.toString()
        )
    }

    override fun mapElement(element: NotificationTask): ItemStack {
        val template = itemsMap['E'] ?: return ItemStack(Material.PLAYER_HEAD)
        val replacements = mapOf(
            "%task_type%" to taskTypeName(element.type),
            "%task_label%" to element.label,
            "%task_state%" to element.state,
            "%task_hint%" to element.actionHint,
            "%task_uid%" to (element.uid ?: player.uid)
        )
        val baseItem = template.render(player, replacements).clone()
        val meta = baseItem.itemMeta ?: return baseItem
        if (meta.hasLore()) {
            meta.lore = meta.lore?.map { it.replace("%task_preview%", GuiTextFormatter.renderUserText(element.preview)) }
            baseItem.itemMeta = meta
        }
        val targetUid = element.uid
        return if (template.hasHeadSource() || targetUid == null) baseItem else GuiHeads.applyForUid(baseItem, targetUid, player)
    }

    override fun onElementClick(element: NotificationTask, clickType: CyuClickType) {
        when (element.type) {
            NotificationTaskType.RECEIVED_REQUEST -> handleReceivedRequest(element, clickType)
            NotificationTaskType.SENT_REQUEST -> handleSentRequest(element, clickType)
            NotificationTaskType.UNREAD_CONVERSATION -> handleConversation(element, clickType)
            NotificationTaskType.PENDING_WALL -> handlePendingWall(element, clickType)
            NotificationTaskType.PENDING_WALL_REPLY -> handlePendingReply(element, clickType)
            NotificationTaskType.BIRTHDAY -> handleBirthday(element, clickType)
            NotificationTaskType.RECOMMENDATION -> handleRecommendation(element, clickType)
        }
    }

    private fun handleReceivedRequest(task: NotificationTask, clickType: CyuClickType) {
        val request = task.request ?: return
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("friend accept ${request.senderUid}")
            CyuClickType.RIGHT -> player.performCommand("friend deny ${request.senderUid}")
            else -> player.performCommand("friend profile ${CyuIdHook.getName(request.senderUid) ?: request.senderUid}")
        }
    }

    private fun handleSentRequest(task: NotificationTask, clickType: CyuClickType) {
        val request = task.request ?: return
        val receiverName = CyuIdHook.getName(request.receiverUid) ?: request.receiverUid
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("friend revoke ${request.receiverUid}")
            else -> player.performCommand("friend contact ${request.receiverUid} $receiverName")
        }
    }

    private fun handleConversation(task: NotificationTask, clickType: CyuClickType) {
        val conversation = task.conversation ?: return
        val partnerName = CyuIdHook.getName(conversation.partnerUid) ?: conversation.partnerUid
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("friend chat $partnerName")
            CyuClickType.RIGHT -> {
                val chatModule = plugin.moduleManager.getModule<ChatModule>("chat") ?: return
                val receiverUid = player.uid
                CyuConcurrency.scheduler.runAsync(plugin) {
                    val changed = chatModule.manager.clearUnreadFromSenderSync(receiverUid, conversation.partnerUid)
                    CyuConcurrency.scheduler.runEntity(plugin, player) {
                        if (changed > 0) {
                            player.sendLang(
                                "messages-conversation-read",
                                mapOf("target" to partnerName, "amount" to changed.toString())
                            )
                        } else {
                            player.sendLang("messages-conversation-read-empty", mapOf("target" to partnerName))
                        }
                        onRender()
                    }
                }
            }
            else -> player.performCommand("friend profile $partnerName")
        }
    }

    private fun handlePendingWall(task: NotificationTask, clickType: CyuClickType) {
        val wall = task.wallEntry ?: return
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("wall approve ${wall.id}")
            CyuClickType.RIGHT -> player.performCommand("wall reject ${wall.id}")
            else -> player.performCommand("wall pending ${player.name}")
        }
    }

    private fun handlePendingReply(task: NotificationTask, clickType: CyuClickType) {
        val reply = task.pendingReply ?: return
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("wall commentapprove ${reply.id}")
            CyuClickType.RIGHT -> player.performCommand("wall commentreject ${reply.id}")
            else -> player.performCommand("wall commentpending ${reply.wallId}")
        }
    }

    private fun handleBirthday(task: NotificationTask, clickType: CyuClickType) {
        val birthday = task.birthday ?: return
        val friendName = CyuIdHook.getName(birthday.uid) ?: birthday.uid
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("friend profile $friendName")
            CyuClickType.RIGHT -> player.performCommand("friend contact ${birthday.uid} $friendName")
            else -> {
                if (plugin.moduleManager.isEnabled("social")) {
                    player.performCommand("wall $friendName")
                } else {
                    player.performCommand("friend profile $friendName")
                }
            }
        }
    }

    private fun handleRecommendation(task: NotificationTask, clickType: CyuClickType) {
        val recommendation = task.recommendation ?: return
        val candidateName = CyuIdHook.getName(recommendation.candidateUid) ?: recommendation.candidateUid
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("friend contact ${recommendation.candidateUid} $candidateName")
            CyuClickType.RIGHT -> player.performCommand("friend addgui $candidateName")
            CyuClickType.SHIFT_RIGHT -> {
                val days = plugin.config.getLong("recommendation.snooze-days", 14L).coerceAtLeast(1L)
                val expiresAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: return
                val ownerUid = player.uid
                CyuConcurrency.scheduler.runAsync(plugin) {
                    friendModule.friendManager.ignoreRecommendationSync(ownerUid, recommendation.candidateUid, expiresAt)
                    CyuConcurrency.scheduler.runEntity(plugin, player) {
                        player.sendLang("recommend-snoozed", mapOf("target" to candidateName, "days" to days.toString()))
                        onRender()
                    }
                }
            }
            CyuClickType.DOUBLE_CLICK -> {
                val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: return
                val ownerUid = player.uid
                CyuConcurrency.scheduler.runAsync(plugin) {
                    friendModule.friendManager.ignoreRecommendationSync(ownerUid, recommendation.candidateUid, 0L)
                    CyuConcurrency.scheduler.runEntity(plugin, player) {
                        player.sendLang("recommend-hidden", mapOf("target" to candidateName))
                        onRender()
                    }
                }
            }
            else -> player.performCommand("friend profile $candidateName")
        }
    }

    private fun taskTypeName(type: NotificationTaskType): String {
        return when (type) {
            NotificationTaskType.RECEIVED_REQUEST -> "收到申请"
            NotificationTaskType.SENT_REQUEST -> "发出申请"
            NotificationTaskType.UNREAD_CONVERSATION -> "未读会话"
            NotificationTaskType.PENDING_WALL -> "待审留言"
            NotificationTaskType.PENDING_WALL_REPLY -> "待审评论"
            NotificationTaskType.BIRTHDAY -> "生日提醒"
            NotificationTaskType.RECOMMENDATION -> "推荐好友"
        }
    }

    private fun recommendationReason(recommendation: FriendRecommendation): String {
        return when {
            recommendation.mutualCount >= 3 -> "共同好友很多"
            recommendation.mutualCount == 2 -> "你们有 2 位共同好友"
            else -> "你们有共同好友"
        }
    }

    private fun preview(content: String): String {
        val clean = content.trim()
        if (clean.isEmpty()) return "暂无预览"
        return if (clean.length <= 28) clean else clean.take(28) + "..."
    }

    private fun priority(type: NotificationTaskType): Int {
        return when (type) {
            NotificationTaskType.RECEIVED_REQUEST -> 7
            NotificationTaskType.PENDING_WALL_REPLY -> 6
            NotificationTaskType.PENDING_WALL -> 5
            NotificationTaskType.UNREAD_CONVERSATION -> 4
            NotificationTaskType.BIRTHDAY -> 3
            NotificationTaskType.RECOMMENDATION -> 2
            NotificationTaskType.SENT_REQUEST -> 1
        }
    }
}
