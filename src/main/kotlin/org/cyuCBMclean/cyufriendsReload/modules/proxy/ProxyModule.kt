package org.cyuCBMclean.cyufriendsReload.modules.proxy

import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.module.CyuModule
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuTask
import org.cyuCBMclean.cyufriendsReload.extension.displayServerName
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRichMessages
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendTeleportMode
import org.cyuCBMclean.cyufriendsReload.modules.friend.RelationshipTimelineType
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialInteractionNoticeType
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyuidReload.CyuidReload
import java.util.concurrent.ConcurrentHashMap

data class PendingDirectMessage(
    val senderUid: String,
    val targetUid: String,
    val targetName: String,
    val targetServerName: String?,
    val content: String
)

private data class PendingDirectDelivery(
    val message: PendingDirectMessage,
    val timeoutTask: CyuTask
)

private data class PendingTeleportPrecheck(
    val requesterUid: String,
    val timeoutTask: CyuTask
)

class ProxyModule(
    val plugin: CyufriendsReload
) : CyuModule, Listener, PluginMessageListener {

    override val moduleId = "proxy"

    var settings: ProxySettings? = null
        private set
    lateinit var signer: ProxySigner
        private set
    lateinit var gateway: ProxyGateway
        private set
    private lateinit var messageDeduplicator: MessageDeduplicator
    val remotePresence = RemotePresenceDirectory()
    private val localPlayers = ConcurrentHashMap<String, Player>()
    private val pendingDirectMessages = ConcurrentHashMap<String, PendingDirectDelivery>()
    private val pendingTeleportPrechecks = ConcurrentHashMap<String, PendingTeleportPrecheck>()

    private var presenceRefreshTask: CyuTask? = null
    private var snapshotRequested = false
    private var lastSendAt = 0L
    private var lastSendFailureAt = 0L
    private var lastSendFailure = ""
    private var lastReceiveAt = 0L
    private var lastReceiveFailureAt = 0L
    private var lastReceiveFailure = ""

    override fun onEnable() {
        settings = ProxySettings.from(plugin.config)
        signer = ProxySigner(settings!!.secret)
        messageDeduplicator = MessageDeduplicator(settings!!.maxClockSkewSeconds * 2_000L)
        gateway = ProxyGateway(this)
        DebugLogger.debug(1) {
            "Proxy 模块初始化: enabled=${settings!!.enabled}, server=${settings!!.serverId}, channel=${settings!!.channel}"
        }
        Bukkit.getPluginManager().registerEvents(this, plugin)
        if (settings!!.enabled && !Settings.databaseType.equals("mysql", ignoreCase = true)) {
            plugin.logger.severe("cyufriends-reload proxy 已阻止启动：跨服模式要求 database.type 使用 MySQL 并由所有后端共用。")
            settings = settings!!.copy(enabled = false)
        } else if (settings!!.enabled && !cyuIdStorageIsCompatible()) {
            plugin.logger.severe("cyufriends-reload proxy 已阻止启动：已安装的 cyuid-reload 必须使用共享 MySQL 存储。")
            settings = settings!!.copy(enabled = false)
        } else if (settings!!.enabled && settings!!.hasSecureSecret()) {
            registerChannels(settings!!.channel)
            publishLocalPresence()
            startPresenceRefresh(settings!!)
        } else if (settings!!.enabled) {
            plugin.logger.severe("cyufriends-reload proxy 已阻止启动：proxy.secret 仍为默认值，请先修改配置。")
            settings = settings!!.copy(enabled = false)
        }
    }

    private fun cyuIdStorageIsCompatible(): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("cyuid-reload")) return true
        return runCatching { CyuidReload.Companion.api.usesSharedStorage() }.getOrDefault(false)
    }

    override fun onDisable() {
        settings?.takeIf { it.enabled }?.let { unregisterChannels(it.channel) }
        presenceRefreshTask?.cancel()
        presenceRefreshTask = null
        pendingDirectMessages.values.forEach { it.timeoutTask.cancel() }
        pendingDirectMessages.clear()
        pendingTeleportPrechecks.values.forEach { it.timeoutTask.cancel() }
        pendingTeleportPrechecks.clear()
        snapshotRequested = false
        localPlayers.clear()
        HandlerList.unregisterAll(this)
        DebugLogger.debug(1) { "Proxy 模块已关闭，缓存与挂起请求已清空。" }
    }

    override fun reloadConfig() {
        settings?.takeIf { it.enabled }?.let { unregisterChannels(it.channel) }
        presenceRefreshTask?.cancel()
        presenceRefreshTask = null
        pendingDirectMessages.values.forEach { it.timeoutTask.cancel() }
        pendingDirectMessages.clear()
        pendingTeleportPrechecks.values.forEach { it.timeoutTask.cancel() }
        pendingTeleportPrechecks.clear()
        snapshotRequested = false
        localPlayers.clear()
        settings = ProxySettings.from(plugin.config)
        signer = ProxySigner(settings!!.secret)
        messageDeduplicator = MessageDeduplicator(settings!!.maxClockSkewSeconds * 2_000L)
        gateway = ProxyGateway(this)
        DebugLogger.debug(1) {
            "Proxy 模块重载: enabled=${settings!!.enabled}, server=${settings!!.serverId}, channel=${settings!!.channel}"
        }
        if (settings!!.enabled && settings!!.hasSecureSecret()) {
            registerChannels(settings!!.channel)
            publishLocalPresence()
            startPresenceRefresh(settings!!)
        } else if (settings!!.enabled) {
            plugin.logger.severe("cyufriends-reload proxy 已阻止重载启用：proxy.secret 仍为默认值，请先修改配置。")
            settings = settings!!.copy(enabled = false)
        }
    }
    fun acceptMessageId(messageId: String): Boolean = messageDeduplicator.mark(messageId)


    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        localPlayers[event.player.uid] = event.player
        val current = settings ?: return
        if (!current.enabled) return
        if (!snapshotRequested) {
            gateway.requestSnapshot()
            snapshotRequested = true
        }
        val headSource = resolveHeadSource(event.player)
        DebugLogger.debug(2) { "Proxy 跟踪玩家加入: ${event.player.name}(${event.player.uid})" }
        gateway.publishJoin(event.player.uid, event.player.name, headSource)
        gateway.publishHeadSource(event.player.uid, headSource)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val uid = event.player.uid
        val current = settings
        if (current?.enabled == true) {
            DebugLogger.debug(2) { "Proxy 跟踪玩家离开: ${event.player.name}($uid)" }
            gateway.publishQuit(uid, event.player)
        }
        localPlayers.remove(uid)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        val current = settings ?: return
        if (!current.enabled || channel != current.channel) return
        DebugLogger.debug(2) { "Proxy 收到原始消息: channel=$channel, player=${player.name}, bytes=${message.size}" }
        val envelope = gateway.decode(message) ?: return
        if (envelope.sourceServer == current.serverId) return
        handleIncoming(envelope)
    }

    private fun handleIncoming(envelope: ProxyEnvelope) {
        DebugLogger.debug(2) {
            "Proxy 分发消息: type=${envelope.type.id}, source=${envelope.sourceServer}, subject=${envelope.subjectUid ?: "-"}"
        }
        when (envelope.type) {
            ProxyMessageType.PRESENCE_JOIN -> {
                val uid = envelope.subjectUid ?: return
                val name = JsonPayloads.stringValue(envelope.payloadJson, "name") ?: uid
                val headSource = JsonPayloads.stringValue(envelope.payloadJson, "headSource")
                remotePresence.online(RemotePresence(uid, name, envelope.sourceServer, headSource, envelope.timestamp, envelope.timestamp))
                CyuConcurrency.scheduler.runAsync(plugin) {
                    notifyRemoteOnlineState(uid, name, true)
                }
            }

            ProxyMessageType.PRESENCE_QUIT -> {
                val uid = envelope.subjectUid ?: return
                val name = remotePresence.find(uid)?.name ?: uid
                remotePresence.offline(uid)
                CyuConcurrency.scheduler.runAsync(plugin) {
                    notifyRemoteOnlineState(uid, name, false)
                }
            }

            ProxyMessageType.PRESENCE_SNAPSHOT_FULL -> {
                remotePresence.replaceAll(JsonPayloads.presenceSnapshot(envelope.payloadJson))
            }

            ProxyMessageType.IDENTITY_HEAD_UPDATED -> {
                envelope.subjectUid?.let { uid ->
                    remotePresence.updateHead(uid, JsonPayloads.stringValue(envelope.payloadJson, "headSource"))
                }
            }

            ProxyMessageType.IDENTITY_UID_CHANGED -> {
                val oldUid = envelope.subjectUid ?: return
                val newUid = JsonPayloads.stringValue(envelope.payloadJson, "newUid") ?: return
                remotePresence.moveUid(oldUid, newUid)
                invalidateRelation(oldUid)
                invalidateProfile(oldUid)
                invalidateRequest(oldUid)
                invalidateSettings(oldUid)
                invalidateStatus(oldUid)
                invalidateWall(oldUid)
            }

            ProxyMessageType.CACHE_INVALIDATE_RELATION -> envelope.subjectUid?.let(::invalidateRelation)
            ProxyMessageType.CACHE_INVALIDATE_PROFILE -> envelope.subjectUid?.let(::invalidateProfile)
            ProxyMessageType.CACHE_INVALIDATE_REQUEST -> envelope.subjectUid?.let(::invalidateRequest)
            ProxyMessageType.CACHE_INVALIDATE_SETTINGS -> envelope.subjectUid?.let(::invalidateSettings)
            ProxyMessageType.CACHE_INVALIDATE_MESSAGE -> {}
            ProxyMessageType.CACHE_INVALIDATE_STATUS -> invalidateStatus(envelope.subjectUid)
            ProxyMessageType.CACHE_INVALIDATE_WALL -> envelope.subjectUid?.let(::invalidateWall)
            ProxyMessageType.CHAT_DIRECT_DELIVER -> handleDirectDeliver(envelope)
            ProxyMessageType.CHAT_DIRECT_ACK -> handleDirectAck(envelope)
            ProxyMessageType.CHAT_DIRECT_REJECT -> handleDirectReject(envelope)
            ProxyMessageType.FRIEND_REQUEST_NOTIFY -> handleFriendRequestNotify(envelope)
            ProxyMessageType.FRIEND_REQUEST_ACCEPTED -> handleFriendRequestAccepted(envelope)
            ProxyMessageType.FRIEND_REQUEST_DENIED -> handleFriendRequestDenied(envelope)
            ProxyMessageType.FRIEND_REQUEST_REVOKED -> handleFriendRequestRevoked(envelope)
            ProxyMessageType.NOTIFY_SOCIAL_INTERACTION -> handleSocialInteractionNotify(envelope)
            ProxyMessageType.NOTIFY_BIRTHDAY -> handleBirthdayNotify(envelope)
            ProxyMessageType.TELEPORT_PRECHECK -> handleTeleportPrecheck(envelope)
            ProxyMessageType.TELEPORT_PRECHECK_RESULT -> handleTeleportPrecheckResult(envelope)
            ProxyMessageType.TELEPORT_REQUEST -> handleTeleportRequest(envelope)
            ProxyMessageType.TELEPORT_EXECUTE -> handleTeleportExecute(envelope)
            ProxyMessageType.TELEPORT_EXECUTE_ACK -> {}
            ProxyMessageType.TELEPORT_FAIL -> handleTeleportFail(envelope)
            else -> {}
        }
    }

    fun trackDirectMessage(messageId: String, pending: PendingDirectMessage) {
        val timeoutSeconds = settings?.directMessageTimeoutSeconds ?: 8L
        DebugLogger.debug(1) {
            "跟踪跨服私聊回执: id=$messageId, sender=${pending.senderUid}, target=${pending.targetUid}, timeout=${timeoutSeconds}s"
        }
        val timeoutTask = CyuConcurrency.scheduler.runLaterAsync(plugin, timeoutSeconds * 20L) {
            val timedOut = pendingDirectMessages.remove(messageId)?.message ?: return@runLaterAsync
            val sender = localPlayers[timedOut.senderUid] ?: return@runLaterAsync
            CyuConcurrency.scheduler.runEntity(plugin, sender) {
                sender.sendLang(
                    "msg-remote-timeout",
                    mapOf(
                        "target" to timedOut.targetName,
                        "server" to (timedOut.targetServerName ?: plugin.displayServerName(remotePresence.find(timedOut.targetUid)?.serverId))
                    )
                )
            }
        }
        pendingDirectMessages[messageId] = PendingDirectDelivery(pending, timeoutTask)
    }

    fun remoteOnlineCount(): Int = remotePresence.count()

    fun remoteServerBreakdown(): Map<String, Int> = remotePresence.countByServer()

    fun pendingDirectCount(): Int = pendingDirectMessages.size

    fun pendingTeleportPrecheckCount(): Int = pendingTeleportPrechecks.size

    fun hasRequestedSnapshot(): Boolean = snapshotRequested

    fun recordProxySend() {
        lastSendAt = System.currentTimeMillis()
    }

    fun recordProxySendFailure(reason: String) {
        lastSendFailureAt = System.currentTimeMillis()
        lastSendFailure = reason
    }

    fun recordProxyReceive() {
        lastReceiveAt = System.currentTimeMillis()
    }

    fun recordProxyReceiveFailure(reason: String) {
        lastReceiveFailureAt = System.currentTimeMillis()
        lastReceiveFailure = reason
    }

    fun proxyDiagnostics(): List<String> {
        val current = settings
        val secretState = when {
            current == null -> "未加载"
            !current.enabled -> "未启用"
            current.hasSecureSecret() -> "已设置"
            else -> "仍为默认值"
        }
        val carrier = localPlayerCarrier()
        return listOf(
            "§7协议版本: §f${current?.protocolVersion ?: "未加载"}  §7频道: §f${current?.channel ?: "未加载"}",
            "§7共享密钥: §f$secretState  §7发包载体: §f${carrier?.name ?: "无在线玩家"}",
            "§7最近发包: §f${formatProxyTime(lastSendAt)}  §7最近收包: §f${formatProxyTime(lastReceiveAt)}",
            "§7最近发包失败: §f${formatProxyIssue(lastSendFailureAt, lastSendFailure)}",
            "§7最近收包失败: §f${formatProxyIssue(lastReceiveFailureAt, lastReceiveFailure)}"
        )
    }

    fun trackTeleportPrecheck(messageId: String, requesterUid: String) {
        val timeoutSeconds = settings?.teleportPrecheckTimeoutSeconds ?: 6L
        DebugLogger.debug(1) {
            "跟踪跨服传送预检: id=$messageId, requester=$requesterUid, timeout=${timeoutSeconds}s"
        }
        val timeoutTask = CyuConcurrency.scheduler.runLaterAsync(plugin, timeoutSeconds * 20L) {
            val timedOut = pendingTeleportPrechecks.remove(messageId) ?: return@runLaterAsync
            val requester = localPlayers[timedOut.requesterUid] ?: return@runLaterAsync
            CyuConcurrency.scheduler.runEntity(plugin, requester) {
                requester.sendLang("tp-precheck-timeout")
            }
        }
        pendingTeleportPrechecks[messageId] = PendingTeleportPrecheck(requesterUid, timeoutTask)
    }

    private fun handleDirectDeliver(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        val senderUid = JsonPayloads.stringValue(envelope.payloadJson, "senderUid") ?: return
        val senderName = JsonPayloads.stringValue(envelope.payloadJson, "senderName") ?: senderUid
        val content = JsonPayloads.stringValue(envelope.payloadJson, "content") ?: return
        val targetPlayer = localPlayers[targetUid]
        if (targetPlayer == null) {
            gateway.sendDirectReject(envelope.sourceServer, senderUid, envelope.messageId, "offline")
            return
        }

        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        CyuConcurrency.scheduler.runAsync(plugin) {
            if (profileModule != null && !runBlocking { profileModule.manager.canReceiveMsgStored(targetUid) }) {
                gateway.sendDirectReject(envelope.sourceServer, senderUid, envelope.messageId, "msg-disabled")
                return@runAsync
            }
            if (friendModule != null) {
                if (runBlocking { friendModule.blockManager.isBlockedStored(targetUid, senderUid) }) {
                    gateway.sendDirectReject(envelope.sourceServer, senderUid, envelope.messageId, "blocked")
                    return@runAsync
                }
                if (!runBlocking { friendModule.friendManager.isFriendStored(senderUid, targetUid) }) {
                    gateway.sendDirectReject(envelope.sourceServer, senderUid, envelope.messageId, "not-friend")
                    return@runAsync
                }
            }

            val chatModule = plugin.moduleManager.getModule<ChatModule>("chat") ?: run {
                gateway.sendDirectReject(envelope.sourceServer, senderUid, envelope.messageId, "unavailable")
                return@runAsync
            }
            runBlocking { chatModule.manager.logOnlineMessage(senderUid, targetUid, content) }
            chatModule.manager.setReplyTarget(targetUid, senderUid)
            friendModule?.friendManager?.touchInteractionSync(senderUid, targetUid)
            friendModule?.timelineManager?.recordInteractionSync(
                senderUid,
                targetUid,
                senderUid,
                RelationshipTimelineType.PRIVATE_MESSAGE,
                content
            )
            gateway.invalidateMessage(senderUid, targetUid)
            CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
                targetPlayer.sendLang("msg-received", mapOf("sender" to senderName, "content" to content))
                targetPlayer.playAudio("msg-received")
            }
            gateway.sendDirectAck(envelope.sourceServer, senderUid, envelope.messageId)
        }
    }

    private fun handleDirectAck(envelope: ProxyEnvelope) {
        val pending = envelope.correlationId?.let { pendingDirectMessages.remove(it) } ?: return
        pending.timeoutTask.cancel()
        val sender = localPlayers[pending.message.senderUid] ?: return
        CyuConcurrency.scheduler.runEntity(plugin, sender) {
            sender.sendLang(
                "msg-remote-delivered",
                mapOf(
                    "target" to pending.message.targetName,
                    "server" to (pending.message.targetServerName ?: plugin.displayServerName(remotePresence.find(pending.message.targetUid)?.serverId)),
                    "content" to pending.message.content
                )
            )
            sender.playAudio("msg-sent")
        }
    }

    private fun handleDirectReject(envelope: ProxyEnvelope) {
        val pending = envelope.correlationId?.let { pendingDirectMessages.remove(it) } ?: return
        pending.timeoutTask.cancel()
        val sender = localPlayers[pending.message.senderUid] ?: return
        val reason = JsonPayloads.stringValue(envelope.payloadJson, "reason") ?: "offline"

        if (reason == "offline") {
            val chatModule = plugin.moduleManager.getModule<ChatModule>("chat") ?: return
            val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
            CyuConcurrency.scheduler.runAsync(plugin) {
                runBlocking { chatModule.manager.sendOfflineMessage(pending.message.senderUid, pending.message.targetUid, pending.message.content) }
                friendModule?.friendManager?.touchInteractionSync(pending.message.senderUid, pending.message.targetUid)
                friendModule?.timelineManager?.recordInteractionSync(
                    pending.message.senderUid,
                    pending.message.targetUid,
                    pending.message.senderUid,
                    RelationshipTimelineType.PRIVATE_MESSAGE,
                    pending.message.content
                )
                gateway.invalidateMessage(pending.message.senderUid, pending.message.targetUid)
                CyuConcurrency.scheduler.runEntity(plugin, sender) {
                    sender.sendLang("msg-offline-sent", mapOf("target" to pending.message.targetName, "content" to pending.message.content))
                    sender.playAudio("msg-sent")
                }
            }
            return
        }

        CyuConcurrency.scheduler.runEntity(plugin, sender) {
            when (reason) {
                "blocked" -> sender.sendLang("blocked-by-target")
                "msg-disabled" -> sender.sendLang("target-msg-disabled")
                "not-friend" -> sender.sendLang("msg-friend-only")
                else -> sender.sendLang("player-offline")
            }
        }
    }

    private fun handleFriendRequestNotify(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        val targetPlayer = localPlayers[targetUid] ?: return
        val senderUid = JsonPayloads.stringValue(envelope.payloadJson, "senderUid")
            ?: JsonPayloads.stringValue(envelope.payloadJson, "senderName")
            ?: return
        val senderName = JsonPayloads.stringValue(envelope.payloadJson, "senderName")
            ?: senderUid
            ?: return
        val note = JsonPayloads.stringValue(envelope.payloadJson, "note")
        invalidateRequest(targetUid)
        CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
            FriendRichMessages.sendFriendRequestPrompt(targetPlayer, senderName, senderUid, note)
            targetPlayer.playAudio("request-received")
        }
    }

    private fun handleFriendRequestAccepted(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        invalidateRequest(targetUid)
        invalidateRelation(targetUid)
        val targetPlayer = localPlayers[targetUid] ?: return
        val actorName = JsonPayloads.stringValue(envelope.payloadJson, "actorName") ?: "对方"
        CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
            targetPlayer.sendLang("friend-added", mapOf("player" to actorName))
            targetPlayer.playAudio("friend-added")
        }
    }

    private fun handleFriendRequestDenied(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        invalidateRequest(targetUid)
        val targetPlayer = localPlayers[targetUid] ?: return
        val actorName = JsonPayloads.stringValue(envelope.payloadJson, "actorName") ?: "对方"
        CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
            targetPlayer.sendLang("request-denied-by", mapOf("player" to actorName))
        }
    }

    private fun handleFriendRequestRevoked(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        invalidateRequest(targetUid)
        val targetPlayer = localPlayers[targetUid] ?: return
        val actorName = JsonPayloads.stringValue(envelope.payloadJson, "actorName") ?: "对方"
        CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
            targetPlayer.sendLang("request-revoked-by", mapOf("player" to actorName))
        }
    }

    private fun handleBirthdayNotify(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        val targetPlayer = localPlayers[targetUid] ?: return
        val playerName = JsonPayloads.stringValue(envelope.payloadJson, "player") ?: return
        val daysAhead = JsonPayloads.intValue(envelope.payloadJson, "daysAhead") ?: 0
        CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
            if (plugin.config.getStringList("birthdayReminder.disabledWorlds").any { it.equals(targetPlayer.world.name, ignoreCase = true) }) {
                return@runEntity
            }
            if (daysAhead <= 0) {
                targetPlayer.sendLang("birthday-reminder-friend", mapOf("player" to playerName))
            } else {
                targetPlayer.sendLang("birthday-reminder-upcoming", mapOf("player" to playerName, "days" to daysAhead.toString()))
            }
            targetPlayer.playAudio("birthday-reminder-friend")
        }
    }

    private fun handleSocialInteractionNotify(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        val targetPlayer = localPlayers[targetUid] ?: return
        val type = SocialInteractionNoticeType.fromId(JsonPayloads.stringValue(envelope.payloadJson, "kind")) ?: return
        if (!plugin.config.getBoolean("socialNotifications.${type.configKey}", true)) return
        val profileModule = plugin.moduleManager.getModule<org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule>("profile")
        if (profileModule != null && !profileModule.manager.canReceiveSocialNoticeSync(targetUid, type)) return
        val actorUid = JsonPayloads.stringValue(envelope.payloadJson, "actorUid")
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        if (actorUid != null && friendModule != null) {
            val globalEnabled = profileModule?.manager?.canReceiveSocialNoticeSync(targetUid, type) ?: true
            if (!friendModule.preferencesManager.canReceiveSocialNoticeFromStoredSync(targetUid, actorUid, type, globalEnabled)) return
        }
        val actorName = JsonPayloads.stringValue(envelope.payloadJson, "actorName") ?: return
        val preview = JsonPayloads.stringValue(envelope.payloadJson, "preview").orEmpty()
        CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
            targetPlayer.sendLang(type.messageKey, mapOf("actor" to actorName, "preview" to preview))
            targetPlayer.playAudio(type.soundKey)
        }
    }

    private fun handleTeleportPrecheck(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        val requesterUid = JsonPayloads.stringValue(envelope.payloadJson, "requesterUid") ?: return
        val targetPlayer = localPlayers[targetUid]

        if (targetPlayer == null) {
            gateway.sendTeleportPrecheckResult(envelope.sourceServer, requesterUid, envelope.messageId, "offline")
            return
        }

        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: run {
            gateway.sendTeleportPrecheckResult(envelope.sourceServer, requesterUid, envelope.messageId, "failed")
            return
        }
        val targetName = targetPlayer.name
        val targetWorldName = targetPlayer.world.name

        CyuConcurrency.scheduler.runAsync(plugin) {
            val mode = runBlocking { friendModule.preferencesManager.resolveTeleportMode(targetUid, requesterUid) }
            val status = when {
                !runBlocking { friendModule.friendManager.isFriendStored(requesterUid, targetUid) } -> "not-friend"
                mode == FriendTeleportMode.DENY -> "not-allowed"
                plugin.config.getStringList("disabledWorlds").any { it.equals(targetWorldName, ignoreCase = true) } -> "world-disabled"
                else -> "ok"
            }

            if (status == "ok") {
                gateway.sendTeleportPrecheckResult(
                    envelope.sourceServer,
                    requesterUid,
                    envelope.messageId,
                    "ok",
                    targetUid,
                    targetName,
                    mode
                )
            } else {
                gateway.sendTeleportPrecheckResult(envelope.sourceServer, requesterUid, envelope.messageId, status)
            }
        }
    }

    private fun handleTeleportPrecheckResult(envelope: ProxyEnvelope) {
        val pending = envelope.correlationId?.let { pendingTeleportPrechecks.remove(it) } ?: return
        pending.timeoutTask.cancel()
        val requesterUid = envelope.subjectUid ?: pending.requesterUid
        val requester = localPlayers[requesterUid] ?: return
        val status = JsonPayloads.stringValue(envelope.payloadJson, "status") ?: "failed"

        if (status != "ok") {
            CyuConcurrency.scheduler.runEntity(plugin, requester) {
                sendTeleportFailure(requester, status, null)
            }
            return
        }

        val targetUid = JsonPayloads.stringValue(envelope.payloadJson, "targetUid") ?: return
        val targetName = JsonPayloads.stringValue(envelope.payloadJson, "targetName")
            ?: remotePresence.find(targetUid)?.name
            ?: targetUid
        val mode = FriendTeleportMode.fromId(JsonPayloads.stringValue(envelope.payloadJson, "mode")) ?: FriendTeleportMode.CONFIRM
        val serverName = plugin.displayServerName(envelope.sourceServer)
        val sent = gateway.sendTeleportRequest(requesterUid, requester.name, targetUid)
        val timeoutSeconds = plugin.moduleManager.getModule<FriendModule>("friend")
            ?.teleportManager
            ?.requestTimeoutSeconds()
            ?: 60L

        CyuConcurrency.scheduler.runEntity(plugin, requester) {
            if (sent == null) {
                requester.sendLang("tp-failed")
            } else {
                if (mode == FriendTeleportMode.DIRECT) {
                    requester.sendLang("tp-cross-server-direct", mapOf("target" to targetName, "server" to serverName))
                } else {
                    requester.sendLang("tp-precheck-passed", mapOf("target" to targetName, "server" to serverName))
                    requester.sendLang("tp-request-sent", mapOf("target" to targetName, "seconds" to timeoutSeconds.toString()))
                }
            }
        }
    }

    private fun handleTeleportRequest(envelope: ProxyEnvelope) {
        val targetUid = envelope.subjectUid ?: return
        val requesterUid = JsonPayloads.stringValue(envelope.payloadJson, "requesterUid") ?: return
        val requesterName = JsonPayloads.stringValue(envelope.payloadJson, "requesterName") ?: requesterUid
        val targetPlayer = localPlayers[targetUid]
        if (targetPlayer == null) {
            gateway.sendTeleportFail(requesterUid, "offline")
            return
        }
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: return
        val targetWorldName = targetPlayer.world.name
        val targetName = targetPlayer.name

        CyuConcurrency.scheduler.runAsync(plugin) {
            if (!runBlocking { friendModule.friendManager.isFriendStored(requesterUid, targetUid) }) {
                gateway.sendTeleportFail(requesterUid, "not-friend")
                return@runAsync
            }
            val mode = runBlocking { friendModule.preferencesManager.resolveTeleportMode(targetUid, requesterUid) }
            if (mode == FriendTeleportMode.DENY) {
                gateway.sendTeleportFail(requesterUid, "not-allowed")
                return@runAsync
            }
            if (plugin.config.getStringList("disabledWorlds").any { it.equals(targetWorldName, ignoreCase = true) }) {
                gateway.sendTeleportFail(requesterUid, "world-disabled")
                return@runAsync
            }

            if (mode == FriendTeleportMode.DIRECT) {
                val sent = gateway.sendTeleportTransfer(requesterUid, targetUid, targetName)
                if (sent == null) {
                    gateway.sendTeleportFail(requesterUid, "failed")
                }
                return@runAsync
            }

            val timeoutSeconds = friendModule.teleportManager.requestTimeoutSeconds()
            val request = friendModule.teleportManager.createRequest(requesterUid, requesterName, envelope.sourceServer)
            val queued = friendModule.teleportManager.sendRequest(targetUid, request) { expired ->
                gateway.sendTeleportFail(expired.senderUid, "expired", targetName)
                val refreshedTarget = localPlayers[targetUid]
                if (refreshedTarget != null) {
                    CyuConcurrency.scheduler.runEntity(plugin, refreshedTarget) {
                        refreshedTarget.sendLang("tp-request-expired-received", mapOf("sender" to expired.senderName))
                    }
                }
            }
            if (!queued) {
                gateway.sendTeleportFail(requesterUid, "pending", targetName)
                return@runAsync
            }

            CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
                FriendRichMessages.sendTeleportRequestPrompt(targetPlayer, requesterName, timeoutSeconds)
                targetPlayer.playAudio("tp-request-received")
            }
        }
    }

    private fun handleTeleportExecute(envelope: ProxyEnvelope) {
        val requesterUid = envelope.subjectUid ?: return
        val targetUid = JsonPayloads.stringValue(envelope.payloadJson, "targetUid") ?: return
        val targetName = JsonPayloads.stringValue(envelope.payloadJson, "targetName") ?: targetUid
        val correlationId = envelope.correlationId
        executeTeleport(requesterUid, targetUid, targetName, correlationId, 0)
    }

    private fun executeTeleport(requesterUid: String, targetUid: String, targetName: String, correlationId: String?, attempt: Int) {
        val requester = localPlayers[requesterUid]
        if (requester == null) {
            if (attempt >= 10) {
                correlationId?.let { gateway.sendTeleportExecuteAck(it, "failed") }
                return
            }
            CyuConcurrency.scheduler.runLaterAsync(plugin, 2L) {
                executeTeleport(requesterUid, targetUid, targetName, correlationId, attempt + 1)
            }
            return
        }

        val target = localPlayers[targetUid]
        if (target == null) {
            CyuConcurrency.scheduler.runEntity(plugin, requester) {
                requester.sendLang("player-offline")
            }
            correlationId?.let { gateway.sendTeleportExecuteAck(it, "failed") }
            return
        }

        CyuConcurrency.scheduler.runEntity(plugin, target) {
            if (plugin.config.getStringList("disabledWorlds").any { it.equals(target.world.name, ignoreCase = true) }) {
                CyuConcurrency.scheduler.runEntity(plugin, requester) {
                    requester.sendLang("tp-world-disabled")
                }
                correlationId?.let { gateway.sendTeleportExecuteAck(it, "failed") }
                return@runEntity
            }

            CyuConcurrency.scheduler.runEntity(plugin, requester) {
                requester.teleportAsync(target.location).thenAccept { success ->
                    if (success) {
                        requester.sendLang("tp-success", mapOf("target" to targetName))
                        correlationId?.let { gateway.sendTeleportExecuteAck(it, "success") }
                    } else {
                        requester.sendLang("tp-failed")
                        correlationId?.let { gateway.sendTeleportExecuteAck(it, "failed") }
                    }
                }
            }
        }
    }

    private fun handleTeleportFail(envelope: ProxyEnvelope) {
        val requesterUid = envelope.subjectUid ?: return
        val requester = localPlayers[requesterUid] ?: return
        val reason = JsonPayloads.stringValue(envelope.payloadJson, "reason") ?: "failed"
        val actorName = JsonPayloads.stringValue(envelope.payloadJson, "actorName")
        CyuConcurrency.scheduler.runEntity(plugin, requester) {
            sendTeleportFailure(requester, reason, actorName)
        }
    }

    private fun invalidateRelation(uid: String) {
        plugin.moduleManager.getModule<FriendModule>("friend")?.let { friend ->
            friend.friendManager.invalidate(uid)
            friend.blockManager.invalidate(uid)
        }
    }

    private fun invalidateProfile(uid: String) {
        plugin.moduleManager.getModule<ProfileModule>("profile")?.manager?.invalidate(uid)
    }

    private fun invalidateRequest(uid: String) {
        plugin.moduleManager.getModule<FriendModule>("friend")?.requestManager?.invalidate(uid)
    }

    private fun invalidateSettings(uid: String) {
        plugin.moduleManager.getModule<FriendModule>("friend")?.preferencesManager?.invalidate(uid)
        plugin.moduleManager.getModule<ProfileModule>("profile")?.manager?.invalidate(uid)
    }

    private fun invalidateStatus(uid: String?) {
        plugin.moduleManager.getModule<SocialModule>("social")?.manager?.invalidateStatusCache(uid)
    }

    private fun invalidateWall(uid: String) {
        plugin.moduleManager.getModule<SocialModule>("social")?.manager?.invalidateWallCache(uid)
    }

    private fun registerChannels(channel: String) {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel)
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, this)
        DebugLogger.debug(1) { "Proxy 通道已注册: $channel" }
    }

    private fun unregisterChannels(channel: String) {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, channel)
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, channel, this)
        DebugLogger.debug(1) { "Proxy 通道已注销: $channel" }
    }

    private fun publishLocalPresence() {
        localPlayers.clear()
        if (!snapshotRequested) {
            gateway.requestSnapshot()
            snapshotRequested = true
        }
        DebugLogger.debug(1) { "开始发布本服在线快照，当前在线=${Bukkit.getOnlinePlayers().size}" }
        Bukkit.getOnlinePlayers().forEach { player ->
            localPlayers[player.uid] = player
            val headSource = resolveHeadSource(player)
            gateway.publishJoin(player.uid, player.name, headSource)
            gateway.publishHeadSource(player.uid, headSource)
        }
    }

    private fun startPresenceRefresh(current: ProxySettings) {
        presenceRefreshTask?.cancel()
        val periodTicks = current.presenceRefreshSeconds * 20L
        presenceRefreshTask = CyuConcurrency.scheduler.runTimerAsync(plugin, periodTicks, periodTicks) {
            if (settings?.enabled != true) return@runTimerAsync
            Bukkit.getOnlinePlayers().forEach { player ->
                localPlayers[player.uid] = player
                val headSource = resolveHeadSource(player)
                gateway.publishJoin(player.uid, player.name, headSource)
            }
        }
        DebugLogger.debug(1) { "Proxy 在线刷新已启动，间隔=${current.presenceRefreshSeconds}s" }
    }

    fun localPlayerCarrier(): Player? = localPlayers.values.firstOrNull()

    fun sendPluginMessage(carrier: Player, payload: ByteArray) {
        CyuConcurrency.scheduler.runEntity(plugin, carrier) {
            DebugLogger.debug(2) { "通过玩家 ${carrier.name} 发送插件消息，bytes=${payload.size}" }
            carrier.sendPluginMessage(plugin, settings?.channel ?: return@runEntity, payload)
        }
    }

    fun sendPluginMessageNow(carrier: Player, payload: ByteArray): Boolean {
        val channel = settings?.channel ?: return false
        return runCatching {
            DebugLogger.debug(2) { "立即通过玩家 ${carrier.name} 发送插件消息，bytes=${payload.size}" }
            carrier.sendPluginMessage(plugin, channel, payload)
        }.onFailure { exception ->
            recordProxySendFailure("插件消息立即发送失败: ${exception.message ?: exception.javaClass.simpleName}")
        }.isSuccess
    }

    private fun resolveHeadSource(player: Player): String? {
        return runCatching {
            val profile = Player::class.java.getMethod("getPlayerProfile").invoke(player) ?: return null
            val textures = profile.javaClass.getMethod("getTextures").invoke(profile) ?: return null
            val skinUrl = textures.javaClass.getMethod("getSkin").invoke(textures)
            (skinUrl as? java.net.URL)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { "url-$it" }
        }.getOrNull()
    }

    private fun notifyRemoteOnlineState(uid: String, playerName: String, online: Boolean) {
        plugin.moduleManager.getModule<FriendModule>("friend")?.let { friendModule ->
            runBlocking { friendModule.notifyOnlineState(uid, playerName, online) }
        }
    }

    private fun sendTeleportFailure(player: Player, reason: String, actorName: String?) {
        when (reason) {
            "offline" -> player.sendLang("player-offline")
            "denied" -> player.sendLang("tp-denied", mapOf("target" to (actorName ?: "对方")))
            "expired" -> player.sendLang("tp-request-expired", mapOf("target" to (actorName ?: "对方")))
            "world-disabled" -> player.sendLang("tp-world-disabled")
            "pending" -> player.sendLang("tp-request-pending")
            "not-allowed" -> player.sendLang("tp-not-allowed")
            "not-friend" -> player.sendLang("tp-friend-only")
            "execute-timeout" -> player.sendLang("tp-execute-timeout")
            else -> player.sendLang("tp-failed")
        }
    }

    private fun formatProxyIssue(timestamp: Long, reason: String): String {
        if (timestamp <= 0L || reason.isBlank()) return "无"
        return "${formatProxyTime(timestamp)} / $reason"
    }

    private fun formatProxyTime(timestamp: Long): String {
        if (timestamp <= 0L) return "暂无"
        val seconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60L -> "${seconds}秒前"
            seconds < 3600L -> "${seconds / 60L}分钟前"
            else -> "${seconds / 3600L}小时前"
        }
    }
}
