package org.cyuCBMclean.cyufriendsReload.ui.view

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionNode
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate

abstract class CyuView(
    val player: Player,
    title: String,
    private val pattern: GuiPattern,
    private val items: Map<Char, ItemTemplate>
) : InventoryHolder {

    protected data class LayoutBinding(
        val symbol: Char,
        val template: ItemTemplate,
        val replacements: Map<String, String>
    )

    private val inv = Bukkit.createInventory(this, pattern.size, title)
    protected val layoutActions = mutableMapOf<Int, LayoutBinding>()

    fun open() {
        pattern.mapLayout().forEach { (char, slots) ->
            val template = items[char] ?: return@forEach
            slots.forEach { slot ->
                val replacements = resolvedReplacements(char, slot)
                val itemStack = template.render(player, replacements)
                inv.setItem(slot, itemStack)
                layoutActions[slot] = LayoutBinding(char, template, replacements)
            }
        }
        onRender()
        player.openInventory(inv)
    }

    protected open fun viewReplacements(): Map<String, String> = emptyMap()

    protected open fun layoutReplacements(symbol: Char, slot: Int): Map<String, String> = emptyMap()

    protected open fun builtinReplacements(): Map<String, String> {
        val uid = player.uid
        return mapOf(
            "%player%" to player.name,
            "%player_name%" to player.name,
            "%player_uid%" to uid,
            "%player_uid_label%" to CyuIdHook.displayLabel(uid),
            "%player_uid_display%" to CyuIdHook.displayValue(uid)
        )
    }

    open fun onRender() {}
    open fun onClose(event: InventoryCloseEvent) {}
    open fun onDynamicClick(slot: Int, clickType: CyuClickType) {}

    fun handleClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val slot = event.rawSlot
        if (slot < 0 || slot >= inv.size) return

        val clickType = CyuClickType.fromBukkit(event.click)
        val binding = layoutActions[slot]

        if (binding != null) {
            val nodes = binding.template.actions[clickType] ?: binding.template.actions[CyuClickType.ALL] ?: return
            playGuiClickSound(binding.template, clickType, nodes)
            ActionRegistry.execute(
                player,
                nodes.map { node ->
                    node.copy(
                        payload = binding.replacements.entries.fold(node.payload) { current, entry ->
                            current.replace(entry.key, entry.value)
                        }
                    )
                }
            )
        } else {
            if (event.currentItem != null) player.playAudio(defaultGuiSound(clickType, emptyList()))
            onDynamicClick(slot, clickType)
        }
    }

    fun handleDrag(event: InventoryDragEvent) {
        if (event.rawSlots.any { it < inv.size }) event.isCancelled = true
    }

    override fun getInventory(): Inventory = inv

    protected fun setItem(slot: Int, item: ItemStack?) {
        inv.setItem(slot, item)
    }

    protected fun hideStaticSymbol(symbol: Char) {
        val slots = layoutActions
            .filterValues { it.symbol == symbol }
            .keys
            .toList()
        slots.forEach { slot ->
            layoutActions.remove(slot)
            setItem(slot, null)
        }
    }

    protected fun hideStaticSymbols(vararg symbols: Char) {
        symbols.forEach(::hideStaticSymbol)
    }

    protected fun rerenderLayoutBindings(bindings: Map<Int, LayoutBinding> = layoutActions) {
        val snapshot = bindings.toMap()
        snapshot.forEach { (slot, binding) ->
            val replacements = resolvedReplacements(binding.symbol, slot)
            setItem(slot, binding.template.render(player, replacements))
            layoutActions[slot] = LayoutBinding(binding.symbol, binding.template, replacements)
        }
    }

    protected fun resolvedReplacements(symbol: Char, slot: Int): Map<String, String> {
        val builtins = builtinReplacements()
        val global = viewReplacements()
        val local = layoutReplacements(symbol, slot)
        if (global.isEmpty() && local.isEmpty()) return builtins
        return LinkedHashMap<String, String>(builtins.size + global.size + local.size).apply {
            putAll(builtins)
            putAll(global)
            putAll(local)
        }
    }

    protected fun playGuiElementSound(clickType: CyuClickType, nodes: List<ActionNode> = emptyList()) {
        player.playAudio(defaultGuiSound(clickType, nodes))
    }

    private fun playGuiClickSound(template: ItemTemplate, clickType: CyuClickType, nodes: List<ActionNode>) {
        player.playAudio(template.sound(clickType) ?: defaultGuiSound(clickType, nodes))
    }

    private fun defaultGuiSound(clickType: CyuClickType, nodes: List<ActionNode>): String {
        if (nodes.any { it.executorId == "prev_page" || it.executorId == "next_page" }) return "gui-page"
        if (nodes.any { it.executorId == "close" }) return "gui-close"
        if (nodes.any { it.executorId == "player" && isCancelCommand(it.payload) }) return "gui-cancel"
        if (nodes.any { it.executorId == "player" && isConfirmCommand(it.payload) }) return "gui-confirm"
        return when (clickType) {
            CyuClickType.RIGHT,
            CyuClickType.SHIFT_RIGHT -> "gui-secondary-click"
            CyuClickType.MIDDLE -> "gui-middle-click"
            else -> "gui-click"
        }
    }

    private fun isCancelCommand(payload: String): Boolean {
        val lower = payload.lowercase()
        return lower.startsWith("friend profile ") ||
            lower.startsWith("friend profiledetail ") ||
            lower.contains(" cancel") ||
            lower.contains(" deny") ||
            lower.contains(" reject")
    }

    private fun isConfirmCommand(payload: String): Boolean {
        val lower = payload.lowercase()
        return lower.contains(" accept") ||
            lower.contains(" approve") ||
            lower.startsWith("friend remove ") ||
            lower.startsWith("friend revoke ") ||
            lower.startsWith("friend add ") ||
            lower.startsWith("friend tag") ||
            lower.startsWith("friend group") ||
            lower.startsWith("wall approve") ||
            lower.startsWith("status pin") ||
            lower.startsWith("birthday ")
    }
}
