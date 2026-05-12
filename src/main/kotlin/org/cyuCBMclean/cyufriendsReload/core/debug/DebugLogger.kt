package org.cyuCBMclean.cyufriendsReload.core.debug

import org.bukkit.plugin.Plugin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Level

object DebugLogger {

    private var plugin: Plugin? = null
    private var consoleEnabled = false
    private var fileEnabled = false
    private var detailLevel = 0
    private var filePath = "logs/debug.log"
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun initialize(plugin: Plugin) {
        this.plugin = plugin
        reload()
    }

    fun reload() {
        val plugin = plugin ?: return
        consoleEnabled = plugin.config.getBoolean("debugLog.enable-console-output", false)
        fileEnabled = plugin.config.getBoolean("debugLog.enable-file-output", false)
        detailLevel = plugin.config.getInt("debugLog.detail-level", 0).coerceIn(0, 2)
        filePath = plugin.config.getString("debugLog.file", "logs/debug.log")
            ?.trim()
            ?.ifBlank { "logs/debug.log" }
            ?: "logs/debug.log"
        if (isEnabled()) {
            log(Level.INFO, "DEBUG", "调试日志已启用，级别: $detailLevel")
        }
    }

    fun shutdown() {
        plugin = null
        consoleEnabled = false
        fileEnabled = false
        detailLevel = 0
    }

    fun configureRuntime(
        consoleEnabled: Boolean? = null,
        fileEnabled: Boolean? = null,
        detailLevel: Int? = null
    ) {
        consoleEnabled?.let { this.consoleEnabled = it }
        fileEnabled?.let { this.fileEnabled = it }
        detailLevel?.let { this.detailLevel = it.coerceIn(0, 2) }
        if (isEnabled()) {
            log(Level.INFO, "DEBUG", "调试日志已临时调整，级别: ${this.detailLevel}")
        }
    }

    fun debug(level: Int, message: String) {
        if (!isLevelEnabled(level)) return
        log(Level.INFO, "DEBUG-$level", message)
    }

    inline fun debug(level: Int, message: () -> String) {
        if (!isLevelEnabled(level)) return
        debug(level, message())
    }

    fun info(message: String) {
        if (!isEnabled()) return
        log(Level.INFO, "INFO", message)
    }

    fun warning(message: String) {
        if (!isEnabled()) return
        log(Level.WARNING, "WARN", message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        val plugin = plugin ?: return
        if (throwable == null) {
            plugin.logger.severe("[CyuFriends-Reload/ERROR] $message")
        } else {
            plugin.logger.log(Level.SEVERE, "[CyuFriends-Reload/ERROR] $message", throwable)
        }
        writeFile("ERROR", message, throwable)
    }

    fun isEnabled(): Boolean = consoleEnabled || fileEnabled

    fun isConsoleEnabled(): Boolean = consoleEnabled

    fun isFileEnabled(): Boolean = fileEnabled

    fun detailLevel(): Int = detailLevel

    fun isLevelEnabled(level: Int): Boolean = isEnabled() && level <= detailLevel

    fun fileLocation(): String {
        val plugin = plugin ?: return filePath
        return File(plugin.dataFolder, filePath).absolutePath
    }

    private fun log(level: Level, tag: String, message: String) {
        val plugin = plugin ?: return
        if (consoleEnabled) {
            plugin.logger.log(level, "[CyuFriends-Reload/$tag] $message")
        }
        writeFile(tag, message, null)
    }

    private fun writeFile(tag: String, message: String, throwable: Throwable?) {
        if (!fileEnabled) return
        val plugin = plugin ?: return
        val file = File(plugin.dataFolder, filePath)
        runCatching {
            file.parentFile?.mkdirs()
            val timestamp = LocalDateTime.now().format(timeFormatter)
            val stack = throwable?.let {
                val writer = StringWriter()
                it.printStackTrace(PrintWriter(writer))
                System.lineSeparator() + writer.toString().trimEnd()
            }.orEmpty()
            file.appendText("[$timestamp] [$tag] $message$stack${System.lineSeparator()}", Charsets.UTF_8)
        }.onFailure {
            if (consoleEnabled) {
                plugin.logger.warning("[CyuFriends-Reload/WARN] Debug 文件写入失败: ${it.message}")
            }
        }
    }
}
