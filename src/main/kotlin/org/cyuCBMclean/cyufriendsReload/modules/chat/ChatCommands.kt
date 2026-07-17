package org.cyuCBMclean.cyufriendsReload.modules.chat

import kotlinx.coroutines.runBlocking
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.command.CyuCommandNode
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.displayServerName
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRichMessages
import org.cyuCBMclean.cyufriendsReload.modules.chat.gui.MessagesView
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.PendingDirectMessage
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiLoader
import org.cyuCBMclean.cyufriendsReload.ui.view.ViewTitles

object ChatCommands {

    fun registerSubCommands(plugin: CyufriendsReload, module: ChatModule, root: CyuCommandNode) {

        root.subCommand("msg") {
            alias("m", "tell", "w")
            requirePlayer = true
            permission = "cyufriends.command.msg"
            onNotPlayer = { it.sendLang("only-player") }
            onNoPermission = { it.sendLang("no-permission") }

            executes {
                if (args.size < 2) return@executes player.sendLang("usage-msg")
                val targetName = args[0]
                val content = args.drop(1).joinToString(" ")
                val senderUid = player.uid
                val senderName = player.name
                val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                val targetPlayer = CyuIdHook.getOnlinePlayer(targetUid)
                val localTargetName = targetPlayer?.name ?: CyuIdHook.getName(targetUid) ?: targetName
                val localTargetOnline = targetPlayer?.isOnline == true
                val proxyModule = plugin.moduleManager.getModule<ProxyModule>("proxy")
                val remotePresence = proxyModule?.remotePresence?.find(targetUid)
                val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")

                if (senderUid == targetUid) return@executes player.sendLang("cannot-msg-self")

                CyuConcurrency.scheduler.runAsync(plugin) {
                    val rejectKey = rejectionKey(plugin, senderUid, targetUid)
                    if (rejectKey != null) {
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang(rejectKey)
                        }
                        return@runAsync
                    }

                    module.manager.setReplyTarget(senderUid, targetUid)

                    when {
                        localTargetOnline -> {
                            val result = module.manager.sendPlayerMessageSync(senderUid, targetUid, content, true)
                            val remaining = module.manager.remainingCooldown(senderUid).toString()
                            if (result == ChatSendResult.SUCCESS) {
                                friendModule?.let {
                                    it.friendManager.touchInteractionSync(senderUid, targetUid)
                                    it.timelineManager.recordInteractionSync(
                                        senderUid,
                                        targetUid,
                                        senderUid,
                                        RelationshipTimelineType.PRIVATE_MESSAGE,
                                        content.trim()
                                    )
                                }
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("msg-sent", mapOf("target" to localTargetName, "content" to content.trim()))
                                }
                                CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
                                    targetPlayer.sendLang("msg-received", mapOf("sender" to senderName, "content" to content.trim()))
                                    targetPlayer.playAudio("msg-received")
                                }
                            } else {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    when (result) {
                                        ChatSendResult.EMPTY -> player.sendLang("msg-empty")
                                        ChatSendResult.COOLDOWN -> player.sendLang("msg-cooldown", mapOf("seconds" to remaining))
                                        ChatSendResult.SUCCESS -> {}
                                    }
                                }
                            }
                        }

                        remotePresence != null -> {
                            val activeProxy = plugin.moduleManager.getModule<ProxyModule>("proxy") ?: return@runAsync
                            val prepared = module.manager.prepareOutgoing(senderUid, content)
                            val remaining = module.manager.remainingCooldown(senderUid).toString()
                            if (prepared.result != ChatSendResult.SUCCESS) {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    when (prepared.result) {
                                        ChatSendResult.EMPTY -> player.sendLang("msg-empty")
                                        ChatSendResult.COOLDOWN -> player.sendLang("msg-cooldown", mapOf("seconds" to remaining))
                                        ChatSendResult.SUCCESS -> {}
                                    }
                                }
                                return@runAsync
                            }

                            val clean = prepared.content ?: return@runAsync
                            val messageId = activeProxy.gateway.sendDirectMessage(senderUid, senderName, targetUid, clean)
                            if (messageId == null) {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("player-offline")
                                }
                                return@runAsync
                            }

                            module.manager.markSent(senderUid)
                            activeProxy.trackDirectMessage(
                                messageId,
                                PendingDirectMessage(
                                    senderUid = senderUid,
                                    targetUid = targetUid,
                                    targetName = remotePresence.name,
                                    targetServerName = plugin.displayServerName(remotePresence.serverId),
                                    content = clean
                                )
                            )
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang(
                                    "msg-remote-routing",
                                    mapOf(
                                        "target" to remotePresence.name,
                                        "server" to plugin.displayServerName(remotePresence.serverId),
                                        "content" to clean
                                    )
                                )
                            }
                        }

                        else -> {
                            val result = module.manager.sendPlayerMessageSync(senderUid, targetUid, content, false)
                            val remaining = module.manager.remainingCooldown(senderUid).toString()
                            if (result == ChatSendResult.SUCCESS) {
                                friendModule?.let {
                                    it.friendManager.touchInteractionSync(senderUid, targetUid)
                                    it.timelineManager.recordInteractionSync(
                                        senderUid,
                                        targetUid,
                                        senderUid,
                                        RelationshipTimelineType.PRIVATE_MESSAGE,
                                        content.trim()
                                    )
                                }
                            }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                when (result) {
                                    ChatSendResult.SUCCESS -> {
                                        player.sendLang("msg-offline-sent", mapOf("target" to targetName, "content" to content.trim()))
                                        player.playAudio("msg-sent")
                                    }
                                    ChatSendResult.EMPTY -> player.sendLang("msg-empty")
                                    ChatSendResult.COOLDOWN -> player.sendLang("msg-cooldown", mapOf("seconds" to remaining))
                                }
                            }
                        }
                    }
                }
            }

            tabComplete {
                if (!isPlayer) return@tabComplete emptyList()
                val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: return@tabComplete emptyList()
                filterCompletions(friendModule.friendManager.getFriendEntriesStoredSync(player.uid).map { CyuIdHook.getName(it.friendUid) ?: it.friendUid }, args.getOrElse(0) { "" })
            }
        }

        root.subCommand("reply") {
            alias("r")
            requirePlayer = true
            permission = "cyufriends.command.reply"

            executes {
                if (args.isEmpty()) return@executes player.sendLang("usage-reply")
                val content = args.joinToString(" ")
                val senderUid = player.uid
                val senderName = player.name
                val targetUid = module.manager.getReplyTarget(senderUid) ?: return@executes player.sendLang("no-reply-target")
                val proxyModule = plugin.moduleManager.getModule<ProxyModule>("proxy")
                val remotePresence = proxyModule?.remotePresence?.find(targetUid)
                val targetPlayer = CyuIdHook.getOnlinePlayer(targetUid)
                val localTargetName = targetPlayer?.name
                val localTargetOnline = targetPlayer != null && targetPlayer.isOnline
                val targetName = localTargetName ?: remotePresence?.name ?: CyuIdHook.getName(targetUid) ?: "未知玩家"
                val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")

                CyuConcurrency.scheduler.runAsync(plugin) {
                    val rejectKey = rejectionKey(plugin, senderUid, targetUid)
                    if (rejectKey != null) {
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang(rejectKey)
                        }
                        return@runAsync
                    }

                    module.manager.setReplyTarget(senderUid, targetUid)

                    when {
                        localTargetOnline -> {
                            val result = module.manager.sendPlayerMessageSync(senderUid, targetUid, content, true)
                            val remaining = module.manager.remainingCooldown(senderUid).toString()
                            if (result == ChatSendResult.SUCCESS) {
                                friendModule?.let {
                                    it.friendManager.touchInteractionSync(senderUid, targetUid)
                                    it.timelineManager.recordInteractionSync(
                                        senderUid,
                                        targetUid,
                                        senderUid,
                                        RelationshipTimelineType.PRIVATE_MESSAGE,
                                        content.trim()
                                    )
                                }
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("msg-sent", mapOf("target" to (localTargetName ?: targetName), "content" to content.trim()))
                                }
                                CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
                                    targetPlayer.sendLang("msg-received", mapOf("sender" to senderName, "content" to content.trim()))
                                    targetPlayer.playAudio("msg-received")
                                }
                            } else {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    when (result) {
                                        ChatSendResult.EMPTY -> player.sendLang("msg-empty")
                                        ChatSendResult.COOLDOWN -> player.sendLang("msg-cooldown", mapOf("seconds" to remaining))
                                        ChatSendResult.SUCCESS -> {}
                                    }
                                }
                            }
                        }

                        remotePresence != null -> {
                            val activeProxy = plugin.moduleManager.getModule<ProxyModule>("proxy") ?: return@runAsync
                            val prepared = module.manager.prepareOutgoing(senderUid, content)
                            val remaining = module.manager.remainingCooldown(senderUid).toString()
                            if (prepared.result != ChatSendResult.SUCCESS) {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    when (prepared.result) {
                                        ChatSendResult.EMPTY -> player.sendLang("msg-empty")
                                        ChatSendResult.COOLDOWN -> player.sendLang("msg-cooldown", mapOf("seconds" to remaining))
                                        ChatSendResult.SUCCESS -> {}
                                    }
                                }
                                return@runAsync
                            }

                            val clean = prepared.content ?: return@runAsync
                            val messageId = activeProxy.gateway.sendDirectMessage(senderUid, senderName, targetUid, clean)
                            if (messageId == null) {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("player-offline")
                                }
                                return@runAsync
                            }

                            module.manager.markSent(senderUid)
                            activeProxy.trackDirectMessage(
                                messageId,
                                PendingDirectMessage(
                                    senderUid = senderUid,
                                    targetUid = targetUid,
                                    targetName = targetName,
                                    targetServerName = plugin.displayServerName(remotePresence.serverId),
                                    content = clean
                                )
                            )
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang(
                                    "msg-remote-routing",
                                    mapOf(
                                        "target" to targetName,
                                        "server" to plugin.displayServerName(remotePresence.serverId),
                                        "content" to clean
                                    )
                                )
                            }
                        }

                        else -> {
                            val result = module.manager.sendPlayerMessageSync(senderUid, targetUid, content, false)
                            val remaining = module.manager.remainingCooldown(senderUid).toString()
                            if (result == ChatSendResult.SUCCESS) {
                                friendModule?.let {
                                    it.friendManager.touchInteractionSync(senderUid, targetUid)
                                    it.timelineManager.recordInteractionSync(
                                        senderUid,
                                        targetUid,
                                        senderUid,
                                        RelationshipTimelineType.PRIVATE_MESSAGE,
                                        content.trim()
                                    )
                                }
                            }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                when (result) {
                                    ChatSendResult.SUCCESS -> {
                                        player.sendLang("msg-offline-sent", mapOf("target" to targetName, "content" to content.trim()))
                                        player.playAudio("msg-sent")
                                    }
                                    ChatSendResult.EMPTY -> player.sendLang("msg-empty")
                                    ChatSendResult.COOLDOWN -> player.sendLang("msg-cooldown", mapOf("seconds" to remaining))
                                }
                            }
                        }
                    }
                }
            }
        }

        root.subCommand("messages") {
            requirePlayer = true
            permission = "cyufriends.command.messages"

            executes {
                val action = getArg(0)?.lowercase()
                if (action == "remove" || action == "clear") {
                    val ownerUid = player.uid
                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val amount = module.manager.clearUnreadSync(ownerUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("messages-cleared", mapOf("amount" to amount.toString()))
                            player.playAudio("success")
                        }
                    }
                    return@executes
                }

                if (action == "chat" || action == "list" || action == "text") {
                    showConversationsInChat(plugin, module, player)
                    return@executes
                }

                if (action == "read" || action == "mark" || action == "seen") {
                    val targetInput = args.drop(1).joinToString(" ").trim()
                    if (targetInput.isBlank()) return@executes player.sendLang("usage-messages")
                    val targetUid = resolveChatTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                    val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                    val ownerUid = player.uid
                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val amount = module.manager.clearUnreadFromSenderSync(ownerUid, targetUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (amount > 0) {
                                player.sendLang("messages-conversation-read", mapOf("target" to targetName, "amount" to amount.toString()))
                                player.playAudio("success")
                            } else {
                                player.sendLang("messages-conversation-read-empty", mapOf("target" to targetName))
                            }
                        }
                    }
                    return@executes
                }

                val guiData = GuiLoader.load(plugin, "messages_list.yml") ?: return@executes player.sendLang("gui-open-failed")
                val title = guiData.resolveTitle(player, ViewTitles.unreadMessages())
                MessagesView(player, guiData.pattern, guiData.items, module, title).open()
            }

            tabComplete {
                when (args.size) {
                    0, 1 -> filterCompletions(listOf("clear", "remove", "read", "mark", "seen", "chat", "list", "text"), args.getOrElse(0) { "" })
                    2 -> when (args[0].lowercase()) {
                        "read", "mark", "seen" -> conversationTargetCompletions(module, player.uid, args[1])
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
            }
        }
    }

    private fun showConversationsInChat(plugin: CyufriendsReload, module: ChatModule, player: org.bukkit.entity.Player) {
        val ownerUid = player.uid
        CyuConcurrency.scheduler.runAsync(plugin) {
            val conversations = module.manager.getConversationSummariesSync(ownerUid)
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                if (conversations.isEmpty()) {
                    player.sendLang("messages-chat-empty")
                    return@runEntity
                }
                player.sendLang("messages-chat-header", mapOf("amount" to conversations.size.toString()))
                conversations.take(8).forEach { summary ->
                    val partnerName = CyuIdHook.getName(summary.partnerUid) ?: summary.partnerUid
                    FriendRichMessages.sendConversationEntry(player, summary, partnerName)
                }
                if (conversations.size > 8) {
                    player.sendLang("messages-chat-more", mapOf("amount" to (conversations.size - 8).toString()))
                }
            }
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

    private fun conversationTargetCompletions(module: ChatModule, playerUid: String, prefix: String): List<String> {
        val values = module.manager.conversationSummariesCached(playerUid)
            .ifEmpty { module.manager.getConversationSummariesSync(playerUid) }
            .map { CyuIdHook.getName(it.partnerUid) ?: it.partnerUid }
        return filterCompletions(values, prefix)
    }

    private fun resolveChatTarget(value: String): String? {
        return CyuIdHook.getUidByName(value)
            ?: value.takeIf { CyuIdHook.getName(it) != null }
    }

    private fun rejectionKey(plugin: CyufriendsReload, senderUid: String, targetUid: String): String? {
        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
        if (profileModule != null && !runBlocking { profileModule.manager.canReceiveMsgStored(targetUid) }) {
            DebugLogger.debug(1) { "私聊拦截: sender=$senderUid target=$targetUid reason=target-msg-disabled" }
            return "target-msg-disabled"
        }

        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        if (friendModule != null) {
            if (runBlocking { friendModule.blockManager.isBlockedStored(targetUid, senderUid) }) {
                DebugLogger.debug(1) { "私聊拦截: sender=$senderUid target=$targetUid reason=blocked-by-target" }
                return "blocked-by-target"
            }
            if (!runBlocking { friendModule.friendManager.isFriendStored(senderUid, targetUid) }) {
                DebugLogger.debug(1) { "私聊拦截: sender=$senderUid target=$targetUid reason=msg-friend-only" }
                return "msg-friend-only"
            }
        }

        return null
    }
}
