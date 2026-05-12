package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendDefaults
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class FriendTagColorView(
    player: Player,
    private val pattern: GuiPattern,
    private val items: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val targetName: String,
    private val tagName: String,
    title: String
) : CyuView(player, title, pattern, items) {

    private val commonSlots = pattern.compileSlots('O')
    private val recentSlots = pattern.compileSlots('R')

    override fun layoutReplacements(symbol: Char, slot: Int): Map<String, String> {
        val base = mutableMapOf(
            "%raw_name%" to targetName,
            "%selected_tag%" to tagName
        )
        when (symbol) {
            'O' -> base.putAll(friendColorReplacements(slot))
            'R' -> base.putAll(recentColorReplacements(slot))
        }
        return base
    }

    override fun onRender() {
        renderCommonColors()
        renderRecentColors()
    }

    private fun renderCommonColors() {
        val template = items['O'] ?: return
        val targetUid = CyuIdHook.getUidByName(targetName) ?: return
        val colors = module.friendManager.commonTagColors(player.uid, targetUid, commonSlots.size)
        commonSlots.forEachIndexed { index, slot ->
            if (index >= colors.size) {
                setItem(slot, null)
                layoutActions.remove(slot)
                return@forEachIndexed
            }
            val color = colors[index]
            val replacements = mapOf(
                "%raw_name%" to targetName,
                "%selected_tag%" to tagName,
                "%common_color%" to color,
                "%common_color_mm%" to "<color:$color><bold>$color</bold></color>"
            )
            setItem(slot, template.render(player, replacements))
            layoutActions[slot] = LayoutBinding('O', template, replacements)
        }
    }

    private fun renderRecentColors() {
        val template = items['R'] ?: return
        val colors = module.friendManager.recentTagColors(player.uid, recentSlots.size)
        recentSlots.forEachIndexed { index, slot ->
            if (index >= colors.size) {
                setItem(slot, null)
                layoutActions.remove(slot)
                return@forEachIndexed
            }
            val replacements = mapOf(
                "%raw_name%" to targetName,
                "%selected_tag%" to tagName
            ) + recentColorReplacements(slot)
            setItem(slot, template.render(player, replacements))
            layoutActions[slot] = LayoutBinding('R', template, replacements)
        }
    }

    private fun recentColorReplacements(slot: Int): Map<String, String> {
        val index = recentSlots.indexOf(slot)
        val color = module.friendManager.recentTagColors(player.uid, recentSlots.size)
            .getOrNull(index)
            ?: FriendDefaults.TAG_COLOR_PALETTE.first()
        return mapOf(
            "%recent_color%" to color,
            "%recent_color_mm%" to "<color:$color><bold>$color</bold></color>"
        )
    }

    private fun friendColorReplacements(slot: Int): Map<String, String> {
        val targetUid = CyuIdHook.getUidByName(targetName)
        val index = commonSlots.indexOf(slot)
        val color = targetUid
            ?.let { module.friendManager.commonTagColors(player.uid, it, commonSlots.size).getOrNull(index) }
            ?: FriendDefaults.TAG_COLOR_PALETTE.first()
        return mapOf(
            "%common_color%" to color,
            "%common_color_mm%" to "<color:$color><bold>$color</bold></color>"
        )
    }
}
