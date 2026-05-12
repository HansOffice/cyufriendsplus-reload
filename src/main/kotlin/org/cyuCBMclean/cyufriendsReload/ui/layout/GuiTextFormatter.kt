package org.cyuCBMclean.cyufriendsReload.ui.layout

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.core.config.ColorCompat
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook

object GuiTextFormatter {

    private val miniMessage = MiniMessage.miniMessage()
    private val miniMessageTagPattern = Regex("</?(?:#[0-9A-Fa-f]{6}|[A-Za-z][^>]*)>")

    fun replaceTokens(value: String, player: Player, replacements: Map<String, String>): String {
        val uid = player.uid
        val base = value
            .replace("%player%", player.name)
            .replace("%player_name%", player.name)
            .replace("%player_uid_label%", CyuIdHook.displayLabel(uid))
            .replace("%player_uid_display%", CyuIdHook.displayValue(uid))
            .replace("%player_uid%", uid)
        return replacements.entries.fold(base) { current, entry ->
            val replacement = if (entry.key.endsWith("_mm%")) entry.value else miniMessage.escapeTags(entry.value)
            current.replace(entry.key, replacement)
        }
    }

    fun renderTitle(value: String, player: Player, replacements: Map<String, String>): String {
        val resolved = replaceTokens(value, player, replacements)
        return if (looksLikeMiniMessage(resolved)) {
            ColorCompat.renderGuiMiniMessage(miniMessage, resolved)
        } else {
            ChatColor.translateAlternateColorCodes('&', resolved)
        }
    }

    fun renderUserText(value: String): String {
        return ChatColor.translateAlternateColorCodes('&', value)
    }

    private fun looksLikeMiniMessage(value: String): Boolean {
        return miniMessageTagPattern.containsMatchIn(value)
    }
}
