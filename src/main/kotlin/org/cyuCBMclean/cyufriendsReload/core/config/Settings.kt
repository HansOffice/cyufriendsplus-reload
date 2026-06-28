package org.cyuCBMclean.cyufriendsReload.core.config

import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.configuration.file.FileConfiguration
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate

object Settings {
    var databaseType: String = "SQLite"
        private set
    var databaseHost: String = "localhost"
        private set
    var databasePort: Int = 3306
        private set
    var requestCooldownSeconds: Long = 60
        private set
    var defaultMaxFriends: Int = 50
        private set
    var guiOfflineHeadSource: String = ""
        private set
    var guiHeadCacheSize: Long = 512L
        private set
    var guiPageDisabledEnabled: Boolean = true
        private set
    var guiPageDisabledMaterial: String = "GRAY_STAINED_GLASS_PANE"
        private set
    var guiPageDisabledPreviousName: String = "<dark_gray>已是第一页</dark_gray>"
        private set
    var guiPageDisabledNextName: String = "<dark_gray>已是最后一页</dark_gray>"
        private set
    var guiPageDisabledLore: List<String> = listOf("<gray>当前页 <white>%page%</white>/<white>%total_pages%</white></gray>")
        private set
    var guiPageDisabledCustomModelData: Int = 0
        private set
    var guiPageDisabledPreviousTemplate: ItemTemplate? = null
        private set
    var guiPageDisabledNextTemplate: ItemTemplate? = null
        private set

    fun reload(config: FileConfiguration) {
        databaseType = config.getString("database.type", databaseType)!!
        databaseHost = config.getString("database.host", databaseHost)!!
        databasePort = config.getInt("database.port", databasePort)
        requestCooldownSeconds = config.getLong("settings.request-cooldown", requestCooldownSeconds)
        defaultMaxFriends = config.getInt("settings.max-friends-default", defaultMaxFriends)
        guiOfflineHeadSource = config.getString("gui.default-offline-head", guiOfflineHeadSource) ?: ""
        guiHeadCacheSize = config.getLong("gui.head-cache-size", guiHeadCacheSize).coerceIn(16L, 4096L)
        guiPageDisabledEnabled = config.getBoolean(
            "gui.pagination.disabled.enabled",
            config.getBoolean("gui.page_disable_hint", guiPageDisabledEnabled)
        )
        guiPageDisabledMaterial = config.getString(
            "gui.pagination.disabled.material",
            config.getString("gui.page_disable_item.material", guiPageDisabledMaterial)
        ) ?: guiPageDisabledMaterial
        guiPageDisabledPreviousName = config.getString(
            "gui.pagination.disabled.previous-name",
            guiPageDisabledPreviousName
        ) ?: guiPageDisabledPreviousName
        guiPageDisabledNextName = config.getString(
            "gui.pagination.disabled.next-name",
            guiPageDisabledNextName
        ) ?: guiPageDisabledNextName
        guiPageDisabledLore = config.getStringList("gui.pagination.disabled.lore")
            .takeIf { it.isNotEmpty() }
            ?: guiPageDisabledLore
        guiPageDisabledCustomModelData = config.getInt("gui.pagination.disabled.custom_model_data", 0).coerceAtLeast(0)
        guiPageDisabledPreviousTemplate = loadDisabledTemplate(config, "previous", guiPageDisabledPreviousName)
        guiPageDisabledNextTemplate = loadDisabledTemplate(config, "next", guiPageDisabledNextName)
    }

    private fun loadDisabledTemplate(config: FileConfiguration, key: String, fallbackName: String): ItemTemplate {
        val custom = config.getConfigurationSection("gui.pagination.disabled.$key")
        if (custom != null) return ItemTemplate(custom)

        val memory = MemoryConfiguration()
        val section = memory.createSection("item")
        section.set("material", guiPageDisabledMaterial)
        section.set("custom_model_data", guiPageDisabledCustomModelData)
        section.set("name", fallbackName)
        section.set("lore", guiPageDisabledLore)
        return ItemTemplate(section)
    }
}
