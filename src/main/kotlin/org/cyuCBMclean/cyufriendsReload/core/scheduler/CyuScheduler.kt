package org.cyuCBMclean.cyufriendsReload.core.scheduler

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

interface CyuTask {
    fun cancel()
}

interface CyuScheduler {
    fun runAsync(plugin: Plugin, task: () -> Unit): CyuTask
    fun runLaterAsync(plugin: Plugin, delayTicks: Long, task: () -> Unit): CyuTask
    fun runTimerAsync(plugin: Plugin, delayTicks: Long, periodTicks: Long, task: () -> Unit): CyuTask
    fun runGlobal(plugin: Plugin, task: () -> Unit): CyuTask
    fun runRegion(plugin: Plugin, location: Location, task: () -> Unit): CyuTask
    fun runEntity(plugin: Plugin, entity: Entity, task: () -> Unit): CyuTask
    fun cancelAll(plugin: Plugin)
}

/**
 * 统一调度入口，Paper 和 Folia 的差异别往业务里散
 */
object CyuConcurrency {
    val isFolia: Boolean = runCatching {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
    }.isSuccess

    val scheduler: CyuScheduler by lazy {
        if (isFolia) FoliaSchedulerImpl() else BukkitSchedulerImpl()
    }
}
