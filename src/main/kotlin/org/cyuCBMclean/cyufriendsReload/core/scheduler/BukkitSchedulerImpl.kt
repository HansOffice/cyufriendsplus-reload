package org.cyuCBMclean.cyufriendsReload.core.scheduler

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

class BukkitSchedulerImpl : CyuScheduler {

    private fun wrap(task: BukkitTask) = object : CyuTask {
        override fun cancel() = task.cancel()
    }

    override fun runAsync(plugin: Plugin, task: () -> Unit): CyuTask =
        wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, task))

    override fun runLaterAsync(plugin: Plugin, delayTicks: Long, task: () -> Unit): CyuTask =
        wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks))

    override fun runTimerAsync(plugin: Plugin, delayTicks: Long, periodTicks: Long, task: () -> Unit): CyuTask =
        wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks))

    override fun runGlobal(plugin: Plugin, task: () -> Unit): CyuTask =
        wrap(Bukkit.getScheduler().runTask(plugin, task))

    override fun runRegion(plugin: Plugin, location: Location, task: () -> Unit): CyuTask =
        wrap(Bukkit.getScheduler().runTask(plugin, task))

    override fun runEntity(plugin: Plugin, entity: Entity, task: () -> Unit): CyuTask =
        wrap(Bukkit.getScheduler().runTask(plugin, task))

    override fun cancelAll(plugin: Plugin) {
        Bukkit.getScheduler().cancelTasks(plugin)
    }
}