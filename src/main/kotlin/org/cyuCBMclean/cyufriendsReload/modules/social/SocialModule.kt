package org.cyuCBMclean.cyufriendsReload.modules.social

import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.module.CyuModule
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.uid

class SocialModule(
    val plugin: CyufriendsReload,
    private val dbManager: DatabaseManager
) : CyuModule, Listener {

    override val moduleId = "social"
    override val requiredModules = setOf("friend")

    lateinit var statusRepo: StatusRepository
        private set
    lateinit var wallRepo: WallRepository
        private set
    lateinit var seenRepo: SocialSeenRepository
        private set
    lateinit var auditLogger: WallModerationAuditLogger
        private set
    lateinit var manager: SocialManager
        private set

    override fun onEnable() {
        statusRepo = StatusRepository(dbManager)
        wallRepo = WallRepository(dbManager)
        seenRepo = SocialSeenRepository(dbManager)
        auditLogger = WallModerationAuditLogger(plugin)
        manager = SocialManager(plugin, statusRepo, wallRepo, seenRepo)

        runBlocking {
            statusRepo.createTable(dbManager)
            wallRepo.createTable(dbManager)
            seenRepo.createTable(dbManager)
        }

        Bukkit.getPluginManager().registerEvents(this, plugin)
        SocialCommands.register(plugin, this)
    }

    override fun onDisable() {
    }

    override fun reloadConfig() {}

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        manager.invalidateWallCache(event.player.uid)
    }
}
