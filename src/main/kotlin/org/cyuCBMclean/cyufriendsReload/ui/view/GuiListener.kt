package org.cyuCBMclean.cyufriendsReload.ui.view

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent

class GuiListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder is CyuView) {
            holder.handleClick(event)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder
        if (holder is CyuView) {
            holder.handleDrag(event)
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        if (holder is CyuView) {
            holder.onClose(event)
        }
    }
}