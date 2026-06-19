package org.cyuCBMclean.cyufriendsReload.ui.action

import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent

enum class CyuClickType {
    LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, DOUBLE_CLICK, DROP, ALL;

    companion object {
        fun fromBukkit(event: InventoryClickEvent): CyuClickType {
            val click = event.click
            val actionName = event.action.name
            if (click == ClickType.MIDDLE || actionName == "CLONE_STACK") return MIDDLE
            if (click == ClickType.DOUBLE_CLICK) return DOUBLE_CLICK
            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) return DROP

            val shift = click.isShiftClick || event.isShiftClick || actionName == "MOVE_TO_OTHER_INVENTORY"
            val right = click.isRightClick || event.isRightClick
            val left = click.isLeftClick || event.isLeftClick
            return when {
                shift && right -> SHIFT_RIGHT
                shift && left -> SHIFT_LEFT
                right -> RIGHT
                left -> LEFT
                else -> ALL
            }
        }

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
