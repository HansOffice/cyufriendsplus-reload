package org.cyuCBMclean.cyufriendsReload.ui.layout

import org.bukkit.entity.Player

data class GuiDefinition(
    val pattern: GuiPattern,
    val items: Map<Char, ItemTemplate>,
    val titleTemplate: String?
) {
    fun resolveTitle(player: Player, fallbackTitle: String, replacements: Map<String, String> = emptyMap()): String {
        val template = titleTemplate?.takeIf { it.isNotBlank() } ?: return fallbackTitle
        return GuiTextFormatter.renderTitle(template, player, replacements)
    }
}
