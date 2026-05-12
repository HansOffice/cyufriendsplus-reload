package org.cyuCBMclean.cyufriendsReload.modules.profile

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
import org.cyuCBMclean.cyufriendsReload.extension.uid

class ProfileModule(
    val plugin: CyufriendsReload,
    private val dbManager: DatabaseManager
) : CyuModule, Listener {

    override val moduleId = "profile"
    override val requiredModules = setOf("friend")

    lateinit var repository: ProfileRepository
        private set
    lateinit var manager: ProfileManager
        private set
    private lateinit var birthdayReminderTask: BirthdayReminderTask

    override fun onEnable() {
        repository = ProfileRepository(dbManager)
        manager = ProfileManager(plugin, repository)
        birthdayReminderTask = BirthdayReminderTask(plugin, this)

        runBlocking { repository.createTable(dbManager) }

        Bukkit.getPluginManager().registerEvents(this, plugin)
        ProfileCommands.register(plugin, this)
        birthdayReminderTask.start()
    }

    override fun onDisable() {
        if (::birthdayReminderTask.isInitialized) birthdayReminderTask.stop()
    }

    override fun reloadConfig() {
        if (::birthdayReminderTask.isInitialized) birthdayReminderTask.start()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uid = player.uid
        val worldName = player.world.name
        CyuConcurrency.scheduler.runAsync(plugin) {
            runBlocking {
                manager.loadProfile(uid)
                birthdayReminderTask.remindOnJoin(player, uid, worldName)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        manager.unloadProfile(event.player.uid)
    }
}
