package org.cyuCBMclean.cyufriendsReload.modules.chat

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
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.chat.listener.ChatEventListener
import org.cyuCBMclean.cyufriendsReload.ui.input.PendingTextInput

class ChatModule(
    val plugin: CyufriendsReload,
    private val dbManager: DatabaseManager
) : CyuModule, Listener {

    override val moduleId = "chat"
    override val requiredModules = setOf("friend")

    lateinit var repository: ChatRepository
        private set
    lateinit var manager: ChatManager
        private set
    private var cleanupTask: CyuTask? = null

    override fun onEnable() {
        repository = ChatRepository(dbManager)
        manager = ChatManager(plugin, repository)

        runBlocking { repository.createTable(dbManager) }

        cleanupTask = CyuConcurrency.scheduler.runTimerAsync(plugin, 20L * 120, 20L * 3600) {
            val expiration = plugin.config.getLong("messageExpiration", plugin.config.getLong("chat.messageExpiration", 604800L))
            if (expiration <= 0L) return@runTimerAsync
            runCatching {
                manager.clearExpiredUnreadSync(System.currentTimeMillis() - expiration * 1000L)
            }.onFailure { exception ->
                if (plugin.isEnabled) {
                    plugin.logger.warning("聊天未读过期清理失败: ${exception.message}")
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(this, plugin)
        Bukkit.getPluginManager().registerEvents(ChatEventListener(plugin), plugin)

    }

    override fun onDisable() {
        cleanupTask?.cancel()
        cleanupTask = null
        PendingTextInput.clearAll()
    }

    override fun reloadConfig() {}

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uid = player.uid

        CyuConcurrency.scheduler.runLaterAsync(plugin, 40L) {
            val unread = manager.getUnreadSync(uid)
            if (unread.isNotEmpty()) {
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    player.sendLang("unread-messages", mapOf("amount" to unread.size.toString()))
                    player.playAudio("unread-alert")
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        manager.clearTarget(event.player.uid)
        PendingTextInput.clear(event.player.uniqueId)
    }
}
