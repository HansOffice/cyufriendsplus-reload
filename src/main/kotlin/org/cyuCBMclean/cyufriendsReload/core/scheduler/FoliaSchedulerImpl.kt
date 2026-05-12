package org.cyuCBMclean.cyufriendsReload.core.scheduler

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class FoliaSchedulerImpl : CyuScheduler {

    private val asyncScheduler by lazy { Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null) }
    private val globalRegionScheduler by lazy { Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null) }
    private val regionScheduler by lazy { Bukkit::class.java.getMethod("getRegionScheduler").invoke(null) }

    private val runNowMethod by lazy { asyncScheduler::class.java.getMethod("runNow", Plugin::class.java, Consumer::class.java) }
    private val runDelayedMethod by lazy { asyncScheduler::class.java.getMethod("runDelayed", Plugin::class.java, Consumer::class.java, Long::class.javaPrimitiveType, TimeUnit::class.java) }
    private val runAtFixedRateMethod by lazy { asyncScheduler::class.java.getMethod("runAtFixedRate", Plugin::class.java, Consumer::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, TimeUnit::class.java) }
    private val globalRunMethod by lazy { globalRegionScheduler::class.java.getMethod("run", Plugin::class.java, Consumer::class.java) }
    private val regionRunMethod by lazy { regionScheduler::class.java.getMethod("run", Plugin::class.java, Location::class.java, Consumer::class.java) }
    private val cancelAsyncMethod by lazy { asyncScheduler::class.java.getMethod("cancelTasks", Plugin::class.java) }
    private val cancelGlobalMethod by lazy { globalRegionScheduler::class.java.getMethod("cancelTasks", Plugin::class.java) }

    private fun wrap(task: Any): CyuTask = object : CyuTask {
        override fun cancel() {
            task::class.java.getMethod("cancel").invoke(task)
        }
    }

    override fun runAsync(plugin: Plugin, task: () -> Unit): CyuTask =
        wrap(runNowMethod.invoke(asyncScheduler, plugin, Consumer<Any> { task() }))

    override fun runLaterAsync(plugin: Plugin, delayTicks: Long, task: () -> Unit): CyuTask =
        wrap(runDelayedMethod.invoke(asyncScheduler, plugin, Consumer<Any> { task() }, delayTicks * 50L, TimeUnit.MILLISECONDS))

    override fun runTimerAsync(plugin: Plugin, delayTicks: Long, periodTicks: Long, task: () -> Unit): CyuTask =
        wrap(runAtFixedRateMethod.invoke(asyncScheduler, plugin, Consumer<Any> { task() }, delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS))

    override fun runGlobal(plugin: Plugin, task: () -> Unit): CyuTask =
        wrap(globalRunMethod.invoke(globalRegionScheduler, plugin, Consumer<Any> { task() }))

    override fun runRegion(plugin: Plugin, location: Location, task: () -> Unit): CyuTask =
        wrap(regionRunMethod.invoke(regionScheduler, plugin, location, Consumer<Any> { task() }))

    override fun runEntity(plugin: Plugin, entity: Entity, task: () -> Unit): CyuTask {
        val entityScheduler = entity::class.java.getMethod("getScheduler").invoke(entity)
        val method = entityScheduler::class.java.getMethod("run", Plugin::class.java, Consumer::class.java, Runnable::class.java)
        return wrap(method.invoke(entityScheduler, plugin, Consumer<Any> { task() }, null))
    }

    override fun cancelAll(plugin: Plugin) {
        cancelAsyncMethod.invoke(asyncScheduler, plugin)
        cancelGlobalMethod.invoke(globalRegionScheduler, plugin)
    }
}