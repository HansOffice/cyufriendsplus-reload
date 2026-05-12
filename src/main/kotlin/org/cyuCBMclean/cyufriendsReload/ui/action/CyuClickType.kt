package org.cyuCBMclean.cyufriendsReload.ui.action

import org.bukkit.event.inventory.ClickType

enum class CyuClickType {
    LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, DOUBLE_CLICK, DROP, ALL;

    companion object {
        fun fromBukkit(type: ClickType): CyuClickType = when (type) {
            ClickType.LEFT -> LEFT
            ClickType.RIGHT -> RIGHT
            ClickType.SHIFT_LEFT -> SHIFT_LEFT
            ClickType.SHIFT_RIGHT -> SHIFT_RIGHT
            ClickType.MIDDLE -> MIDDLE
            ClickType.DOUBLE_CLICK -> DOUBLE_CLICK
            ClickType.DROP, ClickType.CONTROL_DROP -> DROP
            else -> ALL
        }
    }
}