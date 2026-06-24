package org.cyuCBMclean.cyufriendsReload.core.scheduler

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit

object PlatformSchedulerFactory {
    val isFolia: Boolean = true

    fun create(): CyuScheduler = FoliaSchedulerImpl()
}

private class FoliaSchedulerImpl : CyuScheduler {

    private fun wrap(task: ScheduledTask?) = object : CyuTask {
        override fun cancel() {
            task?.cancel()
        }
    }

    override fun runAsync(plugin: Plugin, task: () -> Unit): CyuTask =
        wrap(Bukkit.getAsyncScheduler().runNow(plugin) { task() })

    override fun runLaterAsync(plugin: Plugin, delayTicks: Long, task: () -> Unit): CyuTask =
        wrap(Bukkit.getAsyncScheduler().runDelayed(plugin, { task() }, delayTicks * 50L, TimeUnit.MILLISECONDS))

    override fun runTimerAsync(plugin: Plugin, delayTicks: Long, periodTicks: Long, task: () -> Unit): CyuTask =
        wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, { task() }, delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS))

    override fun runGlobal(plugin: Plugin, task: () -> Unit): CyuTask =
        wrap(Bukkit.getGlobalRegionScheduler().run(plugin) { task() })

    override fun runRegion(plugin: Plugin, location: Location, task: () -> Unit): CyuTask =
        wrap(Bukkit.getRegionScheduler().run(plugin, location) { task() })

    override fun runEntity(plugin: Plugin, entity: Entity, task: () -> Unit): CyuTask =
        wrap(entity.scheduler.run(plugin, { task() }, null))

    override fun cancelAll(plugin: Plugin) {
        Bukkit.getAsyncScheduler().cancelTasks(plugin)
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin)
    }
}
