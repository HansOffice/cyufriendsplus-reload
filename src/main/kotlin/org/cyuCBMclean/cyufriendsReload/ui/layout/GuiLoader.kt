package org.cyuCBMclean.cyufriendsReload.ui.layout

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import java.io.File

/**
 * GUI 模板加载器，菜单长什么样交给 yml 决定
 */
object GuiLoader {
    fun load(plugin: Plugin, fileName: String): GuiDefinition? {
        val file = File(plugin.dataFolder, "gui/$fileName")
        if (!file.exists()) {
            runCatching { plugin.saveResource("gui/$fileName", false) }
                .onFailure { plugin.logger.warning("GUI 资源 gui/$fileName 释放失败: ${it.message}") }
        }
        if (!file.exists()) {
            plugin.logger.warning("缺少 GUI 配置: gui/$fileName")
            return null
        }

        val yaml = YamlConfiguration.loadConfiguration(file)
        val layoutKeys = yaml.getStringList("layout")
        if (layoutKeys.isEmpty()) {
            plugin.logger.warning("GUI 配置 gui/$fileName 未定义 layout。")
            return null
        }
        DebugLogger.debug(2) { "GUI 模板已加载: gui/$fileName | rows=${layoutKeys.size}" }

        val pattern = GuiPattern(layoutKeys)
        val items = mutableMapOf<Char, ItemTemplate>()

        yaml.getConfigurationSection("items")?.let { sec ->
            sec.getKeys(false).forEach { key ->
                if (key.isNotEmpty()) {
                    sec.getConfigurationSection(key)?.let { itemSec ->
                        items[key[0]] = ItemTemplate(itemSec)
                    }
                }
            }
        }
        return GuiDefinition(pattern, items, yaml.getString("title"))
    }
}
