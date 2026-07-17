package org.cyuCBMclean.cyufriendsReload.modules.friend

import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.module.CyuModule
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuTask
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.remotePresence
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.listener.FriendInteractListener
import org.cyuCBMclean.cyufriendsReload.modules.friend.listener.PublicChatFilterListener
import java.util.concurrent.CompletableFuture

class FriendModule(
    val plugin: CyufriendsReload,
    private val dbManager: DatabaseManager
) : CyuModule, Listener {

    override val moduleId = "friend"

    lateinit var friendManager: FriendManager
        private set
    lateinit var requestManager: RequestManager
        private set
    lateinit var blockManager: BlockManager
        private set
    lateinit var teleportManager: TeleportManager
        private set
    lateinit var preferencesManager: FriendPreferencesManager
        private set
    lateinit var timelineManager: RelationshipTimelineManager
        private set
    lateinit var legacyMigrationAssistant: LegacyDataMigrationAssistant
        private set
    private var requestCleanupTask: CyuTask? = null

    override fun onEnable() {
        val friendRepo = FriendRepository(dbManager)
        val requestRepo = RequestRepository(dbManager)
        val blockRepo = BlockRepository(dbManager)
        val preferencesRepo = FriendPreferencesRepository(dbManager)
        val timelineRepo = RelationshipTimelineRepository(dbManager)

        friendManager = FriendManager(friendRepo)
        requestManager = RequestManager(requestRepo)
        blockManager = BlockManager(blockRepo)
        teleportManager = TeleportManager(plugin)
        preferencesManager = FriendPreferencesManager(preferencesRepo, friendManager)
        timelineManager = RelationshipTimelineManager(plugin, timelineRepo)
        legacyMigrationAssistant = LegacyDataMigrationAssistant(plugin, dbManager)

        runBlocking {
            friendRepo.createTable(dbManager)
            requestRepo.createTable(dbManager)
            blockRepo.createTable(dbManager)
            preferencesRepo.createTable(dbManager)
            timelineRepo.createTable(dbManager)
        }

        requestCleanupTask = CyuConcurrency.scheduler.runTimerAsync(plugin, 20L * 60, 20L * 3600) {
            runBlocking {
                val threshold = System.currentTimeMillis() - (7L * 24 * 3600 * 1000)
                runCatching { requestManager.clearExpiredCache(threshold) }
            }
        }

        Bukkit.getPluginManager().registerEvents(this, plugin)
        Bukkit.getPluginManager().registerEvents(FriendInteractListener(plugin, this), plugin)
        Bukkit.getPluginManager().registerEvents(PublicChatFilterListener(plugin, this), plugin)

    }

    override fun onDisable() {
        requestCleanupTask?.cancel()
        requestCleanupTask = null
        FriendListStateStore.clearAll()
    }

    override fun reloadConfig() {}

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uid = CyuIdHook.getUid(player)
        val name = player.name
        CyuConcurrency.scheduler.runAsync(plugin) {
            runBlocking {
                runCatching {
                    friendManager.loadPlayer(uid)
                    requestManager.loadPlayer(uid)
                    blockManager.loadPlayer(uid)
                    preferencesManager.loadPlayer(uid)
                    remindPendingRequests(player, uid)
                    sendJoinSummary(player, uid)
                    notifyOnlineState(uid, name, true)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val uid = CyuIdHook.getUid(event.player)
        val name = event.player.name
        CyuConcurrency.scheduler.runAsync(plugin) {
            runBlocking {
                runCatching {
                    preferencesManager.recordLastOnline(uid, System.currentTimeMillis())
                    notifyOnlineState(uid, name, false)
                }
            }
            friendManager.unloadPlayer(uid)
            requestManager.unloadPlayer(uid)
            blockManager.unloadPlayer(uid)
            preferencesManager.unloadPlayer(uid)
            FriendListStateStore.clear(uid)
        }
        teleportManager.clearRequest(uid)
    }

    private suspend fun remindPendingRequests(player: org.bukkit.entity.Player, uid: String) {
        val amount = requestManager.getRequestsFromDbForSync(uid).size
        if (amount <= 0) return
        CyuConcurrency.scheduler.runEntity(plugin, player) {
            player.sendLang("friend-join-pending-requests", mapOf("amount" to amount.toString()))
        }
    }

    private suspend fun sendJoinSummary(player: org.bukkit.entity.Player, uid: String) {
        if (!plugin.config.getBoolean("joinChat.enabled", true)) return
        val lines = plugin.config.getStringList("joinChat.lines")
        if (lines.isEmpty()) return

        val friendUids = friendManager.getFriends(uid)
        val unreadCount = plugin.moduleManager.getModule<ChatModule>("chat")
            ?.manager
            ?.getUnread(uid)
            ?.size
            ?: 0
        val onlineCount = onlineCount(friendUids)

        CyuConcurrency.scheduler.runEntity(plugin, player) {
            lines.forEach { line ->
                plugin.langEngine.sendRaw(
                    player,
                    line.replace("{onlineCount}", onlineCount.toString())
                        .replace("{unreadCount}", unreadCount.toString())
                )
            }
        }
    }

    private fun onlineCount(friendUids: Set<String>): Int {
        if (friendUids.isEmpty()) return 0
        val future = CompletableFuture<Int>()
        CyuConcurrency.scheduler.runGlobal(plugin) {
            val localOnline = Bukkit.getOnlinePlayers().mapTo(hashSetOf<String>()) { CyuIdHook.getUid(it) }
            future.complete(friendUids.count { it in localOnline || plugin.remotePresence(it) != null })
        }
        return future.join()
    }

    suspend fun notifyOnlineState(uid: String, playerName: String, online: Boolean) {
        val recipients = friendManager.getFriends(uid)
            .filter {
                preferencesManager.canBroadcastJoinNoticeTo(uid, it) &&
                    preferencesManager.canReceiveJoinNoticeFrom(it, uid)
            }
            .toSet()
        if (recipients.isEmpty()) return

        val message = if (online) "friend-online-notice" else "friend-offline-notice"
        val sound = if (online) "friend-online" else "friend-offline"

        CyuConcurrency.scheduler.runGlobal(plugin) {
            Bukkit.getOnlinePlayers()
                .filter { CyuIdHook.getUid(it) in recipients }
                .forEach { target ->
                    CyuConcurrency.scheduler.runEntity(plugin, target) {
                        target.sendLang(message, mapOf("player" to playerName))
                        target.playAudio(sound)
                    }
                }
        }
    }
}
