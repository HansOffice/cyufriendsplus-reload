package org.cyuCBMclean.cyufriendsReload.core.module

import org.bukkit.plugin.Plugin
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level

class ModuleManager(private val plugin: Plugin) {

    private val modules = CopyOnWriteArrayList<CyuModule>()
    private val enabledModules = ConcurrentHashMap.newKeySet<String>()

    fun register(module: CyuModule) {
        if (modules.none { it.moduleId == module.moduleId }) {
            modules.add(module)
            DebugLogger.debug(2, "已注册模块: ${module.moduleId}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : CyuModule> getModule(id: String): T? {
        return modules.find { it.moduleId == id && enabledModules.contains(it.moduleId) } as? T
    }

    fun enableAll() {
        enabledModules.clear()
        try {
            val pending = modules
                .filter { isConfigured(it.moduleId) }
                .toMutableList()

            while (pending.isNotEmpty()) {
                var progressed = false
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val module = iterator.next()
                    if (missingDependencies(module).isNotEmpty()) continue
                    DebugLogger.debug(1, "正在启动模块: ${module.moduleId}")
                    module.onEnable()
                    enabledModules.add(module.moduleId)
                    iterator.remove()
                    DebugLogger.debug(1, "模块启动成功: ${module.moduleId}")
                    progressed = true
                }
                if (progressed) continue

                pending.forEach(::logDependencyBlock)
                break
            }
        } catch (exception: Exception) {
            disableEnabledModules(clearModules = false)
            throw exception
        }
    }

    fun disableAll() {
        disableEnabledModules(clearModules = true)
    }

    fun reloadAll() {
        modules.filter { enabledModules.contains(it.moduleId) }.forEach { module ->
            runCatching { module.reloadConfig() }
                .onSuccess { DebugLogger.debug(1, "模块重载完成: ${module.moduleId}") }
                .onFailure { exception ->
                    plugin.logger.log(Level.SEVERE, "Module [${module.moduleId}] reload failed.", exception)
                }
        }
    }

    fun isEnabled(id: String): Boolean {
        return enabledModules.contains(id)
    }

    fun enabledModuleIds(): List<String> {
        return modules.asSequence()
            .map { it.moduleId }
            .filter(enabledModules::contains)
            .toList()
    }

    fun configuredModuleIds(): List<String> {
        return modules.asSequence()
            .map { it.moduleId }
            .filter(::isConfigured)
            .toList()
    }

    fun moduleDiagnostics(): List<String> {
        return modules.map { module ->
            when {
                enabledModules.contains(module.moduleId) -> "${module.moduleId}=运行中"
                !isConfigured(module.moduleId) -> "${module.moduleId}=配置关闭"
                else -> "${module.moduleId}=未启动(${dependencyBlockReason(module)})"
            }
        }
    }

    private fun disableEnabledModules(clearModules: Boolean) {
        modules.reversed().forEach { module ->
            if (!enabledModules.remove(module.moduleId)) return@forEach
            DebugLogger.debug(1, "正在关闭模块: ${module.moduleId}")
            runCatching { module.onDisable() }
                .onSuccess { DebugLogger.debug(1, "模块关闭完成: ${module.moduleId}") }
                .onFailure { exception ->
                    plugin.logger.log(Level.SEVERE, "Module [${module.moduleId}] shutdown failed.", exception)
                }
        }
        if (clearModules) {
            modules.clear()
        }
    }

    private fun isConfigured(moduleId: String): Boolean {
        return plugin.config.getBoolean("modules.$moduleId", true)
    }

    private fun missingDependencies(module: CyuModule): List<String> {
        return module.requiredModules.filterNot(enabledModules::contains)
    }

    private fun logDependencyBlock(module: CyuModule) {
        plugin.logger.warning(
            "模块 [${module.moduleId}] 已跳过启动：${dependencyBlockReason(module)}。" +
                " 请开启对应依赖，或关闭 modules.${module.moduleId}。"
        )
    }

    private fun dependencyBlockReason(module: CyuModule): String {
        val reasons = module.requiredModules.map { dependencyId ->
            when {
                modules.none { it.moduleId == dependencyId } ->
                    "依赖模块 [$dependencyId] 未注册"
                !isConfigured(dependencyId) ->
                    "依赖模块 [$dependencyId] 未开启"
                !enabledModules.contains(dependencyId) ->
                    "依赖模块 [$dependencyId] 未成功启动"
                else ->
                    "依赖模块 [$dependencyId] 不可用"
            }
        }
        return reasons.ifEmpty { listOf("启动时发生异常或未被调度") }.joinToString("，")
    }
}
