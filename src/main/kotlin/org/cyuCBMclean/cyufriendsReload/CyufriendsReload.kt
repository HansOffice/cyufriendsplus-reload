package org.cyuCBMclean.cyufriendsReload

import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.ServicePriority
import org.cyuCBMclean.cyufriendsReload.api.service.CyuFriendsService
import org.cyuCBMclean.cyufriendsReload.api.service.CyuFriendsServiceImpl
import org.cyuCBMclean.cyufriendsReload.command.dispatcher.DispatcherRegistry
import org.cyuCBMclean.cyufriendsReload.core.config.LanguageEngine
import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.config.SoundEngine
import org.cyuCBMclean.cyufriendsReload.core.database.DatabaseManager
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.module.ModuleManager
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.integration.listener.CyuIdChangeListener
import org.cyuCBMclean.cyufriendsReload.integration.placeholder.CyuFriendsPlaceholderExpansion
import org.cyuCBMclean.cyufriendsReload.integration.placeholder.RelationalFriendsPlaceholderExpansion
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.group.GroupModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialModule
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiLoader
import org.cyuCBMclean.cyufriendsReload.ui.view.GuiListener
import java.io.File
import java.nio.file.Files
import java.util.logging.Level

class CyufriendsReload : JavaPlugin() {

    lateinit var moduleManager: ModuleManager
        private set
    lateinit var langEngine: LanguageEngine
        private set
    lateinit var soundEngine: SoundEngine
        private set
    lateinit var databaseManager: DatabaseManager
        private set
    lateinit var apiService: CyuFriendsService
        private set

    companion object {
        private val bundledGuiGuideFiles = setOf(
            "gui_guide.yml"
        )

        private val bundledGuiFiles = listOf(
            "add_friend.yml",
            "blacklist.yml",
            "birthdays_list.yml",
            "friends_list.yml",
            "friend_profile.yml",
            "friend_profile_details.yml",
            "friend_profile_social.yml",
            "friend_remove_confirm.yml",
            "friend_tag_colors.yml",
            "friend_tag_filters.yml",
            "friend_tag_manage.yml",
            "friend_timeline.yml",
            "groups_list.yml",
            "group_members.yml",
            "group_batch_move.yml",
            "group_rules.yml",
            "group_move.yml",
            "gui_guide.yml",
            "friend_recommendations.yml",
            "messages_list.yml",
            "message_chat.yml",
            "notification_center.yml",
            "online_players.yml",
            "profile_home.yml",
            "requests_list.yml",
            "sent_requests.yml",
            "settings_panel.yml",
            "settings_social.yml",
            "status_list.yml",
            "status_comments.yml",
            "wall_comments.yml",
            "wall_comment_pending.yml",
            "wall_pending.yml",
            "wall_view.yml"
        )

        lateinit var instance: CyufriendsReload
            private set
    }

    override fun onLoad() {
        instance = this
    }

    override fun onEnable() {
        try {
            saveDefaultConfig()
            saveBundledResource("Placeholder.yml")
            saveBundledResource("Permissions.yml")
            DebugLogger.initialize(this)
            syncBundledGuiResources()
            auditGuiResources()
            Settings.reload(config)
            GuiHeads.reload()

            langEngine = LanguageEngine(this)
            langEngine.initialize()

            soundEngine = SoundEngine(this)
            soundEngine.reload()

            registerApiService()
            registerPlaceholders()
            startRuntime()
            printEnableBanner()
        } catch (exception: Exception) {
            DebugLogger.error("运行时初始化失败: ${exception.message}", exception)
            logger.log(Level.SEVERE, "CyuFriends runtime initialization failed.", exception)
            shutdownRuntime()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        val enabledModules = if (::moduleManager.isInitialized) moduleManager.enabledModuleIds() else emptyList()
        shutdownRuntime()
        server.servicesManager.unregisterAll(this)
        if (::langEngine.isInitialized) langEngine.shutdown()
        printDisableBanner(enabledModules)
        DebugLogger.shutdown()
    }

    private fun registerModules() {
        moduleManager.register(ProxyModule(this))
        moduleManager.register(FriendModule(this, databaseManager))
        moduleManager.register(GroupModule(this))
        moduleManager.register(ChatModule(this, databaseManager))
        moduleManager.register(SocialModule(this, databaseManager))
        moduleManager.register(org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule(this, databaseManager))
    }

    fun reloadRuntime() {
        shutdownRuntime()
        try {
            reloadConfig()
            DebugLogger.reload()
            Settings.reload(config)
            GuiHeads.reload()
            if (::langEngine.isInitialized) langEngine.reload()
            if (::soundEngine.isInitialized) soundEngine.reload()
            syncBundledGuiResources()
            auditGuiResources()
            startRuntime()
            DebugLogger.info("插件重载完成，当前已启用模块: ${enabledModuleSummary()}")
        } catch (exception: Exception) {
            shutdownRuntime()
            server.pluginManager.disablePlugin(this)
            throw exception
        }
    }

    private fun registerPlaceholders() {
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            CyuFriendsPlaceholderExpansion(this).register()
            CyuFriendsPlaceholderExpansion(this, "friends").register()
            RelationalFriendsPlaceholderExpansion(this).register()
            RelationalFriendsPlaceholderExpansion(this, "friendsrel").register()
        }
    }

    private fun registerCyuIdListener() {
        if (server.pluginManager.isPluginEnabled("cyuid-reload")) {
            server.pluginManager.registerEvents(CyuIdChangeListener(this), this)
        }
    }

    private fun registerApiService() {
        apiService = CyuFriendsServiceImpl(this)
        server.servicesManager.register(CyuFriendsService::class.java, apiService, this, ServicePriority.Normal)
    }

    private fun saveBundledResource(path: String) {
        val file = File(dataFolder, path)
        if (!file.exists()) saveResource(path, false)
    }

    private fun syncBundledGuiResources() {
        bundledGuiFiles.forEach(::syncBundledGuiResource)
    }

    private fun auditGuiResources() {
        val runtimeGuiFiles = bundledGuiFiles.filterNot(bundledGuiGuideFiles::contains)
        val guiFolder = File(dataFolder, "gui")
        val missing = runtimeGuiFiles.filterNot { File(guiFolder, it).exists() }
        val invalid = runtimeGuiFiles.filterNot { GuiLoader.load(this, it) != null }
        val extra = guiFolder.listFiles()
            ?.filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
            ?.map(File::getName)
            ?.filterNot { it in bundledGuiFiles }
            ?.sorted()
            .orEmpty()

        if (missing.isNotEmpty()) {
            logger.severe("以下 GUI 模板缺失：${missing.joinToString("、")}")
        }
        if (invalid.isNotEmpty()) {
            logger.severe("以下 GUI 模板无法正常加载：${invalid.joinToString("、")}")
        }

        DebugLogger.debug(
            1,
            "GUI 模板检查完成：运行模板 ${runtimeGuiFiles.size - invalid.size}/${runtimeGuiFiles.size}，说明文件 ${bundledGuiGuideFiles.size}。"
        )
        if (extra.isNotEmpty()) {
            DebugLogger.debug(1, "检测到额外 GUI 文件：${extra.joinToString("、")}。这些文件会保留，但不会被主类同步覆盖。")
        }
    }

    private fun syncBundledGuiResource(fileName: String) {
        val resourcePath = "gui/$fileName"
        val target = File(dataFolder, resourcePath)
        val snapshot = File(dataFolder, "gui/.bundled/$fileName")
        val bundled = getResource(resourcePath)?.use { it.readBytes() } ?: return

        target.parentFile?.mkdirs()
        snapshot.parentFile?.mkdirs()

        val targetBytes = target.takeIf(File::exists)?.readBytes()
        val snapshotBytes = snapshot.takeIf(File::exists)?.readBytes()

        // 玩家改过的 GUI 不覆盖，只更新还停在内置版本的文件
        if (targetBytes == null || (snapshotBytes != null && targetBytes.contentEquals(snapshotBytes))) {
            Files.write(target.toPath(), bundled)
        }

        if (snapshotBytes == null || !snapshotBytes.contentEquals(bundled)) {
            Files.write(snapshot.toPath(), bundled)
        }
    }

    private fun startRuntime() {
        databaseManager = DatabaseManager(this)
        databaseManager.connect()
        DebugLogger.debug(0, "数据库连接已建立。")

        moduleManager = ModuleManager(this)
        CyuIdHook.install(this)
        registerModules()
        moduleManager.enableAll()
        DebugLogger.debug(0, "模块启动完成：${enabledModuleSummary()}")

        server.pluginManager.registerEvents(GuiListener(), this)
        registerCyuIdListener()
        DebugLogger.debug(1, "GUI 监听器与 UID 监听器注册完成。")
    }

    private fun shutdownRuntime() {
        HandlerList.unregisterAll(this)
        DispatcherRegistry.clear()
        CyuIdHook.clearOnlinePlayers()
        if (::moduleManager.isInitialized) moduleManager.disableAll()
        CyuConcurrency.scheduler.cancelAll(this)
        if (::databaseManager.isInitialized) databaseManager.close()
        DebugLogger.debug(0, "运行时资源已释放。")
    }

    private fun enabledModuleSummary(): String {
        if (!::moduleManager.isInitialized) return "无"
        return moduleManager.enabledModuleIds().ifEmpty { listOf("无") }.joinToString(", ")
    }

    private fun printEnableBanner() {
        val console = Bukkit.getConsoleSender()
        console.sendMessage("§8══════════════════════════════════════════════")
        console.sendMessage("§b  CyuFriends-Reload §7插件已启用")
        console.sendMessage("§7  Author: §fHansOffice")
        console.sendMessage("§7  QQ交流群: §f331910315")
        console.sendMessage("§7  已启用模块: §f${enabledModuleSummary()}")
        if (DebugLogger.isEnabled()) {
            console.sendMessage("§7  Debug: §a开启 §7(Level §f${DebugLogger.detailLevel()}§7)")
        }
        console.sendMessage("§8══════════════════════════════════════════════")
    }

    private fun printDisableBanner(enabledModules: List<String>) {
        val console = Bukkit.getConsoleSender()
        val modules = enabledModules.ifEmpty { listOf("无") }.joinToString(", ")
        console.sendMessage("§8══════════════════════════════════════════════")
        console.sendMessage("§c  CyuFriends-Reload §7已禁用")
        console.sendMessage("§7  Author: §fHansOffice")
        console.sendMessage("§7  已关闭模块: §f$modules")
        console.sendMessage("§8══════════════════════════════════════════════")
    }
}
