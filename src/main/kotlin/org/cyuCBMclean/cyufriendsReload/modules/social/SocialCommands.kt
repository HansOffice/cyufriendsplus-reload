package org.cyuCBMclean.cyufriendsReload.modules.social

import org.bukkit.Bukkit
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.command.CommandDispatcher
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRichMessages
import org.cyuCBMclean.cyufriendsReload.modules.profile.gui.NotificationCenterView
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.social.gui.StatusCommentsView
import org.cyuCBMclean.cyufriendsReload.modules.social.gui.WallCommentPendingView
import org.cyuCBMclean.cyufriendsReload.modules.social.gui.StatusView
import org.cyuCBMclean.cyufriendsReload.modules.social.gui.WallCommentsView
import org.cyuCBMclean.cyufriendsReload.modules.social.gui.WallPendingView
import org.cyuCBMclean.cyufriendsReload.modules.social.gui.WallView
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiLoader
import org.cyuCBMclean.cyufriendsReload.ui.view.ViewTitles
import java.text.SimpleDateFormat
import java.util.Date

object SocialCommands {

    private val commentTimeFormat = SimpleDateFormat("MM-dd HH:mm")

    fun register(plugin: CyufriendsReload, module: SocialModule) {
        CommandDispatcher(plugin, "status") {
            requirePlayer = true
            permission = "cyufriends.command.status"
            onNotPlayer = { it.sendLang("only-player") }
            onNoPermission = { it.sendLang("no-permission") }

            executes {
                if (args.isEmpty()) {
                    val guiData = GuiLoader.load(plugin, "status_list.yml") ?: return@executes player.sendLang("gui-open-failed")
                    val title = guiData.resolveTitle(
                        player,
                        ViewTitles.statusFeed(),
                        mapOf("%target_name%" to player.name, "%page%" to "1", "%view_mode%" to "全服动态")
                    )
                    StatusView(player, guiData.pattern, guiData.items, module, null, title).open()
                    return@executes
                }

                when (args[0].lowercase()) {
                    "publish", "post" -> publishStatus(plugin, module, player, args.drop(1))
                    "view", "show", "open" -> openStatusView(plugin, module, player, args.drop(1))
                    "comment" -> postStatusComment(plugin, module, player, args.drop(1))
                    "comments" -> showStatusComments(plugin, module, player, getArg(1))
                    "commentdelete", "commentdel" -> deleteStatusComment(plugin, module, player, getArg(1))
                    "like" -> setStatusLiked(plugin, module, player, getArg(1), true)
                    "unlike" -> setStatusLiked(plugin, module, player, getArg(1), false)
                    "pin", "top", "sticky" -> setStatusPinned(plugin, module, player, getArg(1), true)
                    "unpin", "untop", "unsticky" -> setStatusPinned(plugin, module, player, getArg(1), false)
                    "delete", "del", "remove" -> deleteStatus(plugin, module, player, getArg(1))
                    else -> player.sendLang("usage-status")
                }
            }

            tabComplete {
                when (args.size) {
                    1 -> filterCompletions(listOf("publish", "post", "view", "show", "open", "comment", "comments", "commentdelete", "commentdel", "like", "unlike", "pin", "top", "unpin", "delete", "del", "remove"), args[0])
                    2 -> when {
                        isStatusViewAction(args[0]) -> filterCompletions(friendNames(plugin, player), args[1])
                        isStatusPublishAction(args[0]) -> filterCompletions(listOf("public", "friends", "private"), args[1])
                        else -> emptyList()
                    }
                    3 -> when {
                        isStatusViewAction(args[0]) -> filterCompletions((1..9).map(Int::toString), args[2])
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
            }
        }.register()

        CommandDispatcher(plugin, "wall") {
            requirePlayer = true
            permission = "cyufriends.command.wall"
            onNotPlayer = { it.sendLang("only-player") }
            onNoPermission = { it.sendLang("no-permission") }

            executes {
                if (args.isEmpty()) return@executes openWallView(plugin, module, player, emptyList())

                when (args[0].lowercase()) {
                    "post", "add" -> postWall(plugin, module, player, args)
                    "view", "show", "open" -> openWallView(plugin, module, player, args.drop(1))
                    "pending", "review" -> showPendingWall(plugin, module, player, args.drop(1))
                    "approve", "pass" -> reviewWall(plugin, module, player, getArg(1), true)
                    "approveall", "passall" -> reviewAllWalls(plugin, module, player, args.drop(1), true)
                    "reject", "deny" -> reviewWall(plugin, module, player, getArg(1), false)
                    "rejectall", "denyall" -> reviewAllWalls(plugin, module, player, args.drop(1), false)
                    "comment" -> postWallReply(plugin, module, player, args.drop(1))
                    "comments" -> showWallReplies(plugin, module, player, getArg(1))
                    "commentpending", "replypending" -> showPendingWallReplies(plugin, module, player, args.drop(1))
                    "commentapprove", "replyapprove", "commentpass", "replypass" -> reviewWallReply(plugin, module, player, getArg(1), true)
                    "commentreject", "replyreject", "commentdeny", "replydeny" -> reviewWallReply(plugin, module, player, getArg(1), false)
                    "commentapproveall", "replyapproveall", "commentpassall", "replypassall" -> reviewAllWallReplies(plugin, module, player, getArg(1), true)
                    "commentrejectall", "replyrejectall", "commentdenyall", "replydenyall" -> reviewAllWallReplies(plugin, module, player, getArg(1), false)
                    "commentdelete", "commentdel" -> deleteWallReply(plugin, module, player, getArg(1))
                    "like" -> setWallLiked(plugin, module, player, getArg(1), true)
                    "unlike" -> setWallLiked(plugin, module, player, getArg(1), false)
                    "pin", "top", "sticky" -> setWallPinned(plugin, module, player, getArg(1), true)
                    "unpin", "untop", "unsticky" -> setWallPinned(plugin, module, player, getArg(1), false)
                    "delete", "del", "remove" -> deleteWall(plugin, module, player, getArg(1))
                    else -> openWallByName(plugin, module, player, args[0])
                }
            }

            tabComplete {
                if (!isPlayer) return@tabComplete emptyList()
                when (args.size) {
                    1 -> filterCompletions(listOf("post", "add", "view", "show", "open", "pending", "review", "approve", "pass", "approveall", "passall", "reject", "deny", "rejectall", "denyall", "comment", "comments", "commentpending", "replypending", "commentapprove", "replyapprove", "commentreject", "replyreject", "commentapproveall", "replyapproveall", "commentrejectall", "replyrejectall", "commentdelete", "commentdel", "like", "unlike", "pin", "top", "unpin", "delete", "del", "remove") + friendNames(plugin, player), args[0])
                    2 -> when {
                        isWallTargetAction(args[0]) || isWallBulkReviewAction(args[0]) -> filterCompletions(friendNames(plugin, player), args[1])
                        isWallPendingAction(args[0]) -> filterCompletions(friendNames(plugin, player) + listOf("chat", "list", "text"), args[1])
                        isWallReviewAction(args[0]) -> filterCompletions(module.manager.getWallCommentsSync(player.uid, player.uid, true).filter { !it.approved }.map { it.id.toString() }, args[1])
                        isWallCommentPendingAction(args[0]) -> filterCompletions(module.manager.pendingWallReplyWallIdsSync(player.uid).map(Int::toString) + listOf("chat", "list", "text"), args[1])
                        isWallCommentBulkReviewAction(args[0]) -> filterCompletions(module.manager.pendingWallReplyWallIdsSync(player.uid).map(Int::toString), args[1])
                        isWallCommentReviewAction(args[0]) -> filterCompletions(module.manager.pendingWallReplyIdsSync(player.uid).map(Int::toString), args[1])
                        else -> emptyList()
                    }
                    3 -> when {
                        isWallViewAction(args[0]) -> filterCompletions((1..9).map(Int::toString), args[2])
                        isWallPostAction(args[0]) -> filterCompletions(WallVisibility.entries.map { it.id.lowercase() }, args[2])
                        isWallPendingAction(args[0]) || isWallCommentPendingAction(args[0]) -> filterCompletions(listOf("chat", "list", "text"), args[2])
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
            }
        }.register()
    }

    private fun setStatusLiked(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?, liked: Boolean) {
        val id = rawId?.toIntOrNull() ?: return player.sendLang("usage-status-like")
        val actorUid = player.uid
        val actorName = player.name
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getVisibleStatusEntrySync(actorUid, id)
            if (entry == null) {
                DebugLogger.debug(1) {
                    "动态不可见: viewer=$actorUid statusId=$id reason=${module.manager.explainStatusVisibilitySync(actorUid, id)}"
                }
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    player.sendLang("status-not-found")
                }
                return@runAsync
            }
            val result = module.manager.setStatusLikedSync(actorUid, id, liked)
            if (result == SocialReactionResult.SUCCESS) {
                proxyGateway?.invalidateStatus(entry.uid)
                if (liked) {
                    friendModule?.friendManager?.touchInteractionSync(actorUid, entry.uid)
                    friendModule?.timelineManager?.recordInteractionSync(
                        actorUid,
                        entry.uid,
                        actorUid,
                        org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType.STATUS_LIKE,
                        entry.content,
                        entry.id
                    )
                    notifySocialInteraction(plugin, entry.uid, actorUid, actorName, SocialInteractionNoticeType.STATUS_LIKE)
                }
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialReactionResult.SUCCESS -> {
                        player.sendLang(if (liked) "status-liked" else "status-unliked")
                        player.playAudio("success")
                        refreshOpenSocialView(player)
                    }
                    SocialReactionResult.NOT_FOUND -> player.sendLang("status-not-found")
                    SocialReactionResult.ALREADY_REACTED -> player.sendLang("status-like-already")
                    SocialReactionResult.NOT_REACTED -> player.sendLang("status-like-missing")
                }
            }
        }
    }

    private fun postStatusComment(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        if (args.size < 2) return player.sendLang("usage-status-comment")
        val statusId = args[0].toIntOrNull() ?: return player.sendLang("usage-status-comment")
        val content = args.drop(1).joinToString(" ")
        val actorUid = player.uid
        val actorName = player.name
        val cooldownSeconds = module.manager.statusCommentCooldownSeconds(player)
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getVisibleStatusEntrySync(actorUid, statusId)
            if (entry == null) {
                DebugLogger.debug(1) {
                    "动态评论拦截: viewer=$actorUid statusId=$statusId reason=${module.manager.explainStatusVisibilitySync(actorUid, id = statusId)}"
                }
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    player.sendLang("status-not-found")
                }
                return@runAsync
            }
            val result = module.manager.addStatusCommentSync(statusId, actorUid, content, cooldownSeconds)
            if (result == SocialWriteResult.SUCCESS) {
                proxyGateway?.invalidateStatus(entry.uid)
                friendModule?.friendManager?.touchInteractionSync(actorUid, entry.uid)
                friendModule?.timelineManager?.recordInteractionSync(
                    actorUid,
                    entry.uid,
                    actorUid,
                    org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType.STATUS_COMMENT,
                    content,
                    statusId
                )
                notifySocialInteraction(
                    plugin,
                    entry.uid,
                    actorUid,
                    actorName,
                    SocialInteractionNoticeType.STATUS_COMMENT,
                    content
                )
            }
            val remaining = module.manager.remainingStatusCommentCooldown(actorUid, cooldownSeconds).toString()
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when {
                    result == SocialWriteResult.SUCCESS -> {
                        player.sendLang("status-commented")
                        player.playAudio("success")
                    }
                    result == SocialWriteResult.EMPTY -> player.sendLang("status-comment-empty")
                    else -> player.sendLang("status-comment-cooldown", mapOf("seconds" to remaining))
                }
            }
        }
    }

    private fun showStatusComments(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?) {
        val statusId = rawId?.toIntOrNull() ?: return player.sendLang("usage-status-comments")
        val viewerUid = player.uid
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getVisibleStatusEntrySync(viewerUid, statusId)
            if (entry == null) {
                DebugLogger.debug(1) {
                    "动态评论查看拦截: viewer=$viewerUid statusId=$statusId reason=${module.manager.explainStatusVisibilitySync(viewerUid, id = statusId)}"
                }
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    player.sendLang("status-not-found")
                }
                return@runAsync
            }
            val comments = module.manager.getStatusCommentsSync(statusId, 10)
            val ownerName = CyuIdHook.getName(entry.uid) ?: entry.uid
            val guiData = GuiLoader.load(plugin, "status_comments.yml")
            if (guiData != null) {
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    val title = guiData.resolveTitle(
                        player,
                        ViewTitles.statusComments(statusId),
                        mapOf("%status_id%" to statusId.toString(), "%target_name%" to ownerName, "%owner_name%" to ownerName)
                    )
                    StatusCommentsView(player, guiData.pattern, guiData.items, module, statusId, entry.uid, ownerName, title).open()
                }
                return@runAsync
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                if (comments.isEmpty()) {
                    player.sendLang("status-comments-empty")
                    return@runEntity
                }
                player.sendMessage("§b[CyuFriends] §f动态评论 §7#${statusId}")
                comments.asReversed().forEach { comment ->
                    val author = CyuIdHook.getName(comment.authorUid) ?: comment.authorUid
                    val time = commentTimeFormat.format(Date(comment.timestamp))
                    player.sendMessage("§7${comment.id}. §b$author §7[$time] §f${comment.content}")
                }
            }
        }
    }

    private fun deleteStatusComment(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?) {
        val commentId = rawId?.toIntOrNull() ?: return player.sendLang("usage-status-comment-delete")
        val actorUid = player.uid
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val comment = module.manager.getStatusCommentSync(commentId)
            val ownerUid = comment?.statusId?.let { module.manager.getStatusOwnerSync(it) }
            val result = module.manager.deleteStatusCommentSync(actorUid, commentId, force)
            if (result == SocialDeleteResult.SUCCESS && ownerUid != null) {
                proxyGateway?.invalidateStatus(ownerUid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialDeleteResult.SUCCESS -> {
                        player.sendLang("status-comment-deleted")
                        player.playAudio("success")
                        refreshOpenSocialView(player)
                    }
                    SocialDeleteResult.NOT_FOUND -> player.sendLang("status-comment-not-found")
                    SocialDeleteResult.FORBIDDEN -> player.sendLang("status-comment-delete-forbidden")
                }
            }
        }
    }

    private fun publishStatus(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        if (args.isEmpty()) return player.sendLang("usage-status-publish")
        val visibility = StatusVisibility.fromValue(args.firstOrNull()) ?: StatusVisibility.PUBLIC
        val content = if (StatusVisibility.fromValue(args.firstOrNull()) != null) args.drop(1).joinToString(" ") else args.joinToString(" ")
        if (content.isBlank()) return player.sendLang("usage-status-publish")
        val uid = player.uid
        val maxStatuses = module.manager.statusMaxLimit(player)
        val cooldownSeconds = module.manager.statusPublishCooldownSeconds(player)
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway

        CyuConcurrency.scheduler.runAsync(plugin) {
            val result = module.manager.publishStatusSync(uid, content, visibility, maxStatuses, cooldownSeconds)
            if (result == SocialWriteResult.SUCCESS) {
                proxyGateway?.invalidateStatus(uid)
            }
            val remaining = module.manager.remainingStatusPublishCooldown(uid, cooldownSeconds).toString()
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialWriteResult.SUCCESS -> {
                        player.sendLang("status-published", mapOf("visibility" to visibility.displayName))
                        player.playAudio("status-publish")
                    }
                    SocialWriteResult.EMPTY -> player.sendLang("status-empty")
                    SocialWriteResult.COOLDOWN -> player.sendLang("status-publish-cooldown", mapOf("seconds" to remaining))
                }
            }
        }
    }

    private fun setStatusPinned(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?, pinned: Boolean) {
        val id = rawId?.toIntOrNull() ?: return player.sendLang("usage-status-pin")
        val uid = player.uid
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val ownerUid = module.manager.getStatusOwnerSync(id)
            val result = module.manager.setStatusPinnedSync(uid, id, pinned, force)
            if (result == SocialDeleteResult.SUCCESS) {
                proxyGateway?.invalidateStatus(ownerUid ?: uid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialDeleteResult.SUCCESS -> {
                        player.sendLang(if (pinned) "status-pinned" else "status-unpinned")
                        player.playAudio("success")
                        refreshOpenSocialView(player)
                    }
                    SocialDeleteResult.NOT_FOUND -> player.sendLang("status-delete-not-found")
                    SocialDeleteResult.FORBIDDEN -> player.sendLang("status-delete-forbidden")
                }
            }
        }
    }

    private fun openStatusView(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        val parsed = parseStatusView(player, args) ?: return
        val guiData = GuiLoader.load(plugin, "status_list.yml") ?: run {
            player.sendLang("gui-open-failed")
            return
        }
        val targetName = CyuIdHook.getName(parsed.targetUid) ?: player.name
        val viewMode = targetName
        val title = guiData.resolveTitle(
            player,
            parsed.title,
            mapOf("%target_name%" to targetName, "%page%" to parsed.page.toString(), "%view_mode%" to viewMode)
        )
        val view = StatusView(player, guiData.pattern, guiData.items, module, parsed.targetUid, title)
        view.jumpTo(parsed.page)
        view.open()
    }

    private fun deleteStatus(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?) {
        val id = rawId?.toIntOrNull() ?: return player.sendLang("usage-status-delete")
        val uid = player.uid
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val ownerUid = module.manager.getStatusOwnerSync(id)
            val result = module.manager.deleteStatusSync(uid, id, force)
            if (result == SocialDeleteResult.SUCCESS) {
                proxyGateway?.invalidateStatus(ownerUid ?: uid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialDeleteResult.SUCCESS -> {
                        player.sendLang("status-deleted")
                        player.playAudio("success")
                        refreshOpenSocialView(player)
                    }
                    SocialDeleteResult.NOT_FOUND -> player.sendLang("status-delete-not-found")
                    SocialDeleteResult.FORBIDDEN -> player.sendLang("status-delete-forbidden")
                }
            }
        }
    }

    private fun postWall(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        if (args.size < 3) return player.sendLang("usage-wall-post")
        val targetName = args[1]
        val explicitVisibility = WallVisibility.fromValue(args.getOrNull(2))
        val contentIndex = if (explicitVisibility != null) 3 else 2
        if (args.size <= contentIndex) return player.sendLang("usage-wall-post")
        val content = args.drop(contentIndex).joinToString(" ")
        val visibility = explicitVisibility ?: defaultWallVisibility(plugin)
        val targetUid = CyuIdHook.getUidByName(targetName) ?: return player.sendLang("player-not-found")
        val displayName = CyuIdHook.getName(targetUid) ?: targetName
        val actorUid = player.uid
        val actorName = player.name
        val maxMessages = module.manager.wallMaxLimit(player)
        val cooldownSeconds = module.manager.wallPostCooldownSeconds(player)
        val adminApproved = player.hasPermission("cyufriends.admin")
        val approved = module.manager.wallAutoApproved(targetUid, actorUid, adminApproved)

        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        if (friendModule != null && friendModule.blockManager.isBlockedStable(targetUid, actorUid)) {
            return player.sendLang("blocked-by-target")
        }
        if (!canAccessWall(plugin, friendModule, actorUid, targetUid, WallAccessAction.POST)) {
            return player.sendLang("wall-post-denied")
        }
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway

        CyuConcurrency.scheduler.runAsync(plugin) {
            val result = module.manager.postWallCommentSync(targetUid, actorUid, content, visibility, maxMessages, cooldownSeconds, approved)
            if (result == SocialWallSubmitResult.SUCCESS || result == SocialWallSubmitResult.PENDING) {
                friendModule?.let { it.friendManager.touchInteractionSync(actorUid, targetUid) }
                proxyGateway?.invalidateWall(targetUid)
            }
            if (result == SocialWallSubmitResult.SUCCESS) {
                friendModule?.timelineManager?.recordInteractionSync(
                    actorUid,
                    targetUid,
                    actorUid,
                    org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType.WALL_POST,
                    content
                )
                notifySocialInteraction(
                    plugin,
                    targetUid,
                    actorUid,
                    actorName,
                    SocialInteractionNoticeType.WALL_POST,
                    content
                )
            } else if (result == SocialWallSubmitResult.PENDING) {
                notifyPendingWallOwner(plugin, targetUid, actorName, visibility)
                module.auditLogger.log(
                    "wall_entry_pending",
                    mapOf(
                        "actorUid" to actorUid,
                        "actorName" to actorName,
                        "ownerUid" to targetUid,
                        "ownerName" to displayName,
                        "visibility" to visibility.id,
                        "content" to content.take(80)
                    )
                )
            }
            val remaining = module.manager.remainingWallPostCooldown(actorUid, cooldownSeconds).toString()
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialWallSubmitResult.SUCCESS -> {
                        player.sendLang("wall-posted", mapOf("target" to displayName, "visibility" to visibility.displayName))
                        player.playAudio("wall-post")
                    }
                    SocialWallSubmitResult.PENDING -> {
                        player.sendLang("wall-post-pending", mapOf("target" to displayName, "visibility" to visibility.displayName))
                        player.playAudio("wall-post")
                    }
                    SocialWallSubmitResult.EMPTY -> player.sendLang("wall-empty")
                    SocialWallSubmitResult.COOLDOWN -> player.sendLang("wall-post-cooldown", mapOf("seconds" to remaining))
                }
            }
        }
    }

    private fun openWallView(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        val parsed = parseWallView(plugin, player, args) ?: return
        val guiData = GuiLoader.load(plugin, "wall_view.yml") ?: run {
            player.sendLang("gui-open-failed")
            return
        }
        val title = guiData.resolveTitle(
            player,
            parsed.title,
            mapOf("%target_name%" to parsed.targetName, "%friend_name%" to parsed.targetName, "%raw_name%" to parsed.targetName, "%page%" to parsed.page.toString())
        )
        val view = WallView(player, guiData.pattern, guiData.items, module, parsed.targetName, title)
        view.jumpTo(parsed.page)
        view.open()
    }

    private fun openWallByName(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, targetName: String) {
        val targetUid = CyuIdHook.getUidByName(targetName) ?: return player.sendLang("player-not-found")
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        if (friendModule != null && friendModule.blockManager.isBlockedStable(targetUid, player.uid)) {
            return player.sendLang("blocked-by-target")
        }
        if (!canAccessWall(plugin, friendModule, player.uid, targetUid, WallAccessAction.VIEW)) {
            return player.sendLang("wall-view-denied")
        }

        val guiData = GuiLoader.load(plugin, "wall_view.yml") ?: return player.sendLang("gui-open-failed")
        val title = guiData.resolveTitle(
            player,
            ViewTitles.wall(targetName),
            mapOf("%target_name%" to targetName, "%friend_name%" to targetName, "%raw_name%" to targetName, "%page%" to "1")
        )
        WallView(player, guiData.pattern, guiData.items, module, targetName, title).open()
    }

    private fun showPendingWall(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        val (cleanArgs, chatMode) = stripChatListMode(args)
        val targetInput = cleanArgs.firstOrNull()
        val targetUid = if (targetInput.isNullOrBlank()) player.uid else CyuIdHook.getUidByName(targetInput) ?: return player.sendLang("player-not-found")
        if (targetUid != player.uid && !player.hasPermission("cyufriends.admin")) {
            return player.sendLang("no-permission")
        }
        val displayName = if (targetUid == player.uid) player.name else (CyuIdHook.getName(targetUid) ?: targetInput ?: targetUid)
        val guiData = if (chatMode) null else GuiLoader.load(plugin, "wall_pending.yml")
        if (guiData != null) {
            val title = guiData.resolveTitle(
                player,
                ViewTitles.wallPending(displayName),
                mapOf("%target_name%" to displayName, "%friend_name%" to displayName, "%raw_name%" to displayName)
            )
            WallPendingView(player, guiData.pattern, guiData.items, module, targetUid, displayName, title).open()
            return
        }
        CyuConcurrency.scheduler.runAsync(plugin) {
            val pending = module.manager.getPendingWallEntriesSync(targetUid)
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                if (pending.isEmpty()) {
                    player.sendLang("wall-pending-empty", mapOf("target" to displayName))
                    return@runEntity
                }
                player.sendLang("wall-pending-header", mapOf("target" to displayName, "count" to pending.size.toString()))
                pending.take(10).forEach { entry ->
                    val author = CyuIdHook.getName(entry.authorUid) ?: entry.authorUid
                    if (chatMode) {
                        FriendRichMessages.sendPendingWallEntry(player, entry, author)
                    } else {
                        val time = commentTimeFormat.format(Date(entry.timestamp))
                        player.sendMessage("§7#${entry.id} §b$author §7[$time] §f${entry.visibility.displayName} §8| §f${entry.content}")
                    }
                }
                if (pending.size > 10) {
                    player.sendLang("wall-pending-more", mapOf("count" to (pending.size - 10).toString()))
                }
            }
        }
    }

    private fun reviewWall(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?, approve: Boolean) {
        val wallId = rawId?.toIntOrNull() ?: return player.sendLang(if (approve) "usage-wall-approve" else "usage-wall-reject")
        val actorUid = player.uid
        val actorName = player.name
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getWallEntrySync(wallId)
            val result = module.manager.reviewWallEntrySync(actorUid, wallId, approve, force)
            if (result == SocialModerationResult.SUCCESS && entry != null) {
                proxyGateway?.invalidateWall(entry.ownerUid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialModerationResult.SUCCESS -> {
                        entry?.let {
                            module.auditLogger.log(
                                if (approve) "wall_entry_approved" else "wall_entry_rejected",
                                mapOf(
                                    "actorUid" to actorUid,
                                    "actorName" to actorName,
                                    "ownerUid" to it.ownerUid,
                                    "ownerName" to (CyuIdHook.getName(it.ownerUid) ?: it.ownerUid),
                                    "wallId" to wallId.toString(),
                                    "authorUid" to it.authorUid,
                                    "authorName" to (CyuIdHook.getName(it.authorUid) ?: it.authorUid)
                                )
                            )
                        }
                    player.sendLang(if (approve) "wall-approved" else "wall-rejected")
                    player.playAudio("success")
                    refreshOpenSocialView(player)
                }
                    SocialModerationResult.EMPTY -> player.sendLang("wall-review-empty")
                    SocialModerationResult.NOT_FOUND -> player.sendLang("wall-delete-not-found")
                    SocialModerationResult.FORBIDDEN -> player.sendLang("wall-review-forbidden")
                    SocialModerationResult.ALREADY_APPROVED -> player.sendLang("wall-already-approved")
                }
            }
        }
    }

    private fun reviewAllWalls(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>, approve: Boolean) {
        val targetInput = args.firstOrNull()
        val actorUid = player.uid
        val actorName = player.name
        val targetUid = if (targetInput.isNullOrBlank()) actorUid else CyuIdHook.getUidByName(targetInput) ?: return player.sendLang("player-not-found")
        if (targetUid != actorUid && !player.hasPermission("cyufriends.admin")) {
            return player.sendLang("no-permission")
        }
        val displayName = if (targetUid == actorUid) actorName else (CyuIdHook.getName(targetUid) ?: targetInput ?: targetUid)
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val result = module.manager.reviewAllWallEntriesSync(actorUid, targetUid, approve, force)
            if (result.first == SocialModerationResult.SUCCESS) {
                proxyGateway?.invalidateWall(targetUid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result.first) {
                    SocialModerationResult.SUCCESS -> {
                        module.auditLogger.log(
                            if (approve) "wall_entry_approved_all" else "wall_entry_rejected_all",
                            mapOf(
                                "actorUid" to actorUid,
                                "actorName" to actorName,
                                "ownerUid" to targetUid,
                                "ownerName" to displayName,
                                "amount" to result.second.toString()
                            )
                        )
                player.sendLang(
                    if (approve) "wall-approved-all" else "wall-rejected-all",
                    mapOf("target" to displayName, "amount" to result.second.toString())
                )
                player.playAudio("success")
                refreshOpenSocialView(player)
            }
                    SocialModerationResult.EMPTY -> player.sendLang("wall-review-empty")
                    SocialModerationResult.FORBIDDEN -> player.sendLang("wall-review-forbidden")
                    SocialModerationResult.NOT_FOUND -> player.sendLang("wall-delete-not-found")
                    SocialModerationResult.ALREADY_APPROVED -> player.sendLang("wall-already-approved")
                }
            }
        }
    }

    private fun deleteWall(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?) {
        val id = rawId?.toIntOrNull() ?: return player.sendLang("usage-wall-delete")
        val uid = player.uid
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val ownerUid = module.manager.getWallOwnerSync(id)
            val result = module.manager.deleteWallCommentSync(uid, id, force)
            if (result == SocialDeleteResult.SUCCESS && ownerUid != null) {
                proxyGateway?.invalidateWall(ownerUid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialDeleteResult.SUCCESS -> {
                        player.sendLang("wall-deleted")
                        player.playAudio("success")
                        refreshOpenSocialView(player)
                    }
                    SocialDeleteResult.NOT_FOUND -> player.sendLang("wall-delete-not-found")
                    SocialDeleteResult.FORBIDDEN -> player.sendLang("wall-delete-forbidden")
                }
            }
        }
    }

    private fun setWallLiked(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?, liked: Boolean) {
        val id = rawId?.toIntOrNull() ?: return player.sendLang("usage-wall-like")
        val actorUid = player.uid
        val actorName = player.name
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getWallEntrySync(id)
            if (entry == null) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-delete-not-found") }
                return@runAsync
            }
            if (!canUseWallEntry(plugin, module, friendModule, actorUid, entry, WallAccessAction.LIKE)) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-view-denied") }
                return@runAsync
            }
            val result = module.manager.setWallLikedSync(actorUid, id, liked)
            if (result == SocialReactionResult.SUCCESS) {
                proxyGateway?.invalidateWall(entry.ownerUid)
                if (liked) {
                    friendModule?.friendManager?.touchInteractionSync(actorUid, entry.ownerUid)
                    friendModule?.timelineManager?.recordInteractionSync(
                        actorUid,
                        entry.ownerUid,
                        actorUid,
                        org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType.WALL_LIKE,
                        entry.content,
                        entry.id
                    )
                    notifySocialInteraction(plugin, entry.ownerUid, actorUid, actorName, SocialInteractionNoticeType.WALL_LIKE)
                }
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialReactionResult.SUCCESS -> {
                        player.sendLang(if (liked) "wall-liked" else "wall-unliked")
                        player.playAudio("success")
                    }
                    SocialReactionResult.NOT_FOUND -> player.sendLang("wall-delete-not-found")
                    SocialReactionResult.ALREADY_REACTED -> player.sendLang("wall-like-already")
                    SocialReactionResult.NOT_REACTED -> player.sendLang("wall-like-missing")
                }
            }
        }
    }

    private fun setWallPinned(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?, pinned: Boolean) {
        val id = rawId?.toIntOrNull() ?: return player.sendLang("usage-wall-pin")
        val uid = player.uid
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val ownerUid = module.manager.getWallOwnerSync(id)
            val result = module.manager.setWallPinnedSync(uid, id, pinned, force)
            if (result == SocialDeleteResult.SUCCESS && ownerUid != null) {
                proxyGateway?.invalidateWall(ownerUid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialDeleteResult.SUCCESS -> {
                        player.sendLang(if (pinned) "wall-pinned" else "wall-unpinned")
                        player.playAudio("success")
                    }
                    SocialDeleteResult.NOT_FOUND -> player.sendLang("wall-delete-not-found")
                    SocialDeleteResult.FORBIDDEN -> player.sendLang("wall-delete-forbidden")
                }
            }
        }
    }

    private fun postWallReply(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        if (args.size < 2) return player.sendLang("usage-wall-comment")
        val wallId = args[0].toIntOrNull() ?: return player.sendLang("usage-wall-comment")
        val content = args.drop(1).joinToString(" ")
        val actorUid = player.uid
        val actorName = player.name
        val cooldownSeconds = module.manager.wallReplyCooldownSeconds(player)
        val adminApproved = player.hasPermission("cyufriends.admin")
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getWallEntrySync(wallId)
            if (entry == null) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-delete-not-found") }
                return@runAsync
            }
            if (friendModule != null && friendModule.blockManager.isBlockedStable(entry.ownerUid, actorUid)) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("blocked-by-target") }
                return@runAsync
            }
            if (!canUseWallEntry(plugin, module, friendModule, actorUid, entry, WallAccessAction.COMMENT)) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-comment-denied") }
                return@runAsync
            }
            val result = module.manager.addWallReplySync(wallId, actorUid, content, cooldownSeconds, adminApproved)
            if (result == SocialWallSubmitResult.SUCCESS || result == SocialWallSubmitResult.PENDING) {
                friendModule?.let { it.friendManager.touchInteractionSync(actorUid, entry.ownerUid) }
                proxyGateway?.invalidateWall(entry.ownerUid)
            }
            if (result == SocialWallSubmitResult.SUCCESS) {
                friendModule?.timelineManager?.recordInteractionSync(
                    actorUid,
                    entry.ownerUid,
                    actorUid,
                    org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType.WALL_COMMENT,
                    content,
                    wallId
                )
                notifySocialInteraction(
                    plugin,
                    entry.ownerUid,
                    actorUid,
                    actorName,
                    SocialInteractionNoticeType.WALL_COMMENT,
                    content
                )
            } else if (result == SocialWallSubmitResult.PENDING) {
                notifyPendingWallReplyOwner(plugin, entry.ownerUid, actorName)
                module.auditLogger.log(
                    "wall_reply_pending",
                    mapOf(
                        "actorUid" to actorUid,
                        "actorName" to actorName,
                        "ownerUid" to entry.ownerUid,
                        "ownerName" to (CyuIdHook.getName(entry.ownerUid) ?: entry.ownerUid),
                        "wallId" to wallId.toString(),
                        "content" to content.take(80)
                    )
                )
            }
            val remaining = module.manager.remainingWallReplyCooldown(actorUid, cooldownSeconds).toString()
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialWallSubmitResult.SUCCESS -> {
                        player.sendLang("wall-commented")
                        player.playAudio("success")
                    }
                    SocialWallSubmitResult.PENDING -> {
                        player.sendLang("wall-comment-pending")
                        player.playAudio("success")
                    }
                    SocialWallSubmitResult.EMPTY -> player.sendLang("wall-comment-empty")
                    SocialWallSubmitResult.COOLDOWN -> player.sendLang("wall-reply-cooldown", mapOf("seconds" to remaining))
                }
            }
        }
    }

    private fun showWallReplies(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?) {
        val wallId = rawId?.toIntOrNull() ?: return player.sendLang("usage-wall-comments")
        val viewerUid = player.uid
        val admin = player.hasPermission("cyufriends.admin")
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getWallEntrySync(wallId)
            if (entry == null) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-delete-not-found") }
                return@runAsync
            }
            if (!canUseWallEntry(plugin, module, friendModule, viewerUid, entry, WallAccessAction.VIEW)) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-view-denied") }
                return@runAsync
            }
            val canReview = admin || viewerUid == entry.ownerUid
            val replies = module.manager.getWallRepliesSync(wallId, viewerUid, 10, includePending = canReview)
            val pendingCount = if (canReview) module.manager.pendingWallReplyCountSync(wallId) else 0
            val ownerName = CyuIdHook.getName(entry.ownerUid) ?: entry.ownerUid
            val guiData = GuiLoader.load(plugin, "wall_comments.yml")
            if (guiData != null) {
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    val title = guiData.resolveTitle(
                        player,
                        ViewTitles.wallComments(wallId),
                        mapOf("%wall_id%" to wallId.toString(), "%target_name%" to ownerName, "%owner_name%" to ownerName)
                    )
                    WallCommentsView(player, guiData.pattern, guiData.items, module, wallId, entry.ownerUid, ownerName, title).open()
                }
                return@runAsync
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                if (replies.isEmpty()) {
                    player.sendLang("wall-comments-empty")
                    return@runEntity
                }
                player.sendLang("wall-comments-header", mapOf("id" to wallId.toString()))
                if (pendingCount > 0) {
                    player.sendLang("wall-comment-pending-summary", mapOf("amount" to pendingCount.toString()))
                }
                replies.asReversed().forEach { reply ->
                    val author = CyuIdHook.getName(reply.authorUid) ?: reply.authorUid
                    val time = commentTimeFormat.format(Date(reply.timestamp))
                    val state = if (reply.approved) "§a已通过" else "§6待审核"
                    player.sendMessage("§7${reply.id}. §b$author §7[$time] $state §8| §f${reply.content}")
                }
            }
        }
    }

    private fun showPendingWallReplies(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, args: List<String>) {
        val (cleanArgs, chatMode) = stripChatListMode(args)
        val wallId = cleanArgs.firstOrNull()?.toIntOrNull() ?: return player.sendLang("usage-wall-commentpending")
        val actorUid = player.uid
        val force = player.hasPermission("cyufriends.admin")
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getWallEntrySync(wallId)
            if (entry == null) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-delete-not-found") }
                return@runAsync
            }
            if (!force && entry.ownerUid != actorUid) {
                CyuConcurrency.scheduler.runEntity(plugin, player) { player.sendLang("wall-review-forbidden") }
                return@runAsync
            }
            val replies = module.manager.getPendingWallRepliesSync(wallId)
            val ownerName = CyuIdHook.getName(entry.ownerUid) ?: entry.ownerUid
            val guiData = if (chatMode) null else GuiLoader.load(plugin, "wall_comment_pending.yml")
            if (guiData != null) {
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    val title = guiData.resolveTitle(
                        player,
                        ViewTitles.wallCommentPending(wallId),
                        mapOf("%wall_id%" to wallId.toString(), "%target_name%" to ownerName, "%friend_name%" to ownerName, "%raw_name%" to ownerName)
                    )
                    WallCommentPendingView(
                        player,
                        guiData.pattern,
                        guiData.items,
                        module,
                        wallId,
                        ownerName,
                        title
                    ).open()
                }
                return@runAsync
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                if (replies.isEmpty()) {
                    player.sendLang("wall-comment-pending-empty", mapOf("id" to wallId.toString()))
                    return@runEntity
                }
                player.sendLang("wall-comment-pending-header", mapOf("id" to wallId.toString(), "count" to replies.size.toString()))
                replies.asReversed().forEach { reply ->
                    val author = CyuIdHook.getName(reply.authorUid) ?: reply.authorUid
                    if (chatMode) {
                        FriendRichMessages.sendPendingReplyEntry(
                            player,
                            org.cyuCBMclean.cyufriendsReload.modules.social.PendingWallReplyEntry(
                                id = reply.id,
                                wallId = wallId,
                                ownerUid = entry.ownerUid,
                                authorUid = reply.authorUid,
                                content = reply.content,
                                timestamp = reply.timestamp
                            ),
                            author
                        )
                    } else {
                        val time = commentTimeFormat.format(Date(reply.timestamp))
                        player.sendMessage("§7${reply.id}. §b$author §7[$time] §8| §f${reply.content}")
                    }
                }
            }
        }
    }

    private fun reviewWallReply(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?, approve: Boolean) {
        val replyId = rawId?.toIntOrNull() ?: return player.sendLang(if (approve) "usage-wall-commentapprove" else "usage-wall-commentreject")
        val actorUid = player.uid
        val actorName = player.name
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val reply = module.manager.getWallReplySync(replyId)
            val entry = reply?.wallId?.let { module.manager.getWallEntrySync(it) }
            val result = module.manager.reviewWallReplySync(actorUid, replyId, approve, force)
            if (result == SocialModerationResult.SUCCESS && entry != null) {
                proxyGateway?.invalidateWall(entry.ownerUid)
                module.auditLogger.log(
                    if (approve) "wall_reply_approved" else "wall_reply_rejected",
                    mapOf(
                        "actorUid" to actorUid,
                        "actorName" to actorName,
                        "ownerUid" to entry.ownerUid,
                        "ownerName" to (CyuIdHook.getName(entry.ownerUid) ?: entry.ownerUid),
                        "wallId" to entry.id.toString(),
                        "replyId" to replyId.toString(),
                        "authorUid" to (reply?.authorUid ?: ""),
                        "authorName" to (reply?.authorUid?.let { CyuIdHook.getName(it) } ?: (reply?.authorUid ?: ""))
                    )
                )
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialModerationResult.SUCCESS -> {
                        player.sendLang(if (approve) "wall-comment-approved" else "wall-comment-rejected")
                        player.playAudio("success")
                        refreshOpenSocialView(player)
                    }
                    SocialModerationResult.EMPTY -> player.sendLang("wall-review-empty")
                    SocialModerationResult.NOT_FOUND -> player.sendLang("wall-comment-not-found")
                    SocialModerationResult.FORBIDDEN -> player.sendLang("wall-review-forbidden")
                    SocialModerationResult.ALREADY_APPROVED -> player.sendLang("wall-already-approved")
                }
            }
        }
    }

    private fun reviewAllWallReplies(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?, approve: Boolean) {
        val wallId = rawId?.toIntOrNull() ?: return player.sendLang(if (approve) "usage-wall-commentapproveall" else "usage-wall-commentrejectall")
        val actorUid = player.uid
        val actorName = player.name
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val entry = module.manager.getWallEntrySync(wallId)
            val result = module.manager.reviewAllWallRepliesSync(actorUid, wallId, approve, force)
            if (result.first == SocialModerationResult.SUCCESS && entry != null) {
                proxyGateway?.invalidateWall(entry.ownerUid)
                module.auditLogger.log(
                    if (approve) "wall_reply_approved_all" else "wall_reply_rejected_all",
                    mapOf(
                        "actorUid" to actorUid,
                        "actorName" to actorName,
                        "ownerUid" to entry.ownerUid,
                        "ownerName" to (CyuIdHook.getName(entry.ownerUid) ?: entry.ownerUid),
                        "wallId" to wallId.toString(),
                        "amount" to result.second.toString()
                    )
                )
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result.first) {
                    SocialModerationResult.SUCCESS -> {
                    player.sendLang(
                        if (approve) "wall-comment-approved-all" else "wall-comment-rejected-all",
                        mapOf("id" to wallId.toString(), "amount" to result.second.toString())
                    )
                    player.playAudio("success")
                    refreshOpenSocialView(player)
                }
                    SocialModerationResult.EMPTY -> player.sendLang("wall-comment-review-empty", mapOf("id" to wallId.toString()))
                    SocialModerationResult.NOT_FOUND -> player.sendLang("wall-delete-not-found")
                    SocialModerationResult.FORBIDDEN -> player.sendLang("wall-review-forbidden")
                    SocialModerationResult.ALREADY_APPROVED -> player.sendLang("wall-already-approved")
                }
            }
        }
    }

    private fun deleteWallReply(plugin: CyufriendsReload, module: SocialModule, player: org.bukkit.entity.Player, rawId: String?) {
        val replyId = rawId?.toIntOrNull() ?: return player.sendLang("usage-wall-comment-delete")
        val actorUid = player.uid
        val force = player.hasPermission("cyufriends.admin")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(plugin) {
            val reply = module.manager.getWallReplySync(replyId)
            val ownerUid = reply?.wallId?.let { module.manager.getWallOwnerSync(it) }
            val result = module.manager.deleteWallReplySync(actorUid, replyId, force)
            if (result == SocialDeleteResult.SUCCESS && ownerUid != null) {
                proxyGateway?.invalidateWall(ownerUid)
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                when (result) {
                    SocialDeleteResult.SUCCESS -> {
                        player.sendLang("wall-comment-deleted")
                        player.playAudio("success")
                        refreshOpenSocialView(player)
                    }
                    SocialDeleteResult.NOT_FOUND -> player.sendLang("wall-comment-not-found")
                    SocialDeleteResult.FORBIDDEN -> player.sendLang("wall-comment-delete-forbidden")
                }
            }
        }
    }

    private data class StatusViewTarget(
        val targetUid: String,
        val page: Int,
        val title: String
    )

    private data class WallViewTarget(
        val targetName: String,
        val page: Int,
        val title: String
    )

    private fun parseStatusView(player: org.bukkit.entity.Player, args: List<String>): StatusViewTarget? {
        val selfUid = player.uid
        if (args.isEmpty()) return StatusViewTarget(selfUid, 1, ViewTitles.myStatus(player.name))

        val first = args[0]
        val pageOnly = first.toIntOrNull()
        if (pageOnly != null) {
            return StatusViewTarget(selfUid, pageOnly, ViewTitles.myStatus(player.name))
        }

        val targetUid = CyuIdHook.getUidByName(first) ?: return player.sendLang("player-not-found").let { null }
        val page = args.getOrNull(1)?.toIntOrNull() ?: 1
        val displayName = CyuIdHook.getName(targetUid) ?: first
        return StatusViewTarget(targetUid, page, ViewTitles.statusOf(displayName))
    }

    private fun parseWallView(plugin: CyufriendsReload, player: org.bukkit.entity.Player, args: List<String>): WallViewTarget? {
        if (args.isEmpty()) {
            return WallViewTarget(player.name, 1, ViewTitles.wall(player.name))
        }

        val first = args[0]
        val pageOnly = first.toIntOrNull()
        if (pageOnly != null) {
            return WallViewTarget(player.name, pageOnly, ViewTitles.wall(player.name))
        }

        val targetUid = CyuIdHook.getUidByName(first) ?: return player.sendLang("player-not-found").let { null }
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        if (!canAccessWall(plugin, friendModule, player.uid, targetUid, WallAccessAction.VIEW)) {
            player.sendLang("wall-view-denied")
            return null
        }

        val page = args.getOrNull(1)?.toIntOrNull() ?: 1
        val targetName = CyuIdHook.getName(targetUid) ?: first
        return WallViewTarget(targetName, page, ViewTitles.wall(targetName))
    }

    private fun isStatusViewAction(value: String): Boolean {
        return value.equals("view", true) || value.equals("show", true) || value.equals("open", true)
    }

    private fun isStatusPublishAction(value: String): Boolean {
        return value.equals("publish", true) || value.equals("post", true)
    }

    private fun isWallViewAction(value: String): Boolean {
        return value.equals("view", true) || value.equals("show", true) || value.equals("open", true)
    }

    private fun isWallPostAction(value: String): Boolean {
        return value.equals("post", true) || value.equals("add", true)
    }

    private fun isWallPendingAction(value: String): Boolean {
        return value.equals("pending", true) || value.equals("review", true)
    }

    private fun isWallReviewAction(value: String): Boolean {
        return value.equals("approve", true) || value.equals("pass", true) || value.equals("reject", true) || value.equals("deny", true)
    }

    private fun isWallBulkReviewAction(value: String): Boolean {
        return value.equals("approveall", true) || value.equals("passall", true) || value.equals("rejectall", true) || value.equals("denyall", true)
    }

    private fun isWallCommentPendingAction(value: String): Boolean {
        return value.equals("commentpending", true) || value.equals("replypending", true)
    }

    private fun isWallCommentReviewAction(value: String): Boolean {
        return value.equals("commentapprove", true) || value.equals("replyapprove", true) ||
            value.equals("commentpass", true) || value.equals("replypass", true) ||
            value.equals("commentreject", true) || value.equals("replyreject", true) ||
            value.equals("commentdeny", true) || value.equals("replydeny", true)
    }

    private fun isWallCommentBulkReviewAction(value: String): Boolean {
        return value.equals("commentapproveall", true) || value.equals("replyapproveall", true) ||
            value.equals("commentpassall", true) || value.equals("replypassall", true) ||
            value.equals("commentrejectall", true) || value.equals("replyrejectall", true) ||
            value.equals("commentdenyall", true) || value.equals("replydenyall", true)
    }

    private fun isWallTargetAction(value: String): Boolean {
        return isWallPostAction(value) || isWallViewAction(value)
    }

    private fun friendNames(plugin: CyufriendsReload, player: org.bukkit.entity.Player): List<String> {
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: return emptyList()
        return friendModule.friendManager.getFriendEntriesStoredSync(player.uid).map { CyuIdHook.getName(it.friendUid) ?: it.friendUid }
    }

    private fun canUseWallEntry(
        plugin: CyufriendsReload,
        module: SocialModule,
        friendModule: FriendModule?,
        viewerUid: String,
        entry: WallEntry,
        action: WallAccessAction
    ): Boolean {
        if (!module.manager.canViewWallEntrySync(viewerUid, entry, viewerUid == entry.ownerUid || viewerUid == entry.authorUid)) {
            return false
        }
        if (viewerUid == entry.ownerUid || viewerUid == entry.authorUid) {
            return true
        }
        return canAccessWall(plugin, friendModule, viewerUid, entry.ownerUid, action)
    }

    private fun canAccessWall(plugin: CyufriendsReload, friendModule: FriendModule?, viewerUid: String, ownerUid: String, action: WallAccessAction): Boolean {
        if (ownerUid == viewerUid) {
            val allowed = plugin.config.getBoolean("wallPermissions.allow-self", true)
            if (!allowed) {
                DebugLogger.debug(1) { "留言墙访问拦截: viewer=$viewerUid owner=$ownerUid action=${action.name.lowercase()} reason=self-disallowed" }
            }
            return allowed
        }
        val path = when (action) {
            WallAccessAction.VIEW -> "wallPermissions.view-requires-friend"
            WallAccessAction.POST -> "wallPermissions.post-requires-friend"
            WallAccessAction.COMMENT -> "wallPermissions.comment-requires-friend"
            WallAccessAction.LIKE -> "wallPermissions.like-requires-friend"
        }
        val requireFriend = plugin.config.getBoolean(path, false)
        if (!requireFriend) return true
        val allowed = friendModule?.friendManager?.isFriendStable(viewerUid, ownerUid) == true
        if (!allowed) {
            DebugLogger.debug(1) {
                "留言墙访问拦截: viewer=$viewerUid owner=$ownerUid action=${action.name.lowercase()} reason=require-friend"
            }
        }
        return allowed
    }

    private fun defaultWallVisibility(plugin: CyufriendsReload): WallVisibility {
        return WallVisibility.fromValue(plugin.config.getString("wallPermissions.default-visibility")) ?: WallVisibility.PUBLIC
    }

    private fun notifyPendingWallOwner(plugin: CyufriendsReload, ownerUid: String, authorName: String, visibility: WallVisibility) {
        if (!plugin.config.getBoolean("wallModeration.notify-owner", true)) return
        CyuConcurrency.scheduler.runGlobal(plugin) {
            val owner = Bukkit.getOnlinePlayers().firstOrNull { it.uid == ownerUid } ?: return@runGlobal
            CyuConcurrency.scheduler.runEntity(plugin, owner) {
                owner.sendLang("wall-pending-owner-notice", mapOf("author" to authorName, "visibility" to visibility.displayName))
            }
        }
    }

    private fun notifyPendingWallReplyOwner(plugin: CyufriendsReload, ownerUid: String, authorName: String) {
        if (!plugin.config.getBoolean("wallModeration.comment-notify-owner", plugin.config.getBoolean("wallModeration.notify-owner", true))) return
        CyuConcurrency.scheduler.runGlobal(plugin) {
            val owner = Bukkit.getOnlinePlayers().firstOrNull { it.uid == ownerUid } ?: return@runGlobal
            CyuConcurrency.scheduler.runEntity(plugin, owner) {
                owner.sendLang("wall-comment-pending-owner-notice", mapOf("author" to authorName))
            }
        }
    }

    private fun notifySocialInteraction(
        plugin: CyufriendsReload,
        targetUid: String,
        actorUid: String,
        actorName: String,
        type: SocialInteractionNoticeType,
        rawPreview: String? = null
    ) {
        if (targetUid == actorUid) return
        if (!plugin.config.getBoolean("socialNotifications.${type.configKey}", true)) return
        val profileModule = plugin.moduleManager.getModule<org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule>("profile")
        val globalEnabled = profileModule?.manager?.canReceiveSocialNoticeSync(targetUid, type) ?: true
        if (!globalEnabled) return
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        if (friendModule != null && !friendModule.preferencesManager.canReceiveSocialNoticeFromStoredSync(targetUid, actorUid, type, globalEnabled)) return
        val preview = interactionPreview(plugin, rawPreview)
        CyuConcurrency.scheduler.runGlobal(plugin) {
            val localTarget = Bukkit.getOnlinePlayers().firstOrNull { it.uid == targetUid }
            if (localTarget != null) {
                CyuConcurrency.scheduler.runEntity(plugin, localTarget) {
                    localTarget.sendLang(type.messageKey, mapOf("actor" to actorName, "preview" to preview))
                    localTarget.playAudio(type.soundKey)
                }
                return@runGlobal
            }
            plugin.moduleManager.getModule<ProxyModule>("proxy")
                ?.gateway
                ?.sendSocialInteractionNotify(targetUid, type.id, actorUid, actorName, preview.takeIf { it.isNotBlank() })
        }
    }

    private fun interactionPreview(plugin: CyufriendsReload, raw: String?): String {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) return ""
        val limit = plugin.config.getInt("socialNotifications.preview-length", 24).coerceAtLeast(8)
        return if (normalized.length <= limit) {
            normalized
        } else {
            normalized.take(limit) + "..."
        }
    }

    private fun refreshOpenSocialView(player: org.bukkit.entity.Player) {
        when (val holder = player.openInventory.topInventory.holder) {
            is StatusView -> holder.onRender()
            is StatusCommentsView -> holder.onRender()
            is NotificationCenterView -> holder.onRender()
            is WallView -> holder.onRender()
            is WallCommentsView -> holder.onRender()
            is WallPendingView -> {
                holder.invalidateCache()
                holder.onRender()
            }
            is WallCommentPendingView -> {
                holder.invalidateCache()
                holder.onRender()
            }
        }
    }

    private enum class WallAccessAction {
        VIEW,
        POST,
        COMMENT,
        LIKE
    }

    private fun stripChatListMode(args: List<String>): Pair<List<String>, Boolean> {
        val last = args.lastOrNull()?.trim()?.lowercase()
        return if (last == "chat" || last == "list" || last == "text") {
            args.dropLast(1) to true
        } else {
            args to false
        }
    }

    private fun filterCompletions(values: Iterable<String>, prefix: String): List<String> {
        val normalized = prefix.lowercase()
        return values.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it.lowercase().startsWith(normalized) }
            .sortedBy { it.lowercase() }
            .take(30)
            .toList()
    }
}

