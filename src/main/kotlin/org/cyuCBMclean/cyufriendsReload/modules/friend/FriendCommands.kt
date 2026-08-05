package org.cyuCBMclean.cyufriendsReload.modules.friend

import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.command.CommandDispatcher
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.displayServerName
import org.cyuCBMclean.cyufriendsReload.extension.globalOnlineEntries
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.compat.NpcCompat
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatCommands
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.chat.gui.MessageChatView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.AddFriendView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.BlacklistView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendProfileView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendProfileSocialView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendRemoveConfirmView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendTagColorView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendTagManageView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendTagFilterView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendTimelineView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendsListView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.OnlinePlayersView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.RecommendationsView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.RequestsView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.SentRequestsView
import org.cyuCBMclean.cyufriendsReload.modules.group.GroupModule
import org.cyuCBMclean.cyufriendsReload.modules.group.gui.GroupListView
import org.cyuCBMclean.cyufriendsReload.modules.group.gui.GroupBatchMoveView
import org.cyuCBMclean.cyufriendsReload.modules.group.gui.GroupMembersView
import org.cyuCBMclean.cyufriendsReload.modules.group.gui.GroupMoveView
import org.cyuCBMclean.cyufriendsReload.modules.group.gui.GroupRulesView
import org.cyuCBMclean.cyufriendsReload.modules.profile.BirthdaySetResult
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.profile.gui.BirthdaysView
import org.cyuCBMclean.cyufriendsReload.modules.profile.gui.NotificationCenterView
import org.cyuCBMclean.cyufriendsReload.modules.profile.gui.ProfileHomeView
import org.cyuCBMclean.cyufriendsReload.modules.profile.gui.SocialSettingsView
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyGateway
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiLoader
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.ViewTitles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object FriendCommands {

    private const val HELP_PAGE_SIZE = 10
    private val adminTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun register(plugin: CyufriendsReload, module: FriendModule, chatModule: ChatModule?) {
        CommandDispatcher(plugin, "friend") {

            executes {
                sendHelp(sender, null)
            }

            chatModule?.let { ChatCommands.registerSubCommands(plugin, it, this) }

            subCommand("help") {
                executes {
                    sendHelp(sender, getArg(0))
                }

                tabComplete {
                    filterCompletions(
                        (1..helpPageCount(CyufriendsReload.instance)).map(Int::toString),
                        args.getOrElse(0) { "" }
                    )
                }
            }

            subCommand("reload") {
                permission = "cyufriends.admin"
                onNoPermission = { it.sendLang("no-permission") }

                executes {
                    val reloadSender = sender
                    CyuConcurrency.scheduler.runGlobal(plugin) {
                        runCatching { plugin.reloadRuntime() }
                            .onSuccess {
                                if (reloadSender is Player && reloadSender.isOnline) {
                                    CyuConcurrency.scheduler.runEntity(plugin, reloadSender) {
                                        reloadSender.sendLang("reload-success")
                                    }
                                } else {
                                    reloadSender.sendLang("reload-success")
                                }
                            }
                            .onFailure { exception ->
                                plugin.logger.log(java.util.logging.Level.SEVERE, "插件重载失败", exception)
                                if (reloadSender is Player && reloadSender.isOnline) {
                                    CyuConcurrency.scheduler.runEntity(plugin, reloadSender) {
                                        reloadSender.sendMessage("§c[CyuFriends] 重载失败：${exception.message ?: "配置无效"}")
                                    }
                                } else {
                                    reloadSender.sendMessage("§c[CyuFriends] 重载失败：${exception.message ?: "配置无效"}")
                                }
                            }
                    }
                }
            }

            subCommand("admin") {
                permission = "cyufriends.admin"
                onNoPermission = { it.sendLang("no-permission") }

                executes {
                    when (getArg(0)?.lowercase()) {
                        "inspect" -> {
                            val targetInput = getArg(1) ?: return@executes sender.sendMessage("用法: /friend admin inspect <玩家>")
                            val targetUid = CyuIdHook.getUidByName(targetInput) ?: return@executes sender.sendLang("player-not-found")
                            val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
                            val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
                            val proxyModule = plugin.moduleManager.getModule<ProxyModule>("proxy")
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                val displayName = CyuIdHook.getName(targetUid) ?: targetInput
                                val friendCount = module.friendManager.getFriendCountSync(targetUid)
                                val requestReceived = module.requestManager.countReceivedSync(targetUid)
                                val requestSent = module.requestManager.countSentSync(targetUid)
                                val blockCount = module.blockManager.getBlocksStoredSync(targetUid).size
                                val unreadCount = chatModule?.manager?.unreadCountSync(targetUid) ?: 0
                                val statusCount = socialModule?.manager?.getStatusCountSync(targetUid) ?: 0
                                val wallCount = socialModule?.manager?.getWallCountSync(targetUid) ?: 0
                                val pendingWallCount = socialModule?.manager?.pendingWallCountSync(targetUid) ?: 0
                                val birthday = profileModule?.manager?.getBirthdaySync(targetUid) ?: "未设置"
                                val lastInteraction = module.friendManager.getFriendEntriesStoredSync(targetUid).maxOfOrNull { it.lastInteractionAt } ?: 0L
                                val serverName = plugin.displayServerName(proxyModule?.remotePresence?.find(targetUid)?.serverId)
                                val onlineScope = plugin.onlineScope(targetUid)
                                val lines = listOf(
                                    "§b[CyuFriends] §f目标: §a$displayName",
                                    "§7UID: §f$targetUid",
                                    "§7在线状态: §f$onlineScope",
                                    "§7所在服务器: §f$serverName",
                                    "§7好友数: §f$friendCount  §7收到请求: §f$requestReceived  §7发出请求: §f$requestSent",
                                    "§7黑名单数: §f$blockCount  §7未读留言: §f$unreadCount",
                                    "§7动态数: §f$statusCount  §7留言墙数: §f$wallCount  §7待审留言: §f$pendingWallCount",
                                    "§7生日: §f$birthday",
                                    "§7最近互动: §f${formatAdminTime(lastInteraction)}"
                                )
                                CyuConcurrency.scheduler.runGlobal(plugin) {
                                    lines.forEach(sender::sendMessage)
                                }
                            }
                        }

                        "rebuild", "refresh" -> {
                            val targetInput = getArg(1) ?: return@executes sender.sendMessage("用法: /friend admin rebuild <玩家>")
                            val targetUid = CyuIdHook.getUidByName(targetInput) ?: return@executes sender.sendLang("player-not-found")
                            val targetOnline = Bukkit.getOnlinePlayers().any { it.uid == targetUid }
                            val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                module.friendManager.invalidate(targetUid)
                                module.requestManager.invalidate(targetUid)
                                module.blockManager.invalidate(targetUid)
                                module.preferencesManager.invalidate(targetUid)
                                profileModule?.manager?.invalidate(targetUid)
                                socialModule?.manager?.invalidateStatusCache(targetUid)
                                socialModule?.manager?.invalidateWallCache(targetUid)

                                if (targetOnline) {
                                    module.friendManager.loadPlayerSync(targetUid)
                                    module.requestManager.loadPlayerSync(targetUid)
                                    module.blockManager.loadPlayerSync(targetUid)
                                    module.preferencesManager.loadPlayerSync(targetUid)
                                    profileModule?.manager?.loadProfileSync(targetUid)
                                }

                                val displayName = CyuIdHook.getName(targetUid) ?: targetInput
                                val message = if (targetOnline) {
                                    "§b[CyuFriends] §f已重建 §a$displayName §f的缓存，并重新加载在线数据。"
                                } else {
                                    "§b[CyuFriends] §f已清理 §a$displayName §f的缓存，离线数据将在下次使用时重新加载。"
                                }
                                CyuConcurrency.scheduler.runGlobal(plugin) {
                                    sender.sendMessage(message)
                                }
                            }
                        }

                        "proxy" -> {
                            val proxyModule = plugin.moduleManager.getModule<ProxyModule>("proxy")
                            if (proxyModule == null) {
                                sender.sendMessage("§b[CyuFriends] §f代理模块未加载。")
                                return@executes
                            }
                            val settings = proxyModule.settings
                            val enabled = settings?.enabled == true
                            val breakdown = proxyModule.remoteServerBreakdown()
                                .entries
                                .joinToString("§7, §f") { "${plugin.displayServerName(it.key)}§7: §f${it.value}" }
                                .ifBlank { "无" }
                            listOf(
                                "§b[CyuFriends] §f代理观测",
                                "§7跨服同步: §f${if (enabled) "已启用" else "未启用"}",
                                "§7当前服标识: §f${settings?.serverId ?: "未配置"}",
                                "§7远端在线数: §f${proxyModule.remoteOnlineCount()}",
                                "§7待确认私聊: §f${proxyModule.pendingDirectCount()}  §7待完成预检: §f${proxyModule.pendingTeleportPrecheckCount()}",
                                "§7快照请求状态: §f${if (proxyModule.hasRequestedSnapshot()) "已发起" else "未发起"}",
                                "§7远端服分布: §f$breakdown"
                            ).plus(proxyModule.proxyDiagnostics()).forEach(sender::sendMessage)
                        }

                        "cache" -> {
                            val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
                            val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
                            listOf(
                                "§b[CyuFriends] §f缓存观测",
                                "§7好友缓存: §f${module.friendManager.cachedPlayerCount()} §7玩家 / §f${module.friendManager.cachedFriendRelationCount()} §7关系",
                                "§7申请缓存: §f${module.requestManager.cachedReceiverCount()} §7接收者 / §f${module.requestManager.cachedSenderCount()} §7发送者 / §f${module.requestManager.cachedRequestCount()} §7请求 / §f${module.requestManager.cooldownTrackerCount()} §7冷却",
                                "§7黑名单缓存: §f${module.blockManager.cachedPlayerCount()} §7玩家 / §f${module.blockManager.cachedBlockCount()} §7记录",
                                "§7偏好缓存: §f${module.preferencesManager.cachedPreferenceCount()} §7主设置 / §f${module.preferencesManager.cachedPersonalOwnerCount()} §7个人设置持有者 / §f${module.preferencesManager.cachedPersonalRelationCount()} §7单好友设置 / §f${module.preferencesManager.cachedGroupOwnerCount()} §7分组持有者 / §f${module.preferencesManager.cachedGroupRuleCount()} §7分组规则",
                                "§7资料缓存: §f${profileModule?.manager?.cachedProfileCount() ?: 0}",
                                "§7私聊缓存: §f${chatModule?.manager?.cachedUnreadOwnerCount() ?: 0} §7未读持有者 / §f${chatModule?.manager?.cachedUnreadMessageCount() ?: 0} §7未读消息 / §f${chatModule?.manager?.cachedConversationCount() ?: 0} §7会话 / §f${chatModule?.manager?.replyTargetCount() ?: 0} §7回复目标 / §f${chatModule?.manager?.cooldownTrackerCount() ?: 0} §7冷却",
                                "§7社交缓存: §f${socialModule?.manager?.cachedStatusOwnerCount() ?: 0} §7动态 / §f${socialModule?.manager?.cachedStatusCountEntryCount() ?: 0} §7动态计数 / §f${socialModule?.manager?.cachedStatusCommentEntryCount() ?: 0} §7动态评论 / §f${socialModule?.manager?.cachedWallOwnerCount() ?: 0} §7留言墙 / §f${socialModule?.manager?.cachedWallCommentEntryCount() ?: 0} §7留言评论 / §f${socialModule?.manager?.cachedGlobalStatusCount() ?: 0} §7全局动态"
                            ).forEach(sender::sendMessage)
                        }

                        "health" -> {
                            val proxyModule = plugin.moduleManager.getModule<ProxyModule>("proxy")
                            val placeholderEnabled = plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")
                            val localOnline = Bukkit.getOnlinePlayers().size
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                val dbOk = plugin.databaseManager.pingSync()
                                val configuredModules = plugin.moduleManager.configuredModuleIds().joinToString(", ").ifBlank { "无" }
                                val enabledModules = plugin.moduleManager.enabledModuleIds().joinToString(", ").ifBlank { "无" }
                                val lines = listOf(
                                    "§b[CyuFriends] §f健康检查",
                                    "§7数据库: §f${if (dbOk) "正常" else "异常"}  §7连接池上限: §f${plugin.databaseManager.maximumPoolSize()}",
                                    "§7PlaceholderAPI: §f${if (placeholderEnabled) "已接入" else "未接入"}",
                                    "§7代理模块: §f${if (proxyModule?.settings?.enabled == true) "已启用" else "未启用"}",
                                    "§7Debug: §f${debugStateText()}",
                                    "§7在线玩家: §f$localOnline  §7远端在线: §f${proxyModule?.remoteOnlineCount() ?: 0}",
                                    "§7配置启用模块: §f$configuredModules",
                                    "§7运行中模块: §f$enabledModules"
                                ).plus(plugin.moduleManager.moduleDiagnostics().map { "§7模块状态: §f$it" })
                                CyuConcurrency.scheduler.runGlobal(plugin) {
                                    lines.forEach(sender::sendMessage)
                                }
                            }
                        }

                        "debug" -> {
                            handleAdminDebug(sender, getArg(1), getArg(2))
                        }

                        "moderation" -> {
                            val socialModule = plugin.moduleManager.getModule<SocialModule>("social")
                            if (socialModule == null) {
                                sender.sendMessage("§b[CyuFriends] §f社交模块未加载。")
                                return@executes
                            }
                            val targetInput = getArg(1)
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                val lines = if (targetInput.isNullOrBlank()) {
                                    buildModerationOverview(plugin, socialModule)
                                } else {
                                    val targetUid = CyuIdHook.getUidByName(targetInput)
                                    if (targetUid == null) {
                                        listOf("§c未找到玩家: $targetInput")
                                    } else {
                                        buildModerationOwnerOverview(socialModule, targetUid, targetInput)
                                    }
                                }
                                CyuConcurrency.scheduler.runGlobal(plugin) {
                                    lines.forEach(sender::sendMessage)
                                }
                            }
                        }

                        "legacy" -> {
                            when (getArg(1)?.lowercase()) {
                                "inspect", "scan" -> {
                                    CyuConcurrency.scheduler.runAsync(plugin) {
                                        val snapshot = module.legacyMigrationAssistant.inspectSync()
                                        val lines = buildLegacyInspectLines(snapshot)
                                        CyuConcurrency.scheduler.runGlobal(plugin) {
                                            lines.forEach(sender::sendMessage)
                                        }
                                    }
                                }

                                "import", "migrate" -> {
                                    val scopes = module.legacyMigrationAssistant.resolveScopes(getArg(2))
                                    if (scopes.isEmpty()) {
                                        sender.sendLang("usage-admin-legacy")
                                        return@executes
                                    }
                                    CyuConcurrency.scheduler.runAsync(plugin) {
                                        runCatching { module.legacyMigrationAssistant.importSync(scopes) }
                                            .onSuccess { result ->
                                                val lines = buildLegacyImportLines(result)
                                                CyuConcurrency.scheduler.runGlobal(plugin) {
                                                    lines.forEach(sender::sendMessage)
                                                }
                                            }
                                            .onFailure { error ->
                                                CyuConcurrency.scheduler.runGlobal(plugin) {
                                                    sender.sendMessage("§c[CyuFriends] 旧版数据迁移失败：${error.message ?: "未知错误"}")
                                                }
                                            }
                                    }
                                }

                                else -> sender.sendLang("usage-admin-legacy")
                            }
                        }

                        else -> sender.sendMessage("用法: /friend admin inspect <玩家> | /friend admin rebuild <玩家> | /friend admin proxy | /friend admin cache | /friend admin health | /friend admin debug [status|on|off|level|file] | /friend admin moderation [玩家] | /friend admin legacy <inspect|import> [active|all|friends|requests|blocks|settings|profile|chat|social]")
                    }
                }

                tabComplete {
                    when (args.size) {
                        1 -> filterCompletions(listOf("inspect", "rebuild", "refresh", "proxy", "cache", "health", "debug", "moderation", "legacy"), args[0])
                        2 -> {
                            if (args[0].equals("legacy", ignoreCase = true)) {
                                return@tabComplete filterCompletions(listOf("inspect", "import"), args[1])
                            }
                            if (args[0].equals("debug", ignoreCase = true)) {
                                return@tabComplete filterCompletions(listOf("status", "on", "off", "level", "file"), args[1])
                            }
                            if (
                                args[0].equals("proxy", ignoreCase = true) ||
                                args[0].equals("cache", ignoreCase = true) ||
                                args[0].equals("health", ignoreCase = true)
                            ) return@tabComplete emptyList()
                            val online = globalOnlineNames(plugin)
                            val values = if (isPlayer) {
                                sequenceOf(friendNames(module, player).asSequence(), online.asSequence()).flatten().toList()
                            } else {
                                online
                            }
                            filterCompletions(values, args[1])
                        }
                        3 -> {
                            if (args[0].equals("legacy", ignoreCase = true) && args[1].equals("import", ignoreCase = true)) {
                                return@tabComplete filterCompletions(listOf("active", "all", "friends", "requests", "blocks", "settings", "profile", "chat", "social"), args[2])
                            }
                            if (args[0].equals("debug", ignoreCase = true)) {
                                val values = when (args[1].lowercase()) {
                                    "on", "level" -> listOf("0", "1", "2")
                                    "file" -> listOf("on", "off")
                                    else -> emptyList()
                                }
                                return@tabComplete filterCompletions(values, args[2])
                            }
                            emptyList()
                        }
                        else -> emptyList()
                    }
                }
            }

            subCommand("notify") {
                requirePlayer = true
                permission = "cyufriends.command.notify"

                executes {
                    val uid = player.uid
                    val proxyGateway = proxyGateway(plugin)
                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val enabled = runBlocking { module.preferencesManager.toggleNotifyOnJoin(uid) }
                        proxyGateway?.invalidateSettings(uid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang(if (enabled) "notify-enabled" else "notify-disabled")
                            player.playAudio(if (enabled) "notify-enabled" else "notify-disabled")
                        }
                    }
                }
            }

            subCommand("notifyme") {
                requirePlayer = true
                permission = "cyufriends.command.notifyme"

                executes {
                    val uid = player.uid
                    val proxyGateway = proxyGateway(plugin)
                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val enabled = runBlocking { module.preferencesManager.toggleNotifyOwnFriends(uid) }
                        proxyGateway?.invalidateSettings(uid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang(if (enabled) "notifyme-enabled" else "notifyme-disabled")
                            player.playAudio(if (enabled) "notifyme-enabled" else "notifyme-disabled")
                        }
                    }
                }
            }

            subCommand("tptoggle") {
                requirePlayer = true
                permission = "cyufriends.command.tptoggle"

                executes {
                    val uid = player.uid
                    val proxyGateway = proxyGateway(plugin)
                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val mode = runBlocking { module.preferencesManager.cycleTeleportMode(uid) }
                        proxyGateway?.invalidateSettings(uid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang(teleportModeMessageKey(mode))
                            player.playAudio(teleportModeSoundKey(mode))
                        }
                    }
                }
            }

            subCommand("personal") {
                alias("individual", "special")
                requirePlayer = true
                permission = "cyufriends.command.personal"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-personal")
                    val type = personalType(getArg(1) ?: return@executes player.sendLang("usage-personal"))
                        ?: return@executes player.sendLang("usage-personal")

                    val ownerUid = player.uid
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName

                    if (!module.friendManager.isFriend(ownerUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val updated = runBlocking { module.preferencesManager.togglePersonal(ownerUid, targetUid, type) }
                        proxyGateway?.invalidateSettings(ownerUid)
                        val state = personalState(updated, type)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang(
                                "personal-setting-updated",
                                mapOf("target" to displayName, "type" to type.displayName, "state" to state.displayName(type))
                            )
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args[0])
                        2 -> filterCompletions(
                            listOf("tp", "notify", "notifyme", "statuslike", "statuscomment", "wallpost", "walllike", "wallcomment"),
                            args[1]
                        )
                        else -> emptyList()
                    }
                }
            }

            subCommand("add") {
                requirePlayer = true
                permission = "cyufriends.command.add"
                onNotPlayer = { it.sendLang("only-player") }
                onNoPermission = { it.sendLang("no-permission") }

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-add")
                    val proxyModule = plugin.moduleManager.getModule<ProxyModule>("proxy")
                    val senderUid = player.uid
                    val senderName = player.name
                    val rawRequestNote = args.drop(1).joinToString(" ").trim()
                    val requestNoteLimit = FriendRequestNotes.maxLength(plugin)
                    if (rawRequestNote.length > requestNoteLimit) {
                        return@executes player.sendLang("request-note-too-long", mapOf("limit" to requestNoteLimit.toString()))
                    }
                    val requestNote = FriendRequestNotes.normalize(plugin, rawRequestNote)
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val targetPlayer = CyuIdHook.getOnlinePlayer(targetUid)
                    if (targetPlayer != null && NpcCompat.isNpc(targetPlayer)) {
                        return@executes player.sendLang("cannot-add-npc")
                    }
                    val targetOnline = targetPlayer?.isOnline == true
                    val remotePresence = proxyModule?.remotePresence?.find(targetUid)
                    if (targetPlayer == null && remotePresence == null) return@executes player.sendLang("player-offline")
                    val displayName = targetPlayer?.name ?: remotePresence?.name ?: targetName

                    if (senderUid == targetUid) return@executes player.sendLang("cannot-add-self")
                    if (module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("already-friend")

                    val cooldown = requestCooldown(plugin, player)
                    val dailyLimit = requestDailyLimit(plugin, player)
                    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val proxyGateway = proxyGateway(plugin)
                    val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        if (runBlocking { module.blockManager.isBlockedStored(targetUid, senderUid) }) {
                            DebugLogger.debug(1) { "好友申请拦截: sender=$senderUid target=$targetUid reason=blocked-by-target" }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("blocked-by-target")
                            }
                            return@runAsync
                        }
                        if (module.friendManager.isFriend(senderUid, targetUid) || runBlocking { module.friendManager.isFriendStored(senderUid, targetUid) }) {
                            DebugLogger.debug(1) { "好友申请拦截: sender=$senderUid target=$targetUid reason=already-friend" }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("already-friend")
                            }
                            return@runAsync
                        }
                        if (runBlocking { module.requestManager.hasRequestStored(targetUid, senderUid) }) {
                            DebugLogger.debug(1) { "好友申请自动互加: sender=$senderUid target=$targetUid" }
                            runBlocking {
                                module.requestManager.removeRequest(targetUid, senderUid)
                                module.friendManager.establishFriendship(senderUid, targetUid)
                            }
                            proxyGateway?.invalidateRequest(senderUid, targetUid)
                            proxyGateway?.invalidateRelation(senderUid, targetUid)
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("request-mutual-added", mapOf("target" to displayName))
                                player.playAudio("friend-added")
                            }
                            if (targetOnline && targetPlayer != null) {
                                CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
                                    targetPlayer.sendLang("friend-added", mapOf("player" to senderName))
                                    targetPlayer.playAudio("friend-added")
                                }
                            } else if (remotePresence != null) {
                                proxyGateway?.sendFriendRequestAccepted(targetUid, senderName)
                            }
                            return@runAsync
                        }
                        if (profileModule != null) {
                            val targetProfile = runBlocking { profileModule.manager.loadProfile(targetUid) }
                            if (!targetProfile.allowRequests) {
                                DebugLogger.debug(1) { "好友申请拦截: sender=$senderUid target=$targetUid reason=target-requests-disabled" }
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("target-requests-disabled")
                                }
                                return@runAsync
                            }
                        }
                        if (runBlocking { module.requestManager.hasRequestStored(senderUid, targetUid) }) {
                            DebugLogger.debug(1) { "好友申请拦截: sender=$senderUid target=$targetUid reason=request-already-sent" }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("request-already-sent")
                            }
                            return@runAsync
                        }
                        val submitResult = runBlocking {
                            module.requestManager.submitRequest(
                                senderUid,
                                targetUid,
                                requestNote,
                                cooldown,
                                dailyLimit,
                                todayStart
                            )
                        }
                        if (submitResult != FriendRequestSubmitResult.SENT) {
                            val remaining = module.requestManager.remainingCooldown(senderUid, cooldown).toString()
                            DebugLogger.debug(1) {
                                "好友申请拦截: sender=$senderUid target=$targetUid reason=${submitResult.name.lowercase()} remaining=${remaining}s daily=$dailyLimit"
                            }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                when (submitResult) {
                                    FriendRequestSubmitResult.ALREADY_SENT -> player.sendLang("request-already-sent")
                                    FriendRequestSubmitResult.COOLDOWN -> player.sendLang("request-cooldown", mapOf("seconds" to remaining))
                                    FriendRequestSubmitResult.DAILY_LIMIT -> player.sendLang("request-daily-limit", mapOf("amount" to dailyLimit.toString()))
                                    FriendRequestSubmitResult.SENT -> {}
                                }
                            }
                            return@runAsync
                        }

                        DebugLogger.debug(1) {
                            "好友申请已发送: sender=$senderUid target=$targetUid route=${if (targetOnline) "local" else "proxy"} note=${!requestNote.isNullOrBlank()}"
                        }

                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            val placeholders = linkedMapOf("target" to displayName)
                            if (!requestNote.isNullOrBlank()) {
                                placeholders["note"] = requestNote
                            }
                            player.sendLang(if (requestNote.isNullOrBlank()) "request-sent" else "request-sent-with-note", placeholders)
                            player.playAudio("request-sent")
                        }
                        if (targetOnline && targetPlayer != null) {
                            CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
                                FriendRichMessages.sendFriendRequestPrompt(targetPlayer, senderName, senderUid, requestNote)
                                targetPlayer.playAudio("request-received")
                            }
                        } else {
                            proxyGateway?.sendFriendRequestNotify(senderUid, senderName, targetUid, requestNote)
                        }
                        proxyGateway?.invalidateRequest(targetUid)
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    addTargetCompletions(plugin, module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("accept") {
                requirePlayer = true
                permission = "cyufriends.command.accept"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-accept")

                    val receiverUid = player.uid
                    val senderUid = resolveRequestSenderUid(module, targetName, receiverUid)
                        ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(senderUid) ?: targetName

                    if (!module.requestManager.hasRequestStable(senderUid, receiverUid)) return@executes player.sendLang("no-request")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking {
                            module.requestManager.removeRequest(senderUid, receiverUid)
                            module.requestManager.callAcceptEvent(senderUid, receiverUid)
                            module.friendManager.establishFriendship(senderUid, receiverUid)
                        }
                        proxyGateway?.invalidateRequest(senderUid, receiverUid)
                        proxyGateway?.invalidateRelation(senderUid, receiverUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("friend-added", mapOf("player" to displayName))
                            player.playAudio("friend-added")
                            refreshOpenRequestView(player)
                        }
                        val senderPlayer = CyuIdHook.getOnlinePlayer(senderUid)
                        if (senderPlayer != null && senderPlayer.isOnline) {
                            CyuConcurrency.scheduler.runEntity(plugin, senderPlayer) {
                                senderPlayer.sendLang("friend-added", mapOf("player" to player.name))
                                senderPlayer.playAudio("friend-added")
                            }
                        } else {
                            proxyGateway?.sendFriendRequestAccepted(senderUid, player.name)
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    uidNameCompletions(module.requestManager.getRequests(player.uid), args.getOrElse(0) { "" })
                }
            }

            subCommand("deny") {
                requirePlayer = true
                permission = "cyufriends.command.deny"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-deny")
                    val receiverUid = player.uid
                    val senderUid = resolveRequestSenderUid(module, targetName, receiverUid)
                        ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(senderUid) ?: targetName

                    if (!module.requestManager.hasRequestStable(senderUid, receiverUid)) return@executes player.sendLang("no-request")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking {
                            module.requestManager.removeRequest(senderUid, receiverUid)
                            module.requestManager.callDenyEvent(senderUid, receiverUid)
                        }
                        proxyGateway?.invalidateRequest(senderUid, receiverUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("request-denied", mapOf("player" to displayName))
                            refreshOpenRequestView(player)
                        }
                        val senderPlayer = CyuIdHook.getOnlinePlayer(senderUid)
                        if (senderPlayer != null && senderPlayer.isOnline) {
                            CyuConcurrency.scheduler.runEntity(plugin, senderPlayer) {
                                senderPlayer.sendLang("request-denied-by", mapOf("player" to player.name))
                            }
                        } else {
                            proxyGateway?.sendFriendRequestDenied(senderUid, player.name)
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    uidNameCompletions(module.requestManager.getRequests(player.uid), args.getOrElse(0) { "" })
                }
            }

            subCommand("remove") {
                requirePlayer = true
                permission = "cyufriends.command.remove"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-remove")

                    val uid1 = player.uid
                    val uid2 = resolveFriendUid(module, uid1, targetName) ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(uid2) ?: targetName

                    if (!module.friendManager.isFriendStable(uid1, uid2)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking {
                            module.friendManager.severFriendship(uid1, uid2)
                            module.preferencesManager.clearPersonalBetween(uid1, uid2)
                        }
                        proxyGateway?.invalidateRelation(uid1, uid2)
                        proxyGateway?.invalidateSettings(uid1, uid2)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("friend-removed", mapOf("player" to displayName))
                            player.playAudio("friend-removed")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("revoke") {
                requirePlayer = true
                permission = "cyufriends.command.add"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-revoke")
                    val senderUid = player.uid
                    val targetUid = resolveRequestReceiverUid(module, senderUid, targetName)
                        ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName
                    if (!module.requestManager.hasRequestStable(senderUid, targetUid)) {
                        return@executes player.sendLang("request-revoke-missing")
                    }
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking {
                            module.requestManager.removeRequest(senderUid, targetUid)
                            module.requestManager.callRevokeEvent(senderUid, targetUid)
                        }
                        proxyGateway?.invalidateRequest(senderUid, targetUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("request-revoked", mapOf("target" to displayName))
                            player.playAudio("success")
                            refreshOpenRequestView(player)
                        }
                        val targetPlayer = CyuIdHook.getOnlinePlayer(targetUid)
                        if (targetPlayer != null && targetPlayer.isOnline) {
                            CyuConcurrency.scheduler.runEntity(plugin, targetPlayer) {
                                targetPlayer.sendLang("request-revoked-by", mapOf("player" to player.name))
                            }
                        } else {
                            proxyGateway?.sendFriendRequestRevoked(targetUid, player.name)
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    uidNameCompletions(module.requestManager.getSentRequests(player.uid), args.getOrElse(0) { "" })
                }
            }

            subCommand("block") {
                requirePlayer = true
                permission = "cyufriends.command.block"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-block")

                    val userUid = player.uid
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName

                    if (module.blockManager.isBlocked(userUid, targetUid)) return@executes player.sendLang("already-blocked")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking {
                            if (module.friendManager.isFriend(userUid, targetUid)) {
                                module.friendManager.severFriendship(userUid, targetUid)
                                module.preferencesManager.clearPersonalBetween(userUid, targetUid)
                            }
                            module.requestManager.removeRequest(targetUid, userUid)
                            module.blockManager.addBlock(userUid, targetUid)
                        }
                        proxyGateway?.invalidateRelation(userUid, targetUid)
                        proxyGateway?.invalidateRequest(userUid)
                        proxyGateway?.invalidateSettings(userUid, targetUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("player-blocked", mapOf("player" to displayName))
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    blockTargetCompletions(plugin, module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("unblock") {
                requirePlayer = true
                permission = "cyufriends.command.unblock"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-unblock")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName

                    val userUid = player.uid

                    if (!module.blockManager.isBlocked(userUid, targetUid)) return@executes player.sendLang("not-blocked")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking { module.blockManager.removeBlock(userUid, targetUid) }
                        proxyGateway?.invalidateRelation(userUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("player-unblocked", mapOf("player" to displayName))
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    uidNameCompletions(module.blockManager.getBlocks(player.uid), args.getOrElse(0) { "" })
                }
            }

            subCommand("tp") {
                requirePlayer = true
                permission = "cyufriends.command.tp"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-tp")
                    val proxyModule = plugin.moduleManager.getModule<ProxyModule>("proxy")
                    val senderUid = player.uid
                    val senderName = player.name
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val targetPlayer = CyuIdHook.getOnlinePlayer(targetUid)
                    val remotePresence = proxyModule?.remotePresence?.find(targetUid)
                    if (targetPlayer == null && remotePresence == null) return@executes player.sendLang("player-offline")
                    val displayName = targetPlayer?.name ?: remotePresence?.name ?: targetName

                    if (senderUid == targetUid) return@executes player.sendLang("cannot-tp-self")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        if (!module.friendManager.isFriend(senderUid, targetUid) && !runBlocking { module.friendManager.isFriendStored(senderUid, targetUid) }) {
                            DebugLogger.debug(1) { "好友传送拦截: sender=$senderUid target=$targetUid reason=tp-friend-only" }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tp-friend-only")
                            }
                            return@runAsync
                        }
                        val teleportMode = runBlocking { module.preferencesManager.resolveTeleportMode(targetUid, senderUid) }
                        if (teleportMode == FriendTeleportMode.DENY) {
                            DebugLogger.debug(1) { "好友传送拦截: sender=$senderUid target=$targetUid reason=tp-not-allowed" }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tp-not-allowed")
                            }
                            return@runAsync
                        }

                        if (remotePresence == null) {
                            if (teleportMode == FriendTeleportMode.DIRECT) {
                                DebugLogger.debug(1) { "好友传送直达: sender=$senderUid target=$targetUid route=local" }
                                executeLocalTeleport(plugin, player, targetUid)
                                return@runAsync
                            }

                            val liveTarget = CyuIdHook.getOnlinePlayer(targetUid)
                            if (liveTarget == null || !liveTarget.isOnline) {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("player-offline")
                                }
                                return@runAsync
                            }

                            val timeoutSeconds = module.teleportManager.requestTimeoutSeconds()
                            val request = module.teleportManager.createRequest(senderUid, senderName, null)
                            DebugLogger.debug(1) {
                                "好友传送请求已创建: sender=$senderUid target=$targetUid route=local timeout=${timeoutSeconds}s"
                            }
                            val queued = module.teleportManager.sendRequest(targetUid, request) { expired ->
                                val expiredSender = CyuIdHook.getOnlinePlayer(expired.senderUid)
                                if (expiredSender != null && expiredSender.isOnline) {
                                    CyuConcurrency.scheduler.runEntity(plugin, expiredSender) {
                                        expiredSender.sendLang("tp-request-expired", mapOf("target" to liveTarget.name))
                                    }
                                }
                                val expiredReceiver = CyuIdHook.getOnlinePlayer(targetUid)
                                if (expiredReceiver != null && expiredReceiver.isOnline) {
                                    CyuConcurrency.scheduler.runEntity(plugin, expiredReceiver) {
                                        expiredReceiver.sendLang("tp-request-expired-received", mapOf("sender" to expired.senderName))
                                    }
                                }
                            }
                            if (!queued) {
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("tp-request-pending")
                                }
                                return@runAsync
                            }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang(
                                    "tp-request-sent",
                                    mapOf("target" to liveTarget.name, "seconds" to timeoutSeconds.toString())
                                )
                            }
                            CyuConcurrency.scheduler.runEntity(plugin, liveTarget) {
                                FriendRichMessages.sendTeleportRequestPrompt(liveTarget, senderName, timeoutSeconds)
                                liveTarget.playAudio("tp-request-received")
                            }
                            return@runAsync
                        }

                        val sent = proxyGateway?.sendTeleportPrecheck(senderUid, player.name, targetUid)
                        DebugLogger.debug(1) {
                            "好友传送跨服预检: sender=$senderUid target=$targetUid sent=${sent != null}"
                        }
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (sent == null) {
                                player.sendLang("tp-failed")
                            } else {
                                player.sendLang(
                                    "tp-cross-server-processing",
                                    mapOf("target" to displayName, "server" to plugin.displayServerName(remotePresence?.serverId))
                                )
                            }
                        }
                        if (sent != null) {
                            proxyModule?.trackTeleportPrecheck(sent, senderUid)
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    tpTargetCompletions(plugin, module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("tpaccept") {
                requirePlayer = true
                permission = "cyufriends.command.tp"

                executes {
                    val receiverUid = player.uid
                    val request = module.teleportManager.getRequest(receiverUid) ?: return@executes player.sendLang("no-tp-request")
                    val senderUid = request.senderUid
                    val senderPlayer = CyuIdHook.getOnlinePlayer(senderUid)
                    val senderOnline = senderPlayer?.isOnline == true
                    val receiverName = player.name
                    val receiverWorldName = player.world.name
                    val receiverLocation = player.location.clone()
                    val senderRemote = plugin.moduleManager.getModule<ProxyModule>("proxy")?.remotePresence?.find(senderUid)
                    val proxyGateway = proxyGateway(plugin)

                    if (senderPlayer == null && senderRemote == null) {
                        module.teleportManager.clearRequest(receiverUid)
                        return@executes player.sendLang("player-offline")
                    }

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        if (!module.friendManager.isFriend(senderUid, receiverUid) && !runBlocking { module.friendManager.isFriendStored(senderUid, receiverUid) }) {
                            DebugLogger.debug(1) { "好友传送接受拦截: receiver=$receiverUid sender=$senderUid reason=tp-friend-only" }
                            module.teleportManager.clearRequest(receiverUid)
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tp-friend-only")
                            }
                            if (senderOnline && senderPlayer != null) {
                                CyuConcurrency.scheduler.runEntity(plugin, senderPlayer) {
                                    senderPlayer.sendLang("tp-friend-only")
                                }
                            } else {
                                proxyGateway?.sendTeleportFail(senderUid, "not-friend")
                            }
                            return@runAsync
                        }

                        if (!module.preferencesManager.canReceiveTeleportCached(receiverUid, senderUid) && !runBlocking { module.preferencesManager.canReceiveTeleportFrom(receiverUid, senderUid) }) {
                            DebugLogger.debug(1) { "好友传送接受拦截: receiver=$receiverUid sender=$senderUid reason=tp-not-allowed" }
                            module.teleportManager.clearRequest(receiverUid)
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tp-not-allowed")
                            }
                            if (senderOnline && senderPlayer != null) {
                                CyuConcurrency.scheduler.runEntity(plugin, senderPlayer) {
                                    senderPlayer.sendLang("tp-not-allowed")
                                }
                            } else {
                                proxyGateway?.sendTeleportFail(senderUid, "not-allowed")
                            }
                            return@runAsync
                        }

                        if (isTeleportDisabled(plugin, receiverWorldName)) {
                            DebugLogger.debug(1) { "好友传送接受拦截: receiver=$receiverUid sender=$senderUid reason=tp-world-disabled world=$receiverWorldName" }
                            module.teleportManager.clearRequest(receiverUid)
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tp-world-disabled")
                            }
                            if (senderOnline && senderPlayer != null) {
                                CyuConcurrency.scheduler.runEntity(plugin, senderPlayer) {
                                    senderPlayer.sendLang("tp-world-disabled")
                                }
                            } else {
                                proxyGateway?.sendTeleportFail(senderUid, "world-disabled")
                            }
                            return@runAsync
                        }

                        module.teleportManager.clearRequest(receiverUid)

                        if (senderOnline && senderPlayer != null) {
                            CyuConcurrency.scheduler.runEntity(plugin, senderPlayer) {
                                senderPlayer.teleportAsync(receiverLocation).thenAccept { success ->
                                    if (success) {
                                        senderPlayer.sendLang("tp-success", mapOf("target" to receiverName))
                                        player.sendLang("tp-accepted", mapOf("sender" to senderPlayer.name))
                                    } else {
                                        senderPlayer.sendLang("tp-failed")
                                        player.sendLang("tp-failed")
                                    }
                                }
                            }
                            return@runAsync
                        }

                        val sent = proxyGateway?.sendTeleportTransfer(senderUid, receiverUid, receiverName)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (sent == null) {
                                player.sendLang("tp-failed")
                            } else {
                                player.sendLang(
                                    "tp-cross-server-transfer",
                                    mapOf(
                                        "sender" to request.senderName,
                                        "server" to plugin.displayServerName(request.sourceServer)
                                    )
                                )
                            }
                        }
                        if (sent == null) {
                            proxyGateway?.sendTeleportFail(senderUid, "failed")
                        }
                    }
                }
            }

            subCommand("tpdeny") {
                alias("tpdecline")
                requirePlayer = true
                permission = "cyufriends.command.tp"

                executes {
                    val receiverUid = player.uid
                    val request = module.teleportManager.getRequest(receiverUid) ?: return@executes player.sendLang("no-tp-request")
                    val senderUid = request.senderUid
                    val senderPlayer = CyuIdHook.getOnlinePlayer(senderUid)
                    val proxyGateway = proxyGateway(plugin)

                    module.teleportManager.clearRequest(receiverUid)

                    if (senderPlayer != null && senderPlayer.isOnline) {
                        CyuConcurrency.scheduler.runEntity(plugin, senderPlayer) {
                            senderPlayer.sendLang("tp-denied", mapOf("target" to player.name))
                        }
                    } else {
                        proxyGateway?.sendTeleportFail(senderUid, "denied", player.name)
                    }

                    player.sendLang("tp-deny-success", mapOf("sender" to request.senderName))
                }
            }

            subCommand("note") {
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-note")
                    val noteName = normalizeOptionalNoteName(args.drop(1).joinToString(" "))

                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking { module.friendManager.setNote(senderUid, targetUid, noteName) }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (noteName == null) {
                                player.sendLang("note-cleared", mapOf("target" to targetName))
                            } else {
                                player.sendLang("note-set", mapOf("target" to targetName, "note" to noteName))
                            }
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("notedetail") {
                alias("notemore", "notedesc")
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-notedetail")
                    val noteDetail = normalizeOptionalNoteDetail(args.drop(1).joinToString(" "))

                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriendStable(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking { module.friendManager.setNoteDetail(senderUid, targetUid, noteDetail) }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (noteDetail == null) {
                                player.sendLang("note-detail-cleared", mapOf("target" to targetName))
                            } else {
                                player.sendLang("note-detail-set", mapOf("target" to targetName, "detail" to noteDetail))
                            }
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("tag") {
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-tag")
                    val tagName = normalizeTag(args.drop(1).joinToString(" ")) ?: return@executes player.sendLang("usage-tag")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val currentTags = runBlocking { module.friendManager.getTagsStored(senderUid, targetUid) }
                        if (currentTags.any { it.equals(tagName, ignoreCase = true) }) {
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tag-already", mapOf("target" to targetName, "tag" to tagName))
                            }
                            return@runAsync
                        }
                        if (currentTags.size >= FriendDefaults.MAX_TAG_COUNT) {
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tag-limit-reached", mapOf("target" to targetName, "amount" to FriendDefaults.MAX_TAG_COUNT.toString()))
                            }
                            return@runAsync
                        }
                        runBlocking { module.friendManager.addTag(senderUid, targetUid, tagName) }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("tag-added", mapOf("target" to targetName, "tag" to tagName))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args.getOrElse(0) { "" })
                        else -> emptyList()
                    }
                }
            }

            subCommand("untag") {
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-untag")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)
                    val tagName = normalizeTag(args.drop(1).joinToString(" "))

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        if (tagName == null) {
                            runBlocking { module.friendManager.clearTags(senderUid, targetUid) }
                            proxyGateway?.invalidateRelation(senderUid)
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tag-cleared", mapOf("target" to targetName))
                                player.playAudio("success")
                            }
                            return@runAsync
                        }
                        val removed = runBlocking { module.friendManager.removeTag(senderUid, targetUid, tagName) }
                        if (!removed) {
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tag-missing", mapOf("target" to targetName, "tag" to tagName))
                            }
                            return@runAsync
                        }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("tag-removed", mapOf("target" to targetName, "tag" to tagName))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args.getOrElse(0) { "" })
                        2 -> friendTagCompletions(module, player, args[0], args[1])
                        else -> emptyList()
                    }
                }
            }

            subCommand("tags") {
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-tags")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val tags = runBlocking { module.friendManager.getTagsStored(senderUid, targetUid) }
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (tags.isEmpty()) {
                                player.sendLang("tag-list-empty", mapOf("target" to targetName))
                                return@runEntity
                            }
                            player.sendLang(
                                "tag-list-header",
                                mapOf(
                                    "target" to targetName,
                                    "amount" to tags.size.toString(),
                                    "tags" to tags.joinToString("、")
                                )
                            )
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("tagprimary") {
                alias("tagmain", "primarytag")
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-tagprimary")
                    val tagName = normalizeTag(args.drop(1).joinToString(" ")) ?: return@executes player.sendLang("usage-tagprimary")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val updated = runBlocking { module.friendManager.setPrimaryTag(senderUid, targetUid, tagName) }
                        if (!updated) {
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                player.sendLang("tag-missing", mapOf("target" to targetName, "tag" to tagName))
                            }
                            return@runAsync
                        }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("tag-primary-set", mapOf("target" to targetName, "tag" to tagName))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args.getOrElse(0) { "" })
                        2 -> friendTagCompletions(module, player, args[0], args[1])
                        else -> emptyList()
                    }
                }
            }

            subCommand("tagcolor") {
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-tagcolor")
                    val parsed = parseTagColorArgs(args) ?: return@executes player.sendLang("usage-tagcolor")
                    val tagName = parsed.first
                    val color = parsed.second
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val updated = runBlocking { module.friendManager.setTagColor(senderUid, targetUid, tagName, color) }
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (!updated) {
                                player.sendLang("tag-missing", mapOf("target" to displayName, "tag" to tagName))
                                return@runEntity
                            }
                            proxyGateway?.invalidateRelation(senderUid)
                            player.sendLang("tag-color-set", mapOf("target" to displayName, "tag" to tagName, "color" to color))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args.getOrElse(0) { "" })
                        2 -> friendTagCompletions(module, player, args[0], args[1])
                        3 -> filterCompletions(tagColorSuggestions(), args[2])
                        else -> emptyList()
                    }
                }
            }

            subCommand("untagcolor") {
                alias("tagcolorclear", "cleartagcolor")
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-untagcolor")
                    val tagName = normalizeTag(args.drop(1).joinToString(" ")) ?: return@executes player.sendLang("usage-untagcolor")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val updated = runBlocking { module.friendManager.clearTagColor(senderUid, targetUid, tagName) }
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            if (!updated) {
                                player.sendLang("tag-missing", mapOf("target" to displayName, "tag" to tagName))
                                return@runEntity
                            }
                            proxyGateway?.invalidateRelation(senderUid)
                            player.sendLang("tag-color-cleared", mapOf("target" to displayName, "tag" to tagName))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args.getOrElse(0) { "" })
                        2 -> friendTagCompletions(module, player, args[0], args[1])
                        else -> emptyList()
                    }
                }
            }

            subCommand("tagfilter") {
                alias("filtertag", "tagview")
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val input = args.joinToString(" ").trim()
                    val currentState = FriendListStateStore.get(player.uid)
                    if (input.isBlank()) {
                        openGui(player, plugin, "friend_tag_filters.yml", ViewTitles.friendTagFilters(currentState.filterTag), filterTitleReplacements(currentState.filterTag)) { pattern, items, title ->
                            FriendTagFilterView(player, pattern, items, module, currentState.filterTag, title).open()
                        }
                        return@executes
                    }

                    if (input.equals("clear", ignoreCase = true)) {
                        val updatedState = FriendListStateStore.update(player.uid) { it.copy(filterTag = null) }
                        player.sendLang("tag-filter-cleared")
                        openFriendsList(plugin, player, module, updatedState)
                        return@executes
                    }

                    val resolvedTag = module.friendManager.findOwnedTag(player.uid, input)
                        ?: return@executes player.sendLang("tag-filter-empty", mapOf("tag" to input))

                    val updatedState = FriendListStateStore.update(player.uid) { it.copy(filterTag = resolvedTag) }
                    player.sendLang("tag-filter-applied", mapOf("tag" to resolvedTag))
                    openFriendsList(plugin, player, module, updatedState)
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    filterCompletions(listOf("clear") + module.friendManager.tagSummaries(player.uid).map { it.name }, args.getOrElse(0) { "" })
                }
            }

            subCommand("tagfilterpanel") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val input = args.joinToString(" ").trim().takeUnless { it.isBlank() || it.equals("__none__", ignoreCase = true) }
                    val resolved = input?.let { module.friendManager.findOwnedTag(player.uid, it) } ?: input
                    openGui(player, plugin, "friend_tag_filters.yml", ViewTitles.friendTagFilters(resolved), filterTitleReplacements(resolved)) { pattern, items, title ->
                        FriendTagFilterView(player, pattern, items, module, resolved, title).open()
                    }
                }
            }

            subCommand("tagpanel") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-tags")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    if (!module.friendManager.isFriend(player.uid, targetUid)) return@executes player.sendLang("not-friend")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName
                    val friendData = module.friendManager.getFriendData(player.uid, targetUid)
                    if (friendData == null || friendData.tagNames.isEmpty()) return@executes player.sendLang("tag-list-empty", mapOf("target" to displayName))
                    openGui(player, plugin, "friend_tag_manage.yml", ViewTitles.friendTagManage(displayName), targetTitleReplacements(displayName)) { pattern, items, title ->
                        FriendTagManageView(player, pattern, items, module, displayName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("tagcolorpanel") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-tagcolor")
                    val tagName = normalizeTag(args.drop(1).joinToString(" ")) ?: return@executes player.sendLang("usage-tagcolor")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    if (!module.friendManager.isFriend(player.uid, targetUid)) return@executes player.sendLang("not-friend")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName
                    val resolvedTag = module.friendManager.getFriendData(player.uid, targetUid)
                        ?.orderedTags()
                        ?.firstOrNull { it.equals(tagName, ignoreCase = true) }
                        ?: return@executes player.sendLang("tag-missing", mapOf("target" to displayName, "tag" to tagName))
                    openGui(player, plugin, "friend_tag_colors.yml", ViewTitles.friendTagColors(displayName, resolvedTag), targetAndTagTitleReplacements(displayName, resolvedTag)) { pattern, items, title ->
                        FriendTagColorView(player, pattern, items, module, displayName, resolvedTag, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args.getOrElse(0) { "" })
                        2 -> friendTagCompletions(module, player, args[0], args[1])
                        else -> emptyList()
                    }
                }
            }

            subCommand("pin") {
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-pin")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking { module.friendManager.setPinned(senderUid, targetUid, true) }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("pin-enabled", mapOf("target" to targetName))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("unpin") {
                requirePlayer = true
                permission = "cyufriends.command.note"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-unpin")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriend(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking { module.friendManager.setPinned(senderUid, targetUid, false) }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("pin-disabled", mapOf("target" to targetName))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("group") {
                requirePlayer = true
                permission = "cyufriends.command.group"

                executes {
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group")
                        ?: return@executes moduleUnavailable(player, "group")
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-group")
                    val groupName = groupModule.manager.normalize(args.drop(1).joinToString(" "))

                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val senderUid = player.uid

                    if (!module.friendManager.isFriendStable(senderUid, targetUid)) return@executes player.sendLang("not-friend")
                    val proxyGateway = proxyGateway(plugin)

                    CyuConcurrency.scheduler.runAsync(plugin) {
                        runBlocking { groupModule.manager.moveFriend(senderUid, targetUid, groupName) }
                        proxyGateway?.invalidateRelation(senderUid)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang("group-set", mapOf("target" to targetName, "group" to groupName))
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group") ?: return@tabComplete emptyList()
                    when (args.size) {
                        1 -> friendNameCompletions(module, player, args[0])
                        2 -> groupCompletions(groupModule, player, args[1])
                        else -> emptyList()
                    }
                }
            }

            subCommand("grouplist") {
                alias("groups")
                requirePlayer = true
                permission = "cyufriends.command.group"

                executes {
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group")
                        ?: return@executes moduleUnavailable(player, "group")
                    val opened = openGui(player, plugin, "groups_list.yml", ViewTitles.friendGroups()) { pattern, items, title ->
                        GroupListView(player, pattern, items, groupModule, title).open()
                    }
                    if (opened) return@executes

                    val groups = groupModule.manager.groupedFriends(player.uid)
                    if (groups.isEmpty()) return@executes player.sendLang("group-list-empty")

                    player.sendLang("group-list-header")
                    groups.forEach { (groupName, members) ->
                        player.sendLang("group-list-entry", mapOf("group" to groupName, "count" to members.size.toString()))
                    }
                }
            }

            subCommand("groupmembers") {
                requirePlayer = true
                permission = "cyufriends.command.group"

                executes {
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group")
                        ?: return@executes moduleUnavailable(player, "group")
                    val groupName = args.joinToString(" ").takeIf { it.isNotBlank() } ?: return@executes player.sendLang("usage-groupmembers")
                    openGui(player, plugin, "group_members.yml", ViewTitles.groupMembers(groupName), groupTitleReplacements(groupName)) { pattern, items, title ->
                        GroupMembersView(player, pattern, items, groupModule, groupName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group") ?: return@tabComplete emptyList()
                    groupCompletions(groupModule, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("groupmoveall") {
                alias("groupbatchmove")
                requirePlayer = true
                permission = "cyufriends.command.group"

                executes {
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group")
                        ?: return@executes moduleUnavailable(player, "group")
                    val parsedArgs = parseGroupMoveAllArgs(args)
                        ?: return@executes player.sendLang("usage-groupmoveall")
                    val normalizedSource = groupModule.manager.normalize(parsedArgs.sourceGroup)
                    if (parsedArgs.targetGroup == null) {
                        openGui(
                            player,
                            plugin,
                            "group_batch_move.yml",
                            ViewTitles.groupBatchMove(normalizedSource),
                            groupTitleReplacements(normalizedSource)
                        ) { pattern, items, title ->
                            GroupBatchMoveView(player, pattern, items, groupModule, normalizedSource, title).open()
                        }
                        return@executes
                    }

                    val normalizedTarget = groupModule.manager.normalize(parsedArgs.targetGroup)
                    if (normalizedTarget == normalizedSource) {
                        return@executes player.sendLang("group-batch-move-same")
                    }
                    val ownerUid = player.uid
                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val moved = runBlocking { groupModule.manager.moveGroup(ownerUid, normalizedSource, normalizedTarget) }
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            player.sendLang(
                                "group-batch-move-success",
                                mapOf(
                                    "source" to normalizedSource,
                                    "target" to normalizedTarget,
                                    "amount" to moved.toString()
                                )
                            )
                            player.playAudio("success")
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group") ?: return@tabComplete emptyList()
                    val groups = (groupModule.manager.groups(player.uid) + "未分组").distinct()
                    when (args.size) {
                        1 -> filterCompletions(groups, args[0])
                        else -> filterCompletions(groups.filterNot { it == getArg(0) }, args.lastOrNull() ?: "")
                    }
                }
            }

            subCommand("grouprules") {
                requirePlayer = true
                permission = "cyufriends.command.group"

                executes {
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group")
                        ?: return@executes moduleUnavailable(player, "group")
                    val rawAction = getArg(0)
                    if (rawAction == "--") {
                        val groupName = args.drop(1).joinToString(" ").takeIf { it.isNotBlank() }
                            ?: return@executes player.sendLang("usage-grouprules")
                        openGroupRulesGui(plugin, player, groupModule, module, groupName)
                        return@executes
                    }
                    when (val action = rawAction?.lowercase()) {
                        null -> player.sendLang("usage-grouprules")
                        "show", "view", "info" -> {
                            val groupInput = args.drop(1).joinToString(" ").trim()
                            if (groupInput.isBlank()) {
                                if (!openLiteralGroupRulesGui(plugin, player, groupModule, module, rawAction)) {
                                    player.sendLang("usage-grouprules")
                                }
                                return@executes
                            }
                            sendGroupRuleSummary(player, module, groupModule.manager.normalize(groupInput))
                        }
                        "cycle", "next", "toggle" -> {
                            if (args.size < 3) {
                                if (args.size == 1 && openLiteralGroupRulesGui(plugin, player, groupModule, module, rawAction)) {
                                    return@executes
                                }
                                return@executes player.sendLang("usage-grouprules")
                            }
                            val groupInput = args.drop(1).dropLast(1).joinToString(" ").trim()
                            if (groupInput.isBlank()) return@executes player.sendLang("usage-grouprules")
                            val rule = parseGroupRuleKey(args.last()) ?: return@executes player.sendLang("usage-grouprules")
                            val groupName = groupModule.manager.normalize(groupInput)
                            val proxyGateway = proxyGateway(plugin)
                            val ownerUid = player.uid
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                val updatedState = when (rule) {
                                    "pin" -> {
                                        runBlocking { module.preferencesManager.toggleGroupPinned(ownerUid, groupName) }
                                        groupPinStateName(module.preferencesManager.isGroupPinnedStoredSync(ownerUid, groupName))
                                    }
                                    else -> {
                                        val type = groupRuleType(rule) ?: return@runAsync
                                        val settings = runBlocking { module.preferencesManager.toggleGroup(ownerUid, groupName, type) }
                                        groupRuleStateName(settings, type)
                                    }
                                }
                                proxyGateway?.invalidateSettings(ownerUid)
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang(
                                        "group-rule-updated",
                                        mapOf("group" to groupName, "rule" to groupRuleLabel(rule), "state" to updatedState)
                                    )
                                    player.playAudio("success")
                                }
                            }
                        }
                        "set" -> {
                            if (args.size < 4) {
                                if (args.size == 1 && openLiteralGroupRulesGui(plugin, player, groupModule, module, rawAction)) {
                                    return@executes
                                }
                                return@executes player.sendLang("usage-grouprules")
                            }
                            val groupInput = args.drop(1).dropLast(2).joinToString(" ").trim()
                            if (groupInput.isBlank()) return@executes player.sendLang("usage-grouprules")
                            val rule = parseGroupRuleKey(args[args.lastIndex - 1]) ?: return@executes player.sendLang("usage-grouprules")
                            val stateInput = args.last()
                            val groupName = groupModule.manager.normalize(groupInput)
                            val proxyGateway = proxyGateway(plugin)
                            val ownerUid = player.uid
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                val updatedState = when (rule) {
                                    "pin" -> {
                                        val pinned = parsePinnedState(stateInput) ?: run {
                                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                                player.sendLang("group-rule-invalid-state", mapOf("rule" to groupRuleLabel(rule), "states" to "on / off"))
                                            }
                                            return@runAsync
                                        }
                                        runBlocking { module.preferencesManager.setGroupPinned(ownerUid, groupName, pinned) }
                                        groupPinStateName(pinned)
                                    }
                                    else -> {
                                        val type = groupRuleType(rule) ?: return@runAsync
                                        val state = parseGroupRuleState(type, stateInput) ?: run {
                                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                                player.sendLang(
                                                    "group-rule-invalid-state",
                                                    mapOf("rule" to groupRuleLabel(rule), "states" to groupRuleStateOptions(type))
                                                )
                                            }
                                            return@runAsync
                                        }
                                        val settings = runBlocking { module.preferencesManager.setGroup(ownerUid, groupName, type, state) }
                                        groupRuleStateName(settings, type)
                                    }
                                }
                                proxyGateway?.invalidateSettings(ownerUid)
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang(
                                        "group-rule-updated",
                                        mapOf("group" to groupName, "rule" to groupRuleLabel(rule), "state" to updatedState)
                                    )
                                    player.playAudio("success")
                                }
                            }
                        }
                        else -> {
                            val groupName = args.joinToString(" ").takeIf { it.isNotBlank() } ?: return@executes player.sendLang("usage-grouprules")
                            openGroupRulesGui(plugin, player, groupModule, module, groupName)
                        }
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group") ?: return@tabComplete emptyList()
                    when (args.size) {
                        1 -> filterCompletions(listOf("show", "set", "cycle"), args[0]) + groupCompletions(groupModule, player, args[0])
                        2 -> when (args[0].lowercase()) {
                            "show", "view", "info" -> groupCompletions(groupModule, player, args[1])
                            else -> emptyList()
                        }
                        3 -> when (args[0].lowercase()) {
                            "cycle", "next", "toggle" -> filterCompletions(listOf("tp", "notify", "notifyme", "pin"), args[2])
                            else -> emptyList()
                        }
                        4 -> if (args[0].equals("set", ignoreCase = true)) {
                            filterCompletions(listOf("tp", "notify", "notifyme", "pin"), args[3])
                        } else {
                            emptyList()
                        }
                        5 -> if (args[0].equals("set", ignoreCase = true)) {
                            when (parseGroupRuleKey(args[3])) {
                                "tp" -> filterCompletions(listOf("default", "allow", "confirm", "deny"), args[4])
                                "notify", "notifyme" -> filterCompletions(listOf("default", "allow", "deny"), args[4])
                                "pin" -> filterCompletions(listOf("on", "off"), args[4])
                                else -> emptyList()
                            }
                        } else {
                            emptyList()
                        }
                        else -> emptyList()
                    }
                }
            }

            subCommand("gui") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                        ?: return@executes moduleUnavailable(player, "profile")
                    openGui(player, plugin, "profile_home.yml", ViewTitles.profileHome(player.name)) { pattern, items, title ->
                        ProfileHomeView(player, pattern, items, plugin, profileModule, title).open()
                    }
                }
            }

            subCommand("home") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                    if (profileModule != null) {
                        openProfileHome(plugin, profileModule, player)
                    } else {
                        openFriendsList(plugin, player, module)
                    }
                }
            }

            subCommand("list") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    openFriendsList(plugin, player, module)
                }

                subCommand("clear") {
                    executes {
                        FriendListStateStore.clear(player.uid)
                        player.sendLang("friend-list-cleared")
                        openFriendsList(plugin, player, module)
                    }
                }

                subCommand("search") {
                    executes {
                        val input = args.joinToString(" ").trim()
                        if (input.isBlank()) return@executes player.sendLang("usage-list-search")
                        if (input.equals("clear", ignoreCase = true)) {
                            val updatedState = FriendListStateStore.update(player.uid) { it.copy(keyword = null) }
                            player.sendLang("friend-list-search-cleared")
                            openFriendsList(plugin, player, module, updatedState)
                            return@executes
                        }

                        val updatedState = FriendListStateStore.update(player.uid) { it.copy(keyword = input) }
                        player.sendLang("friend-list-search-applied", mapOf("keyword" to input))
                        openFriendsList(plugin, player, module, updatedState)
                    }

                    tabComplete {
                        filterCompletions(listOf("clear"), args.getOrElse(0) { "" })
                    }
                }

                subCommand("sort") {
                    executes {
                        val input = getArg(0)
                        val updatedState = when {
                            input.isNullOrBlank() || input.equals("cycle", ignoreCase = true) -> {
                                FriendListStateStore.update(player.uid) { it.copy(sortMode = it.sortMode.cycle()) }
                            }

                            else -> {
                                val resolved = resolveFriendListSort(input) ?: return@executes player.sendLang("usage-list-sort")
                                FriendListStateStore.update(player.uid) { it.copy(sortMode = resolved) }
                            }
                        }
                        player.sendLang("friend-list-sort-applied", mapOf("mode" to updatedState.sortMode.displayName))
                        openFriendsList(plugin, player, module, updatedState)
                    }

                    tabComplete {
                        filterCompletions(listOf("cycle", "recent", "online", "server", "name", "default"), args.getOrElse(0) { "" })
                    }
                }
            }

            subCommand("requests") {
                requirePlayer = true
                permission = "cyufriends.command.requests"

                executes {
                    when {
                        isChatListMode(getArg(0)) -> showIncomingRequestsInChat(plugin, module, player)
                        getArg(0) == null -> openGui(player, plugin, "requests_list.yml", ViewTitles.requestsList()) { pattern, items, title ->
                            RequestsView(player, pattern, items, module, title).open()
                        }
                        else -> player.sendLang("usage-requests")
                    }
                }

                tabComplete {
                    filterCompletions(listOf("chat", "list", "text"), args.getOrElse(0) { "" })
                }
            }

            subCommand("sentrequests") {
                requirePlayer = true
                permission = "cyufriends.command.requests"

                executes {
                    when {
                        isChatListMode(getArg(0)) -> showSentRequestsInChat(plugin, module, player)
                        getArg(0) == null -> openGui(player, plugin, "sent_requests.yml", ViewTitles.sentRequestsList()) { pattern, items, title ->
                            SentRequestsView(player, pattern, items, module, title).open()
                        }
                        else -> player.sendLang("usage-sentrequests")
                    }
                }

                tabComplete {
                    filterCompletions(listOf("chat", "list", "text"), args.getOrElse(0) { "" })
                }
            }

            subCommand("notifications") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                        ?: return@executes moduleUnavailable(player, "profile")
                    openGui(player, plugin, "notification_center.yml", ViewTitles.notificationCenter()) { pattern, items, title ->
                        NotificationCenterView(player, pattern, items, plugin, profileModule, title).open()
                    }
                }
            }

            subCommand("recommend") {
                alias("recommendations")
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    when (getArg(0)?.lowercase()) {
                        null -> openGui(player, plugin, "friend_recommendations.yml", ViewTitles.friendRecommendations()) { pattern, items, title ->
                            RecommendationsView(player, pattern, items, module, title).open()
                        }
                        "snooze", "later", "mute" -> {
                            val targetInput = getArg(1) ?: return@executes player.sendLang("usage-recommend")
                            val targetUid = resolveProfileTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                            val ownerUid = player.uid
                            if (targetUid == ownerUid) return@executes player.sendLang("recommend-self-invalid")
                            val days = getArg(2)?.toLongOrNull()
                                ?: plugin.config.getLong("recommendation.snooze-days", 14L)
                            if (days <= 0L) return@executes player.sendLang("recommend-days-invalid")
                            val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                            val expiresAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                module.friendManager.ignoreRecommendationSync(ownerUid, targetUid, expiresAt)
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("recommend-snoozed", mapOf("target" to targetName, "days" to days.toString()))
                                    player.playAudio("success")
                                }
                            }
                        }
                        "hide", "dismiss" -> {
                            val targetInput = getArg(1) ?: return@executes player.sendLang("usage-recommend")
                            val targetUid = resolveProfileTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                            val ownerUid = player.uid
                            if (targetUid == ownerUid) return@executes player.sendLang("recommend-self-invalid")
                            val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                module.friendManager.ignoreRecommendationSync(ownerUid, targetUid, 0L)
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    player.sendLang("recommend-hidden", mapOf("target" to targetName))
                                    player.playAudio("success")
                                }
                            }
                        }
                        "restore", "unhide", "show" -> {
                            val targetInput = getArg(1) ?: return@executes player.sendLang("usage-recommend")
                            val targetUid = resolveProfileTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                            val ownerUid = player.uid
                            if (targetUid == ownerUid) return@executes player.sendLang("recommend-self-invalid")
                            val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                            CyuConcurrency.scheduler.runAsync(plugin) {
                                val restored = module.friendManager.clearRecommendationIgnoreSync(ownerUid, targetUid)
                                CyuConcurrency.scheduler.runEntity(plugin, player) {
                                    if (restored) {
                                        player.sendLang("recommend-restored", mapOf("target" to targetName))
                                        player.playAudio("success")
                                    } else {
                                        player.sendLang("recommend-restore-missing", mapOf("target" to targetName))
                                    }
                                }
                            }
                        }
                        else -> player.sendLang("usage-recommend")
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> filterCompletions(listOf("snooze", "hide", "restore"), args[0])
                        2 -> when (args[0].lowercase()) {
                            "snooze", "later", "mute", "hide", "dismiss", "restore", "unhide", "show" -> {
                                filterCompletions(profileTargets(module, player), args[1])
                            }
                            else -> emptyList()
                        }
                        3 -> if (args[0].equals("snooze", ignoreCase = true) || args[0].equals("later", ignoreCase = true) || args[0].equals("mute", ignoreCase = true)) {
                            filterCompletions(listOf("3", "7", "14", "30"), args[2])
                        } else {
                            emptyList()
                        }
                        else -> emptyList()
                    }
                }
            }

            subCommand("birthdays") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                        ?: return@executes moduleUnavailable(player, "profile")
                    val opened = openGui(player, plugin, "birthdays_list.yml", ViewTitles.birthdays()) { pattern, items, title ->
                        BirthdaysView(player, pattern, items, plugin, profileModule, title).open()
                    }
                    if (opened) return@executes
                    val ownerUid = player.uid
                    CyuConcurrency.scheduler.runAsync(plugin) {
                        val lines = buildBirthdaySummary(ownerUid, module, profileModule)
                        CyuConcurrency.scheduler.runEntity(plugin, player) {
                            lines.forEach(player::sendMessage)
                        }
                    }
                }
            }

            subCommand("profile") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    if (getArg(0)?.equals("set", ignoreCase = true) == true && getArg(1)?.equals("birthday", ignoreCase = true) == true) {
                        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            ?: return@executes moduleUnavailable(player, "profile")
                        val birthday = getArg(2) ?: return@executes player.sendLang("usage-birthday")
                        val uid = player.uid
                        val limit = profileModule.manager.birthdayLimit(player)
                        val proxyGateway = proxyGateway(plugin)
                        CyuConcurrency.scheduler.runAsync(plugin) {
                            val result = runBlocking { profileModule.manager.setBirthday(uid, birthday, limit) }
                            if (result == BirthdaySetResult.SUCCESS) {
                                proxyGateway?.invalidateProfile(uid)
                            }
                            CyuConcurrency.scheduler.runEntity(plugin, player) {
                                when (result) {
                                    BirthdaySetResult.SUCCESS -> {
                                        player.sendLang("birthday-set-success", mapOf("birthday" to birthday))
                                        player.playAudio("birthday-set-success")
                                    }
                                    BirthdaySetResult.INVALID_FORMAT -> {
                                        player.sendLang("birthday-invalid-format")
                                        player.playAudio("birthday-set-failed")
                                    }
                                    BirthdaySetResult.LIMIT_REACHED -> {
                                        player.sendLang("birthday-limit-reached")
                                        player.playAudio("birthday-set-failed")
                                    }
                                }
                            }
                        }
                        return@executes
                    }

                    val targetInput = getArg(0)
                    if (targetInput == null) {
                        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            ?: return@executes moduleUnavailable(player, "profile")
                        openProfileHome(plugin, profileModule, player)
                        return@executes
                    }

                    val targetUid = resolveProfileTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                    if (targetUid == player.uid) {
                        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            ?: return@executes moduleUnavailable(player, "profile")
                        openProfileHome(plugin, profileModule, player)
                        return@executes
                    }

                    val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                    if (!module.friendManager.isFriendStable(player.uid, targetUid)) {
                        openAddFriendGui(plugin, player, targetName)
                        return@executes
                    }

                    openFriendProfile(plugin, player, module, targetUid, targetName)
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    when (args.size) {
                        1 -> filterCompletions(listOf("set") + profileTargets(module, player), args[0])
                        2 -> if (args[0].equals("set", ignoreCase = true)) filterCompletions(listOf("birthday"), args[1]) else emptyList()
                        3 -> if (args[0].equals("set", ignoreCase = true) && args[1].equals("birthday", ignoreCase = true)) filterCompletions(listOf("2000-01-01"), args[2]) else emptyList()
                        else -> emptyList()
                    }
                }
            }

            subCommand("removeconfirm") {
                requirePlayer = true
                permission = "cyufriends.command.remove"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-remove")
                    val targetUid = resolveFriendUid(module, player.uid, targetName) ?: return@executes player.sendLang("player-not-found")
                    if (!module.friendManager.isFriendStable(player.uid, targetUid)) return@executes player.sendLang("not-friend")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName
                    openGui(player, plugin, "friend_remove_confirm.yml", ViewTitles.friendRemoveConfirm(displayName), targetTitleReplacements(displayName)) { pattern, items, title ->
                        FriendRemoveConfirmView(player, pattern, items, module, targetUid, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("groupmove") {
                requirePlayer = true
                permission = "cyufriends.command.group"

                executes {
                    val groupModule = plugin.moduleManager.getModule<GroupModule>("group")
                        ?: return@executes moduleUnavailable(player, "group")
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-group")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    if (!module.friendManager.isFriendStable(player.uid, targetUid)) return@executes player.sendLang("not-friend")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName
                    openGui(player, plugin, "group_move.yml", ViewTitles.groupMove(displayName), targetTitleReplacements(displayName)) { pattern, items, title ->
                        GroupMoveView(player, pattern, items, groupModule, displayName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("chat") {
                requirePlayer = true
                permission = "cyufriends.command.msg"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-msg")
                    val targetUid = CyuIdHook.getUidByName(targetName) ?: return@executes player.sendLang("player-not-found")
                    val displayName = CyuIdHook.getName(targetUid) ?: targetName
                    if (targetUid == player.uid) {
                        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            ?: return@executes player.sendLang("cannot-msg-self")
                        openProfileHome(plugin, profileModule, player)
                        return@executes
                    }
                    if (!module.friendManager.isFriendStable(player.uid, targetUid)) {
                        openAddFriendGui(plugin, player, displayName)
                        return@executes
                    }
                    val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
                        ?: return@executes moduleUnavailable(player, "chat")
                    openGui(player, plugin, "message_chat.yml", ViewTitles.privateChat(displayName), targetTitleReplacements(displayName)) { pattern, items, title ->
                        MessageChatView(player, pattern, items, chatModule, displayName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    friendNameCompletions(module, player, args.getOrElse(0) { "" })
                }
            }

            subCommand("profiledetail") {
                alias("profiledetails")
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val targetInput = getArg(0) ?: return@executes player.sendLang("usage-profiledetail")
                    val targetUid = resolveProfileTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                    if (targetUid == player.uid) {
                        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            ?: return@executes moduleUnavailable(player, "profile")
                        openProfileHome(plugin, profileModule, player)
                        return@executes
                    }

                    val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                    if (!module.friendManager.isFriendStable(player.uid, targetUid)) {
                        openAddFriendGui(plugin, player, targetName)
                        return@executes
                    }

                    openFriendProfile(plugin, player, module, targetUid, targetName, detailed = true)
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    profileTargets(module, player)
                }
            }

            subCommand("timeline") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val targetInput = getArg(0) ?: return@executes player.sendLang("usage-timeline")
                    val targetUid = resolveProfileTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                    if (targetUid == player.uid) {
                        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            ?: return@executes moduleUnavailable(player, "profile")
                        openProfileHome(plugin, profileModule, player)
                        return@executes
                    }

                    val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                    if (!module.friendManager.isFriendStable(player.uid, targetUid)) {
                        openAddFriendGui(plugin, player, targetName)
                        return@executes
                    }

                    openGui(player, plugin, "friend_timeline.yml", ViewTitles.friendTimeline(targetName), targetTitleReplacements(targetName)) { pattern, items, title ->
                        FriendTimelineView(player, pattern, items, module, targetName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    profileTargets(module, player)
                }
            }

            subCommand("profilesocial") {
                alias("friendsocial", "personalsocial")
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    if (!plugin.moduleManager.isEnabled("social")) {
                        moduleUnavailable(player, "social")
                        return@executes
                    }
                    val targetInput = getArg(0) ?: return@executes player.sendLang("usage-profilesocial")
                    val targetUid = resolveProfileTarget(targetInput) ?: return@executes player.sendLang("player-not-found")
                    if (targetUid == player.uid) {
                        val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                            ?: return@executes moduleUnavailable(player, "profile")
                        openProfileHome(plugin, profileModule, player)
                        return@executes
                    }

                    val targetName = CyuIdHook.getName(targetUid) ?: targetInput
                    if (!module.friendManager.isFriendStable(player.uid, targetUid)) {
                        openAddFriendGui(plugin, player, targetName)
                        return@executes
                    }

                    openGui(player, plugin, "friend_profile_social.yml", ViewTitles.friendProfileSocial(targetName), targetTitleReplacements(targetName)) { pattern, items, title ->
                        FriendProfileSocialView(player, pattern, items, module, targetName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    profileTargets(module, player)
                }
            }

            subCommand("contact") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val targetInput = getArg(0) ?: return@executes player.sendLang("player-not-found")
                    val targetUid = CyuIdHook.getUidByName(targetInput) ?: return@executes player.sendLang("player-not-found")
                    val fallbackName = args.drop(1).joinToString(" ").trim().takeIf { it.isNotBlank() }
                    val displayName = fallbackName ?: CyuIdHook.getName(targetUid) ?: targetInput
                    val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                    val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
                    val isFriend = module.friendManager.isFriendStable(player.uid, targetUid)

                    if (targetUid == player.uid) {
                        if (profileModule == null) {
                            moduleUnavailable(player, "profile")
                            return@executes
                        }
                        openProfileHome(plugin, profileModule, player)
                        return@executes
                    }

                    if (isFriend && chatModule != null) {
                        openGui(
                            player,
                            plugin,
                            "message_chat.yml",
                            ViewTitles.privateChat(displayName),
                            targetTitleReplacements(displayName)
                        ) { pattern, items, title ->
                            MessageChatView(player, pattern, items, chatModule, displayName, title).open()
                        }
                        return@executes
                    }

                    if (!isFriend) {
                        openGui(
                            player,
                            plugin,
                            "add_friend.yml",
                            ViewTitles.addFriend(displayName),
                            targetTitleReplacements(displayName) + mapOf(
                                "%target_uid%" to targetUid,
                                "%friend_uid%" to targetUid,
                                "%uid%" to targetUid
                            )
                        ) { pattern, items, title ->
                            AddFriendView(player, pattern, items, displayName, title).open()
                        }
                        return@executes
                    }

                    if (profileModule == null) {
                        moduleUnavailable(player, "profile")
                        return@executes
                    }

                    openGui(
                        player,
                        plugin,
                        "friend_profile.yml",
                        ViewTitles.friendProfile(displayName),
                        targetTitleReplacements(displayName) + mapOf(
                            "%target_uid%" to targetUid,
                            "%friend_uid%" to targetUid,
                            "%uid%" to targetUid
                        )
                    ) { pattern, items, title ->
                        FriendProfileView(player, pattern, items, module, targetUid, displayName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    profileTargets(module, player)
                }
            }

            subCommand("blacklist") {
                requirePlayer = true
                permission = "cyufriends.command.blacklist"

                executes {
                    openGui(player, plugin, "blacklist.yml", ViewTitles.blacklist()) { pattern, items, title ->
                        BlacklistView(player, pattern, items, module, title).open()
                    }
                }
            }

            subCommand("online") {
                alias("discover")
                requirePlayer = true
                permission = "cyufriends.command.online"

                executes {
                    openGui(player, plugin, "online_players.yml", ViewTitles.onlinePlayers()) { pattern, items, title ->
                        OnlinePlayersView(player, pattern, items, module, title).open()
                    }
                }
            }

            subCommand("messages") {
                alias("message", "mail")
                requirePlayer = true
                permission = "cyufriends.command.messages"

                executes {
                    if (!plugin.moduleManager.isEnabled("chat")) {
                        return@executes moduleUnavailable(player, "chat")
                    }
                    forwardPlayerCommand(player, "messages", args)
                }
            }

            subCommand("status") {
                requirePlayer = true
                permission = "cyufriends.command.status"

                executes {
                    if (!plugin.moduleManager.isEnabled("social")) {
                        return@executes moduleUnavailable(player, "social")
                    }
                    forwardPlayerCommand(player, "status", args)
                }
            }

            subCommand("wall") {
                requirePlayer = true
                permission = "cyufriends.command.wall"

                executes {
                    if (!plugin.moduleManager.isEnabled("social")) {
                        return@executes moduleUnavailable(player, "social")
                    }
                    forwardPlayerCommand(player, "wall", args)
                }
            }

            subCommand("settings") {
                requirePlayer = true
                permission = "cyufriends.command.settings"

                executes {
                    if (!plugin.moduleManager.isEnabled("profile")) {
                        return@executes moduleUnavailable(player, "profile")
                    }
                    forwardPlayerCommand(player, "settings", args)
                }
            }

            subCommand("socialsettings") {
                requirePlayer = true
                permission = "cyufriends.command.settings"

                executes {
                    if (!plugin.moduleManager.isEnabled("social")) {
                        return@executes moduleUnavailable(player, "social")
                    }
                    val profileModule = plugin.moduleManager.getModule<ProfileModule>("profile")
                        ?: return@executes moduleUnavailable(player, "profile")
                    openGui(player, plugin, "settings_social.yml", ViewTitles.socialSettings()) { pattern, items, title ->
                        SocialSettingsView(player, pattern, items, profileModule, title).open()
                    }
                }
            }

            subCommand("addgui") {
                requirePlayer = true
                permission = "cyufriends.command.gui"

                executes {
                    val targetName = getArg(0) ?: return@executes player.sendLang("usage-add")
                    openGui(player, plugin, "add_friend.yml", ViewTitles.addFriend(targetName), targetTitleReplacements(targetName)) { pattern, items, title ->
                        AddFriendView(player, pattern, items, targetName, title).open()
                    }
                }

                tabComplete {
                    if (!isPlayer) return@tabComplete emptyList()
                    addTargetCompletions(plugin, module, player, args.getOrElse(0) { "" })
                }
            }

        }.register()
    }

    private fun sendHelp(sender: CommandSender, pageArg: String?) {
        val plugin = CyufriendsReload.instance
        val lines = helpLineKeys(plugin)
        val totalPages = maxOf(1, (lines.size + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE)
        val parsedPage = pageArg?.toIntOrNull()
        if (pageArg != null && parsedPage == null) {
            plugin.langEngine.sendRaw(sender, "<red>页码无效，请输入数字。</red>")
            return
        }

        val page = if (parsedPage == null || parsedPage in 1..totalPages) parsedPage ?: 1 else 1
        if (parsedPage != null && parsedPage !in 1..totalPages) {
            plugin.langEngine.sendRaw(
                sender,
                "<gray>帮助页码超出范围，已为你显示第 <white>1</white> 页，共 <white>$totalPages</white> 页。</gray>"
            )
        }

        val start = (page - 1) * HELP_PAGE_SIZE
        val end = minOf(start + HELP_PAGE_SIZE, lines.size)

        sender.sendLang("help-border")
        sender.sendLang("help-title")
        lines.subList(start, end).forEach(sender::sendLang)
        plugin.langEngine.sendRaw(
            sender,
            "<gray>第 <white>$page</white>/<white>$totalPages</white> 页</gray> <dark_gray>|</dark_gray> <gray>使用 <white>/friend help [页码]</white> 翻页</gray>"
        )
        if (sender is Player && totalPages > 1) {
            FriendRichMessages.sendHelpPager(sender, page, totalPages)
        }
        sender.sendLang("help-border")
    }

    private fun helpPageCount(plugin: CyufriendsReload): Int {
        return maxOf(1, (helpLineKeys(plugin).size + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE)
    }

    private fun helpLineKeys(plugin: CyufriendsReload): List<String> {
        val lines = mutableListOf(
            "help-add",
            "help-revoke",
            "help-accept",
            "help-deny",
            "help-remove",
            "help-block",
            "help-unblock",
            "help-tp",
            "help-tpaccept",
            "help-tpdeny",
            "help-tptoggle",
            "help-personal",
            "help-notify",
            "help-notifyme",
            "help-note",
            "help-notedetail",
            "help-tag",
            "help-tags",
            "help-tagprimary",
            "help-tagcolor",
            "help-untagcolor",
            "help-tagfilter",
            "help-untag",
            "help-pin",
            "help-unpin"
        )
        if (plugin.moduleManager.isEnabled("group")) {
            lines += listOf("help-group", "help-grouplist", "help-grouprules", "help-groupmoveall")
        }
        if (plugin.moduleManager.isEnabled("chat")) {
            lines += listOf("help-chat", "help-messages", "help-msg", "help-reply")
        }
        if (plugin.moduleManager.isEnabled("profile")) {
            lines += listOf(
                "help-profile-birthday",
                "help-birthday",
                "help-birthdays",
                "help-gui",
                "help-notifications",
                "help-socialsettings",
                "help-settings"
            )
        }
        lines += listOf("help-list", "help-requests", "help-sentrequests", "help-recommend", "help-timeline", "help-admin", "help-admin-legacy")
        if (plugin.moduleManager.isEnabled("social")) {
            lines += listOf(
                "help-status",
                "help-wall",
                "help-profilesocial",
                "help-status-publish",
                "help-status-comment",
                "help-status-comments",
                "help-status-like",
                "help-status-pin",
                "help-wall-post",
                "help-wall-comment",
                "help-wall-comments",
                "help-wall-commentpending",
                "help-wall-commentapprove",
                "help-wall-commentreject",
                "help-wall-commentapproveall",
                "help-wall-commentrejectall",
                "help-wall-like",
                "help-wall-pin",
                "help-wall-pending",
                "help-wall-approve",
                "help-wall-approveall",
                "help-wall-reject",
                "help-wall-rejectall",
                "help-admin-moderation"
            )
        }
        return lines
    }

    private fun requestDailyLimit(plugin: CyufriendsReload, player: org.bukkit.entity.Player): Int {
        return permissionInt(plugin, player, "requestLimits", "daily", "cyufriends.request.", 20)
    }

    private fun requestCooldown(plugin: CyufriendsReload, player: org.bukkit.entity.Player): Long {
        return permissionLong(plugin, player, "requestLimits", "cooldown", "cyufriends.request.", plugin.config.getLong("settings.request-cooldown", 60L))
    }

    private fun isTeleportDisabled(plugin: CyufriendsReload, worldName: String): Boolean {
        return plugin.config.getStringList("disabledWorlds").any { it.equals(worldName, ignoreCase = true) }
    }

    private fun forwardPlayerCommand(player: org.bukkit.entity.Player, root: String, arguments: List<String>) {
        player.performCommand(listOf(root).plus(arguments).joinToString(" "))
    }

    private fun friendNameCompletions(module: FriendModule, player: Player, prefix: String): List<String> {
        return filterCompletions(friendNames(module, player), prefix)
    }

    private fun friendNames(module: FriendModule, player: Player): List<String> {
        return module.friendManager.getFriendEntriesStoredSync(player.uid).map { CyuIdHook.getName(it.friendUid) ?: it.friendUid }
    }

    private fun uidNameCompletions(uids: Set<String>, prefix: String): List<String> {
        return filterCompletions(uids.map { CyuIdHook.getName(it) ?: it }, prefix)
    }

    private fun friendTagCompletions(module: FriendModule, player: Player, targetInput: String, prefix: String): List<String> {
        val targetUid = CyuIdHook.getUidByName(targetInput) ?: return emptyList()
        val friendData = module.friendManager.getFriendDataStoredSync(player.uid, targetUid) ?: return emptyList()
        return filterCompletions(friendData.tagNames, prefix)
    }

    private fun tagColorSuggestions(): List<String> {
        return listOf(
            "#5EC8FF",
            "#7ED7C1",
            "#F7C948",
            "#F78C6B",
            "#C792EA",
            "#FF7DAF",
            "#8BD450",
            "#9FB3C8",
            "aqua",
            "teal",
            "yellow",
            "orange",
            "purple",
            "pink",
            "green",
            "gray",
            "white"
        )
    }

    private fun groupCompletions(module: GroupModule, player: Player, prefix: String): List<String> {
        return filterCompletions(module.manager.groups(player.uid), prefix)
    }

    private fun profileTargets(module: FriendModule, player: Player): List<String> {
        return sequenceOf(
            sequenceOf(player.name),
            friendNames(module, player).asSequence(),
            CyufriendsReload.instance.globalOnlineEntries().asSequence().map { it.name }
        ).flatten()
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
            .toList()
    }

    private fun globalOnlineNames(plugin: CyufriendsReload): List<String> {
        return plugin.globalOnlineEntries()
            .sortedWith(compareBy({ it.remote }, { it.name.lowercase() }))
            .map { it.name }
            .distinct()
    }

    private fun blockTargetCompletions(plugin: CyufriendsReload, module: FriendModule, player: Player, prefix: String): List<String> {
        val values = plugin.globalOnlineEntries()
            .asSequence()
            .filter { it.uid != player.uid && !module.blockManager.isBlocked(player.uid, it.uid) }
            .map { it.name }
            .toList()
        return filterCompletions(values, prefix)
    }

    private fun addTargetCompletions(plugin: CyufriendsReload, module: FriendModule, player: Player, prefix: String): List<String> {
        val values = plugin.globalOnlineEntries()
            .asSequence()
            .filter { it.uid != player.uid && !module.friendManager.isFriendStable(player.uid, it.uid) }
            .map { it.name }
            .toList()
        return filterCompletions(values, prefix)
    }

    private fun tpTargetCompletions(plugin: CyufriendsReload, module: FriendModule, player: Player, prefix: String): List<String> {
        val values = plugin.globalOnlineEntries()
            .asSequence()
            .filter { it.uid != player.uid && module.friendManager.isFriendStable(player.uid, it.uid) }
            .map { it.name }
            .toList()
        return filterCompletions(values, prefix)
    }

    private fun moduleUnavailable(sender: CommandSender, moduleId: String) {
        sender.sendLang("module-disabled", mapOf("module" to moduleLabel(moduleId)))
    }

    private inline fun openGui(
        player: Player,
        plugin: CyufriendsReload,
        fileName: String,
        fallbackTitle: String,
        replacements: Map<String, String> = emptyMap(),
        open: (GuiPattern, Map<Char, ItemTemplate>, String) -> Unit
    ): Boolean {
        val guiData = GuiLoader.load(plugin, fileName)
        if (guiData == null) {
            player.sendLang("gui-open-failed")
            return false
        }
        open(guiData.pattern, guiData.items, guiData.resolveTitle(player, fallbackTitle, replacements))
        return true
    }

    private fun openFriendsList(
        plugin: CyufriendsReload,
        player: Player,
        module: FriendModule,
        state: FriendListState = FriendListStateStore.get(player.uid)
    ) {
        val normalizedState = state.normalized()
        openGui(
            player,
            plugin,
            "friends_list.yml",
            ViewTitles.friendsList(normalizedState.filterTag),
            friendListTitleReplacements(normalizedState)
        ) { pattern, items, title ->
            FriendsListView(player, pattern, items, module, normalizedState, title).open()
        }
    }

    private fun filterTitleReplacements(filterTag: String? = null): Map<String, String> {
        return mapOf(
            "%filter_tag%" to (filterTag ?: "全部好友"),
            "%filter_state%" to if (filterTag.isNullOrBlank()) "未筛选" else "标签筛选中"
        )
    }

    private fun friendListTitleReplacements(state: FriendListState): Map<String, String> {
        val normalized = state.normalized()
        val focus = when {
            !normalized.keyword.isNullOrBlank() -> "搜索: ${normalized.keyword}"
            !normalized.filterTag.isNullOrBlank() -> "标签: ${normalized.filterTag}"
            else -> normalized.sortMode.displayName
        }
        return filterTitleReplacements(normalized.filterTag) + mapOf(
            "%search_keyword%" to (normalized.keyword ?: "未搜索"),
            "%search_state%" to if (normalized.keyword.isNullOrBlank()) "未搜索" else "关键词搜索中",
            "%sort_mode%" to normalized.sortMode.displayName,
            "%friend_list_focus%" to focus
        )
    }

    private fun targetTitleReplacements(targetName: String): Map<String, String> {
        return mapOf(
            "%target_name%" to targetName,
            "%friend_name%" to targetName,
            "%raw_name%" to targetName
        )
    }

    private fun targetAndTagTitleReplacements(targetName: String, tagName: String): Map<String, String> {
        return targetTitleReplacements(targetName) + mapOf("%tag_name%" to tagName)
    }

    private fun buildLegacyInspectLines(entries: List<LegacyMigrationInspectEntry>): List<String> {
        val lines = mutableListOf(
            "§b[CyuFriends] §f旧版迁移扫描",
            "§7说明: active 表示当前模块组合下建议迁移的范围，all 表示全部支持表。"
        )
        entries.forEach { entry ->
            val tableLabel = if (entry.availableTables.isEmpty()) "未找到旧表" else entry.availableTables.joinToString(", ")
            lines += "§7${entry.scope.displayName}: §f${if (entry.available) "可导入" else "未发现"} §7| §f${entry.rowCount} §7条 §8(§f$tableLabel§8)"
        }
        return lines
    }

    private fun buildLegacyImportLines(result: LegacyMigrationResult): List<String> {
        val lines = mutableListOf(
            "§b[CyuFriends] §f旧版迁移完成",
            "§7总计: §f新增 ${result.inserted} §7条，补全 ${result.updated} §7条，跳过 ${result.skipped} §7条"
        )
        result.scopes.forEach { scope ->
            lines += "§7${scope.scope.displayName}: §f新增 ${scope.inserted} §7/ 补全 ${scope.updated} §7/ 跳过 ${scope.skipped}"
        }
        return lines
    }

    private fun showIncomingRequestsInChat(plugin: CyufriendsReload, module: FriendModule, player: Player) {
        val ownerUid = player.uid
        CyuConcurrency.scheduler.runAsync(plugin) {
            val requests = module.requestManager.getRequestEntries(ownerUid).ifEmpty {
                runBlocking { module.requestManager.getRequestsFromDbForSync(ownerUid) }
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                if (requests.isEmpty()) {
                    player.sendLang("requests-chat-empty")
                    return@runEntity
                }
                player.sendLang("requests-chat-header", mapOf("amount" to requests.size.toString()))
                requests.take(8).forEach { entry ->
                    val requesterName = CyuIdHook.getName(entry.senderUid) ?: entry.senderUid
                    FriendRichMessages.sendRequestEntry(
                        player,
                        requesterName,
                        entry.senderUid,
                        entry.createdAt,
                        FriendRequestNotes.preview(plugin, entry.note)
                    )
                }
                if (requests.size > 8) {
                    player.sendLang("requests-chat-more", mapOf("amount" to (requests.size - 8).toString()))
                }
            }
        }
    }

    private fun showSentRequestsInChat(plugin: CyufriendsReload, module: FriendModule, player: Player) {
        val ownerUid = player.uid
        CyuConcurrency.scheduler.runAsync(plugin) {
            val requests = module.requestManager.getSentRequestEntries(ownerUid).ifEmpty {
                runBlocking { module.requestManager.getSentRequestsFromDbForSync(ownerUid) }
            }
            CyuConcurrency.scheduler.runEntity(plugin, player) {
                if (requests.isEmpty()) {
                    player.sendLang("sentrequests-chat-empty")
                    return@runEntity
                }
                player.sendLang("sentrequests-chat-header", mapOf("amount" to requests.size.toString()))
                requests.take(8).forEach { entry ->
                    val targetName = CyuIdHook.getName(entry.receiverUid) ?: entry.receiverUid
                    FriendRichMessages.sendSentRequestEntry(
                        player,
                        targetName,
                        entry.receiverUid,
                        entry.createdAt,
                        FriendRequestNotes.preview(plugin, entry.note)
                    )
                }
                if (requests.size > 8) {
                    player.sendLang("sentrequests-chat-more", mapOf("amount" to (requests.size - 8).toString()))
                }
            }
        }
    }

    private fun groupTitleReplacements(groupName: String): Map<String, String> {
        return mapOf("%group_name%" to groupName)
    }

    private fun refreshOpenRequestView(player: Player) {
        when (val holder = player.openInventory.topInventory.holder) {
            is NotificationCenterView -> holder.onRender()
            is RequestsView -> holder.onRender()
            is SentRequestsView -> holder.onRender()
        }
    }

    private fun resolveFriendListSort(input: String?): FriendListSortMode? {
        return when (input?.trim()?.lowercase()) {
            null -> null
            "default" -> FriendListSortMode.RECENT
            else -> FriendListSortMode.fromId(input)
        }
    }

    private fun moduleLabel(moduleId: String): String {
        return when (moduleId) {
            "friend" -> "好友基础功能"
            "group" -> "分组功能"
            "profile" -> "资料功能"
            "chat" -> "私聊功能"
            "social" -> "社交功能"
            "proxy" -> "代理功能"
            else -> "该功能"
        }
    }

    private fun isChatListMode(value: String?): Boolean {
        return when (value?.trim()?.lowercase()) {
            "chat", "list", "text" -> true
            else -> false
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

    private fun handleAdminDebug(sender: CommandSender, action: String?, value: String?) {
        when (action?.lowercase()) {
            null, "status" -> sendDebugStatus(sender)
            "on", "enable" -> {
                val level = value?.toIntOrNull()?.coerceIn(0, 2) ?: DebugLogger.detailLevel().coerceAtLeast(1)
                DebugLogger.configureRuntime(consoleEnabled = true, detailLevel = level)
                sender.sendMessage("§b[CyuFriends] §fDebug 控制台输出已临时开启，级别 §a$level§f。")
                sendDebugStatus(sender)
            }
            "off", "disable" -> {
                DebugLogger.configureRuntime(consoleEnabled = false, fileEnabled = false)
                sender.sendMessage("§b[CyuFriends] §fDebug 输出已临时关闭。")
            }
            "level" -> {
                val level = value?.toIntOrNull()?.coerceIn(0, 2)
                if (level == null) {
                    sender.sendMessage("用法: /friend admin debug level <0|1|2>")
                    return
                }
                DebugLogger.configureRuntime(detailLevel = level)
                sender.sendMessage("§b[CyuFriends] §fDebug 级别已临时切换为 §a$level§f。")
                sendDebugStatus(sender)
            }
            "file" -> {
                when (value?.lowercase()) {
                    "on", "enable", "true" -> {
                        DebugLogger.configureRuntime(fileEnabled = true)
                        sender.sendMessage("§b[CyuFriends] §fDebug 文件输出已临时开启。")
                        sendDebugStatus(sender)
                    }
                    "off", "disable", "false" -> {
                        DebugLogger.configureRuntime(fileEnabled = false)
                        sender.sendMessage("§b[CyuFriends] §fDebug 文件输出已临时关闭。")
                        sendDebugStatus(sender)
                    }
                    else -> sender.sendMessage("用法: /friend admin debug file <on|off>")
                }
            }
            else -> sender.sendMessage("用法: /friend admin debug [status|on [0-2]|off|level <0-2>|file <on|off>]")
        }
    }

    private fun sendDebugStatus(sender: CommandSender) {
        listOf(
            "§b[CyuFriends] §fDebug 状态",
            "§7控制台输出: §f${if (DebugLogger.isConsoleEnabled()) "开启" else "关闭"}",
            "§7文件输出: §f${if (DebugLogger.isFileEnabled()) "开启" else "关闭"}",
            "§7当前级别: §f${DebugLogger.detailLevel()}",
            "§7文件位置: §f${DebugLogger.fileLocation()}",
            "§8本命令只改当前运行时；重启或完整重载后仍以 config.yml 为准。"
        ).forEach(sender::sendMessage)
    }

    private fun debugStateText(): String {
        val outputs = mutableListOf<String>()
        if (DebugLogger.isConsoleEnabled()) outputs += "控制台"
        if (DebugLogger.isFileEnabled()) outputs += "文件"
        val target = outputs.ifEmpty { listOf("关闭") }.joinToString("+")
        return "$target / Level ${DebugLogger.detailLevel()}"
    }

    private fun personalType(value: String): FriendPersonalType? {
        return when (value.lowercase()) {
            "tp", "teleport", "传送" -> FriendPersonalType.TELEPORT
            "notify", "notice", "receive", "提醒" -> FriendPersonalType.NOTIFY_RECEIVE
            "notifyme", "broadcast", "visible", "可见" -> FriendPersonalType.NOTIFY_BROADCAST
            "statuslike", "status-like", "动态点赞" -> FriendPersonalType.STATUS_LIKE_NOTICE
            "statuscomment", "status-comment", "动态评论" -> FriendPersonalType.STATUS_COMMENT_NOTICE
            "wallpost", "wall-post", "留言提醒" -> FriendPersonalType.WALL_POST_NOTICE
            "walllike", "wall-like", "留言点赞" -> FriendPersonalType.WALL_LIKE_NOTICE
            "wallcomment", "wall-comment", "留言评论" -> FriendPersonalType.WALL_COMMENT_NOTICE
            else -> null
        }
    }

    private fun personalState(preferences: FriendPersonalPreferences, type: FriendPersonalType): FriendPersonalState {
        return when (type) {
            FriendPersonalType.TELEPORT -> preferences.teleport
            FriendPersonalType.NOTIFY_RECEIVE -> preferences.notifyReceive
            FriendPersonalType.NOTIFY_BROADCAST -> preferences.notifyBroadcast
            FriendPersonalType.STATUS_LIKE_NOTICE -> preferences.statusLikeNotice
            FriendPersonalType.STATUS_COMMENT_NOTICE -> preferences.statusCommentNotice
            FriendPersonalType.WALL_POST_NOTICE -> preferences.wallPostNotice
            FriendPersonalType.WALL_LIKE_NOTICE -> preferences.wallLikeNotice
            FriendPersonalType.WALL_COMMENT_NOTICE -> preferences.wallCommentNotice
        }
    }

    private fun parseGroupRuleKey(value: String): String? {
        return when (value.lowercase()) {
            "tp", "teleport", "传送" -> "tp"
            "notify", "notice", "receive", "提醒" -> "notify"
            "notifyme", "broadcast", "visible", "可见" -> "notifyme"
            "pin", "pinned", "top", "置顶" -> "pin"
            else -> null
        }
    }

    private fun groupRuleType(rule: String): FriendPersonalType? {
        return when (rule) {
            "tp" -> FriendPersonalType.TELEPORT
            "notify" -> FriendPersonalType.NOTIFY_RECEIVE
            "notifyme" -> FriendPersonalType.NOTIFY_BROADCAST
            else -> null
        }
    }

    private fun parseGroupRuleState(type: FriendPersonalType, value: String): FriendPersonalState? {
        return when (value.lowercase()) {
            "default", "inherit", "继承", "继承全局" -> FriendPersonalState.DEFAULT
            "allow", "on", "yes", "允许", "开启" -> FriendPersonalState.ALLOW
            "deny", "off", "no", "拒绝", "关闭" -> FriendPersonalState.DENY
            "confirm", "ask", "需要确认", "确认" -> if (type == FriendPersonalType.TELEPORT) FriendPersonalState.CONFIRM else null
            else -> null
        }
    }

    private fun parsePinnedState(value: String): Boolean? {
        return when (value.lowercase()) {
            "on", "true", "yes", "allow", "开启", "置顶" -> true
            "off", "false", "no", "deny", "关闭", "普通" -> false
            else -> null
        }
    }

    private fun groupRuleStateOptions(type: FriendPersonalType): String {
        return when (type) {
            FriendPersonalType.TELEPORT -> "default / allow / confirm / deny"
            FriendPersonalType.NOTIFY_RECEIVE,
            FriendPersonalType.NOTIFY_BROADCAST -> "default / allow / deny"
            else -> "default / allow / deny"
        }
    }

    private fun groupRuleLabel(rule: String): String {
        return when (rule) {
            "tp" -> "分组传送规则"
            "notify" -> "分组接收提醒"
            "notifyme" -> "分组广播可见"
            "pin" -> "分组置顶显示"
            else -> rule
        }
    }

    private fun groupRuleStateName(settings: FriendGroupPreferences, type: FriendPersonalType): String {
        return when (type) {
            FriendPersonalType.TELEPORT -> settings.teleport.displayName(type)
            FriendPersonalType.NOTIFY_RECEIVE -> settings.notifyReceive.displayName(type)
            FriendPersonalType.NOTIFY_BROADCAST -> settings.notifyBroadcast.displayName(type)
            else -> FriendPersonalState.DEFAULT.displayName(type)
        }
    }

    private fun groupPinStateName(pinned: Boolean): String {
        return if (pinned) "置顶显示" else "普通显示"
    }

    private fun sendGroupRuleSummary(player: Player, module: FriendModule, groupName: String) {
        val settings = module.preferencesManager.snapshotGroupStoredSync(player.uid, groupName)
        player.sendLang("group-rule-summary-header", mapOf("group" to groupName))
        player.sendLang("group-rule-summary-entry", mapOf("rule" to "分组传送规则", "state" to settings.teleport.displayName(FriendPersonalType.TELEPORT)))
        player.sendLang("group-rule-summary-entry", mapOf("rule" to "分组接收提醒", "state" to settings.notifyReceive.displayName(FriendPersonalType.NOTIFY_RECEIVE)))
        player.sendLang("group-rule-summary-entry", mapOf("rule" to "分组广播可见", "state" to settings.notifyBroadcast.displayName(FriendPersonalType.NOTIFY_BROADCAST)))
        player.sendLang("group-rule-summary-entry", mapOf("rule" to "分组置顶显示", "state" to groupPinStateName(settings.pinned)))
    }

    private data class GroupMoveAllArgs(val sourceGroup: String, val targetGroup: String?)

    private fun parseGroupMoveAllArgs(arguments: List<String>): GroupMoveAllArgs? {
        val cleanArgs = arguments.map(String::trim).filter(String::isNotEmpty)
        if (cleanArgs.isEmpty()) return null

        val separatorIndex = cleanArgs.indexOf("--")
        if (separatorIndex >= 0) {
            val source = cleanArgs.take(separatorIndex).joinToString(" ").trim()
            if (source.isBlank()) return null
            val target = cleanArgs.drop(separatorIndex + 1).joinToString(" ").trim().takeIf(String::isNotBlank)
            return GroupMoveAllArgs(source, target)
        }

        if (cleanArgs.size == 1) {
            return GroupMoveAllArgs(cleanArgs.first(), null)
        }

        return GroupMoveAllArgs(
            sourceGroup = cleanArgs.first(),
            targetGroup = cleanArgs.drop(1).joinToString(" ").trim().takeIf(String::isNotBlank)
        )
    }

    private fun openLiteralGroupRulesGui(
        plugin: CyufriendsReload,
        player: Player,
        groupModule: GroupModule,
        friendModule: FriendModule,
        rawGroupName: String?
    ): Boolean {
        val groupName = rawGroupName?.trim()?.takeIf(String::isNotBlank) ?: return false
        val existingGroup = groupModule.manager.groups(player.uid)
            .firstOrNull { it.equals(groupName, ignoreCase = true) }
            ?: return false
        openGroupRulesGui(plugin, player, groupModule, friendModule, existingGroup)
        return true
    }

    private fun openGroupRulesGui(
        plugin: CyufriendsReload,
        player: Player,
        groupModule: GroupModule,
        friendModule: FriendModule,
        groupName: String
    ) {
        openGui(player, plugin, "group_rules.yml", ViewTitles.groupRules(groupName), groupTitleReplacements(groupName)) { pattern, items, title ->
            GroupRulesView(player, pattern, items, groupModule, friendModule, groupName, title).open()
        }
    }

    private fun teleportModeMessageKey(mode: FriendTeleportMode): String {
        return when (mode) {
            FriendTeleportMode.DIRECT -> "tp-mode-direct"
            FriendTeleportMode.CONFIRM -> "tp-mode-confirm"
            FriendTeleportMode.DENY -> "tp-mode-deny"
        }
    }

    private fun teleportModeSoundKey(mode: FriendTeleportMode): String {
        return when (mode) {
            FriendTeleportMode.DIRECT -> "tp-toggle-enabled"
            FriendTeleportMode.CONFIRM -> "tp-toggle-confirm"
            FriendTeleportMode.DENY -> "tp-toggle-disabled"
        }
    }

    private fun executeLocalTeleport(plugin: CyufriendsReload, requester: Player, targetUid: String) {
        val target = CyuIdHook.getOnlinePlayer(targetUid)
        if (target == null || !target.isOnline) {
            CyuConcurrency.scheduler.runEntity(plugin, requester) {
                requester.sendLang("player-offline")
            }
            return
        }

        CyuConcurrency.scheduler.runEntity(plugin, target) {
            val targetName = target.name
            val worldName = target.world.name
            val location = target.location.clone()
            CyuConcurrency.scheduler.runEntity(plugin, requester) {
                if (isTeleportDisabled(plugin, worldName)) {
                    requester.sendLang("tp-world-disabled")
                    return@runEntity
                }
                requester.teleportAsync(location).thenAccept { success ->
                    if (success) {
                        requester.sendLang("tp-success", mapOf("target" to targetName))
                    } else {
                        requester.sendLang("tp-failed")
                    }
                }
            }
        }
    }

    private fun normalizeTag(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.take(FriendDefaults.MAX_TAG_LENGTH)
    }

    private fun parseTagColorArgs(arguments: List<String>): Pair<String, String>? {
        if (arguments.size < 3) return null
        val rest = arguments.drop(1).map { it.trim() }.filter { it.isNotBlank() }
        if (rest.size < 2) return null

        normalizeTagColor(rest.first())?.let { color ->
            val tag = normalizeTag(rest.drop(1).joinToString(" ")) ?: return@let
            return tag to color
        }

        normalizeTagColor(rest.last())?.let { color ->
            val tag = normalizeTag(rest.dropLast(1).joinToString(" ")) ?: return@let
            return tag to color
        }

        return null
    }

    private fun normalizeTagColor(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val hex = Regex("^#([0-9a-fA-F]{6})$")
        if (hex.matches(trimmed)) {
            return trimmed.uppercase()
        }
        return when (trimmed.lowercase()) {
            "aqua", "blue", "cyan" -> "#5EC8FF"
            "teal", "mint" -> "#7ED7C1"
            "yellow", "gold" -> "#F7C948"
            "orange", "coral" -> "#F78C6B"
            "purple", "violet" -> "#C792EA"
            "pink", "rose" -> "#FF7DAF"
            "green", "lime" -> "#8BD450"
            "gray", "grey", "slate" -> "#9FB3C8"
            "white" -> "#F4FBFF"
            else -> null
        }
    }

    private fun normalizeOptionalNoteName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.take(FriendDefaults.MAX_NOTE_NAME_LENGTH)
    }

    private fun normalizeOptionalNoteDetail(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.take(FriendDefaults.MAX_NOTE_DETAIL_LENGTH)
    }

    private fun formatAdminTime(timestamp: Long): String {
        if (timestamp <= 0L) return "暂无记录"
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(adminTimeFormatter)
    }

    private fun buildModerationOverview(plugin: CyufriendsReload, socialModule: SocialModule): List<String> {
        val manager = socialModule.manager
        val pendingWalls = manager.globalPendingWallCountSync()
        val pendingReplies = manager.globalPendingReplyCountSync()
        val recentWalls = manager.recentPendingWallsSync(5)
        val recentReplies = manager.recentPendingRepliesSync(5)
        val recentAudit = socialModule.auditLogger.recent(6)
        val moderationEnabled = plugin.config.getBoolean("wallModeration.enabled", false)
        val commentModerationEnabled = plugin.config.getBoolean("wallModeration.comment-enabled", moderationEnabled)

        val lines = mutableListOf(
            "§b[CyuFriends] §f审核总览",
            "§7留言审核: §f${if (moderationEnabled) "已启用" else "未启用"}  §7评论审核: §f${if (commentModerationEnabled) "已启用" else "未启用"}",
            "§7全局待审留言: §f$pendingWalls  §7全局待审评论: §f$pendingReplies",
            "§7审计日志: §f${socialModule.auditLogger.configuredPath()}"
        )

        if (recentWalls.isEmpty()) {
            lines.add("§7最近待审留言: §8无")
        } else {
            lines.add("§7最近待审留言:")
            lines.addAll(recentWalls.map { entry ->
                val ownerName = CyuIdHook.getName(entry.ownerUid) ?: entry.ownerUid
                val authorName = CyuIdHook.getName(entry.authorUid) ?: entry.authorUid
                "§8- §f#${entry.id} §7墙主: §b$ownerName §7留言者: §a$authorName §7[${formatAdminTime(entry.timestamp)}]"
            })
        }

        if (recentReplies.isEmpty()) {
            lines.add("§7最近待审评论: §8无")
        } else {
            lines.add("§7最近待审评论:")
            lines.addAll(recentReplies.map { entry ->
                val ownerName = CyuIdHook.getName(entry.ownerUid) ?: entry.ownerUid
                val authorName = CyuIdHook.getName(entry.authorUid) ?: entry.authorUid
                "§8- §f#${entry.id} §7留言: #${entry.wallId} §7墙主: §b$ownerName §7评论者: §a$authorName §7[${formatAdminTime(entry.timestamp)}]"
            })
        }

        if (recentAudit.isEmpty()) {
            lines.add("§7最近审核记录: §8无")
        } else {
            lines.add("§7最近审核记录:")
            lines.addAll(recentAudit.map { "§8- §f$it" })
        }

        return lines
    }

    private fun buildModerationOwnerOverview(socialModule: SocialModule, targetUid: String, targetInput: String): List<String> {
        val manager = socialModule.manager
        val displayName = CyuIdHook.getName(targetUid) ?: targetInput
        val pendingWalls = manager.pendingWallCountSync(targetUid)
        val pendingReplies = manager.pendingWallReplyCountSync(targetUid)
        val recentWalls = manager.getPendingWallEntriesSync(targetUid).take(5)
        val recentReplies = manager.recentPendingRepliesSync(targetUid, 5)

        val lines = mutableListOf(
            "§b[CyuFriends] §f审核详情: §a$displayName",
            "§7UID: §f$targetUid",
            "§7待审留言: §f$pendingWalls  §7待审评论: §f$pendingReplies",
            "§7审计日志: §f${socialModule.auditLogger.configuredPath()}"
        )

        if (recentWalls.isEmpty()) {
            lines.add("§7最近待审留言: §8无")
        } else {
            lines.add("§7最近待审留言:")
            lines.addAll(recentWalls.map { entry ->
                val authorName = CyuIdHook.getName(entry.authorUid) ?: entry.authorUid
                "§8- §f#${entry.id} §7留言者: §a$authorName §7[${formatAdminTime(entry.timestamp)}]"
            })
        }

        if (recentReplies.isEmpty()) {
            lines.add("§7最近待审评论: §8无")
        } else {
            lines.add("§7最近待审评论:")
            lines.addAll(recentReplies.map { entry ->
                val authorName = CyuIdHook.getName(entry.authorUid) ?: entry.authorUid
                "§8- §f#${entry.id} §7留言: #${entry.wallId} §7评论者: §a$authorName §7[${formatAdminTime(entry.timestamp)}]"
            })
        }

        return lines
    }

    private fun resolveProfileTarget(value: String): String? {
        return CyuIdHook.getUidByName(value)
            ?: value.takeIf { CyuIdHook.getName(it) != null }
    }

    private fun resolveFriendUid(module: FriendModule, ownerUid: String, value: String): String? {
        val input = value.trim()
        if (input.isEmpty()) return null
        return CyuIdHook.getUidByName(input)
            ?: input.takeIf { module.friendManager.isFriendStable(ownerUid, it) }
    }

    private fun buildBirthdaySummary(uid: String, friendModule: FriendModule, profileModule: ProfileModule): List<String> {
        val friends = friendModule.friendManager.getFriendEntriesStoredSync(uid).mapTo(linkedSetOf(), org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData::friendUid)
        val offsets = profileModule.manager.birthdayReminderOffsets()
        val lines = mutableListOf<String>()
        lines += "§b[CyuFriends] §f近期生日提醒"
        var total = 0
        offsets.forEach { offset ->
            val birthdayUids = profileModule.manager.getBirthdaysAfterSync(offset.toLong())
                .filter { it in friends }
            if (birthdayUids.isEmpty()) return@forEach
            total += birthdayUids.size
            val names = birthdayUids.take(5).map { CyuIdHook.getName(it) ?: it }.joinToString("、")
            val label = if (offset <= 0) "今天生日" else "$offset 天后生日"
            lines += "§7$label: §f$names${if (birthdayUids.size > 5) " §7等 ${birthdayUids.size} 人" else ""}"
        }
        if (total == 0) {
            lines += "§7你的好友近期没有生日提醒。"
        }
        return lines
    }

    private fun openProfileHome(plugin: CyufriendsReload, profileModule: ProfileModule, player: Player) {
        openGui(player, plugin, "profile_home.yml", ViewTitles.profileHome(player.name)) { pattern, items, title ->
            ProfileHomeView(player, pattern, items, plugin, profileModule, title).open()
        }
    }

    private fun openAddFriendGui(plugin: CyufriendsReload, player: Player, targetName: String) {
        openGui(player, plugin, "add_friend.yml", ViewTitles.addFriend(targetName), targetTitleReplacements(targetName)) { pattern, items, title ->
            AddFriendView(player, pattern, items, targetName, title).open()
        }
    }

    private fun openFriendProfile(
        plugin: CyufriendsReload,
        player: Player,
        module: FriendModule,
        targetUid: String,
        targetName: String,
        detailed: Boolean = false
    ) {
        val guiFile = if (detailed) "friend_profile_details.yml" else "friend_profile.yml"
        val title = if (detailed) ViewTitles.friendProfileDetails(targetName) else ViewTitles.friendProfile(targetName)
        openGui(player, plugin, guiFile, title, targetTitleReplacements(targetName)) { pattern, items, viewTitle ->
            FriendProfileView(player, pattern, items, module, targetUid, targetName, viewTitle).open()
        }
    }

    private fun proxyGateway(plugin: CyufriendsReload): ProxyGateway? {
        return plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
    }

    private fun resolveRequestSenderUid(module: FriendModule, input: String, receiverUid: String): String? {
        val resolved = CyuIdHook.getUidByName(input)
        if (resolved != null) return resolved
        return input.trim().takeIf { it.isNotEmpty() && module.requestManager.hasRequestStable(it, receiverUid) }
    }

    private fun resolveRequestReceiverUid(module: FriendModule, senderUid: String, input: String): String? {
        val resolved = CyuIdHook.getUidByName(input)
        if (resolved != null) return resolved
        return input.trim().takeIf { it.isNotEmpty() && module.requestManager.hasRequestStable(senderUid, it) }
    }

    private fun permissionInt(plugin: CyufriendsReload, player: org.bukkit.entity.Player, sectionName: String, key: String, prefix: String, fallback: Int): Int {
        val section = plugin.config.getConfigurationSection(sectionName) ?: return fallback
        var value = section.getConfigurationSection("default")?.getInt(key, fallback) ?: fallback
        section.getKeys(false).forEach { group ->
            if (group != "default" && player.hasPermission(prefix + group)) {
                value = maxOf(value, section.getConfigurationSection(group)?.getInt(key, value) ?: value)
            }
        }
        return value.coerceAtLeast(0)
    }

    private fun permissionLong(plugin: CyufriendsReload, player: org.bukkit.entity.Player, sectionName: String, key: String, prefix: String, fallback: Long): Long {
        val section = plugin.config.getConfigurationSection(sectionName) ?: return fallback
        var value = section.getConfigurationSection("default")?.getLong(key, fallback) ?: fallback
        section.getKeys(false).forEach { group ->
            if (group != "default" && player.hasPermission(prefix + group)) {
                value = minOf(value, section.getConfigurationSection(group)?.getLong(key, value) ?: value)
            }
        }
        return value.coerceAtLeast(0L)
    }
}
