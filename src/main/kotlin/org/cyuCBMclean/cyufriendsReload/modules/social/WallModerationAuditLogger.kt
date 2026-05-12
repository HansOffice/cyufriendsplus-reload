package org.cyuCBMclean.cyufriendsReload.modules.social

import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WallModerationAuditLogger(private val plugin: CyufriendsReload) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    private val lock = Any()

    fun log(action: String, fields: Map<String, String>) {
        if (!plugin.config.getBoolean("wallModeration.audit-log", true)) return
        val file = resolveFile()
        val line = buildString {
            append(formatter.format(Instant.now()))
            append(" | action=")
            append(sanitize(action))
            fields.toSortedMap().forEach { (key, value) ->
                append(" | ")
                append(sanitize(key))
                append("=")
                append(sanitize(value))
            }
            append(System.lineSeparator())
        }
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.appendText(line, StandardCharsets.UTF_8)
        }
    }

    fun recent(limit: Int = 8): List<String> {
        val file = resolveFile()
        if (!file.exists()) return emptyList()
        synchronized(lock) {
            return file.readLines(StandardCharsets.UTF_8)
                .takeLast(limit.coerceAtLeast(1))
        }
    }

    fun configuredPath(): String {
        return plugin.config.getString("wallModeration.audit-file")?.takeIf { it.isNotBlank() } ?: "logs/wall-moderation.log"
    }

    private fun resolveFile(): File {
        return File(plugin.dataFolder, configuredPath())
    }

    private fun sanitize(value: String): String {
        return value.replace("\r", " ").replace("\n", " ").replace("|", "/").trim()
    }
}
