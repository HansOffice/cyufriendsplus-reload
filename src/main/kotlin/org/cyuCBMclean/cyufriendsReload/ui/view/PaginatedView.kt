package org.cyuCBMclean.cyufriendsReload.ui.view

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.cyuCBMclean.cyufriendsReload.core.config.ColorCompat
import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import kotlin.math.ceil

/**
 * 翻页菜单的公共底座，列表页只关心数据和点击
 */
abstract class PaginatedView<T>(
    player: Player,
    title: String,
    pattern: GuiPattern,
    private val viewItems: Map<Char, ItemTemplate>,
    private val listChar: Char,
    private val prevChar: Char,
    private val nextChar: Char
) : CyuView(player, title, pattern, viewItems) {

    companion object {
        private val miniMessage = MiniMessage.miniMessage()
    }

    var page = 1
        private set

    private var lastSourceSize = 0
    private var lastTotalPages = 1

    private val listSlots = pattern.compileSlots(listChar)
    private val prevSlots = pattern.compileSlots(prevChar)
    private val nextSlots = pattern.compileSlots(nextChar)
    private val currentItems = mutableMapOf<Int, T>()

    abstract fun getSource(): List<T>
    abstract fun mapElement(element: T): ItemStack
    abstract fun onElementClick(element: T, clickType: CyuClickType)

    override fun builtinReplacements(): Map<String, String> {
        return super.builtinReplacements() + mapOf(
            "%page%" to page.toString(),
            "%current_page%" to page.toString(),
            "%total_pages%" to lastTotalPages.toString(),
            "%entry_count%" to lastSourceSize.toString()
        )
    }

    override fun onRender() {
        val source = getSource()
        val maxPage = maxOf(1, ceil(source.size.toDouble() / maxOf(1, listSlots.size)).toInt())
        lastSourceSize = source.size
        lastTotalPages = maxPage
        if (page > maxPage) page = maxPage
        if (page < 1) page = 1

        layoutActions
            .filterKeys { it !in listSlots && it !in prevSlots && it !in nextSlots }
            .forEach { (slot, binding) ->
                val replacements = resolvedReplacements(binding.symbol, slot)
                setItem(slot, binding.template.render(player, replacements))
                layoutActions[slot] = LayoutBinding(binding.symbol, binding.template, replacements)
            }

        currentItems.clear()
        val start = (page - 1) * listSlots.size
        val end = minOf(start + listSlots.size, source.size)
        val subList = source.subList(start, end)

        listSlots.forEachIndexed { index, slot ->
            layoutActions.remove(slot)
            if (index < subList.size) {
                val element = subList[index]
                setItem(slot, mapElement(element))
                currentItems[slot] = element
            } else {
                setItem(slot, null)
            }
        }

        prevSlots.forEach { slot -> layoutActions.remove(slot); setItem(slot, null) }
        nextSlots.forEach { slot -> layoutActions.remove(slot); setItem(slot, null) }

        if (page > 1) {
            prevSlots.forEach { slot ->
                viewItems[prevChar]?.let {
                    val replacements = resolvedReplacements(prevChar, slot)
                    setItem(slot, it.render(player, replacements))
                    layoutActions[slot] = LayoutBinding(prevChar, it, replacements)
                }
            }
        } else {
            renderDisabledPageSlots(prevSlots, previous = true)
        }
        if (page < maxPage) {
            nextSlots.forEach { slot ->
                viewItems[nextChar]?.let {
                    val replacements = resolvedReplacements(nextChar, slot)
                    setItem(slot, it.render(player, replacements))
                    layoutActions[slot] = LayoutBinding(nextChar, it, replacements)
                }
            }
        } else {
            renderDisabledPageSlots(nextSlots, previous = false)
        }
    }

    override fun onDynamicClick(slot: Int, clickType: CyuClickType) {
        currentItems[slot]?.let { onElementClick(it, clickType) }
    }

    fun jumpTo(targetPage: Int) {
        page = targetPage
    }

    protected fun pageCapacity(): Int = maxOf(1, listSlots.size)

    protected fun totalPagesFor(itemCount: Int): Int {
        return maxOf(1, ceil(itemCount.toDouble() / pageCapacity()).toInt())
    }

    fun nextPage() { page++; onRender() }
    fun prevPage() { page--; onRender() }

    private fun renderDisabledPageSlots(slots: List<Int>, previous: Boolean) {
        if (!Settings.guiPageDisabledEnabled || slots.isEmpty()) return
        slots.forEach { slot ->
            setItem(slot, disabledPageItem(previous, resolvedReplacements(if (previous) prevChar else nextChar, slot)))
        }
    }

    private fun disabledPageItem(previous: Boolean, replacements: Map<String, String>): ItemStack {
        val material = parseMaterial(Settings.guiPageDisabledMaterial)
        val item = ItemStack(material.first, 1)
        if (material.second > 0) item.durability = material.second
        val meta = item.itemMeta ?: return item
        val name = if (previous) Settings.guiPageDisabledPreviousName else Settings.guiPageDisabledNextName
        applyText(meta, name, replacements)
        Settings.guiPageDisabledLore.takeIf { it.isNotEmpty() }?.let { lore ->
            applyLore(meta, lore, replacements)
        }
        applyCustomModelData(meta)
        item.itemMeta = meta
        return item
    }

    private fun applyText(meta: ItemMeta, value: String, replacements: Map<String, String>) {
        val rendered = replaceText(value, replacements)
        val component = ColorCompat.parseMiniMessage(miniMessage, rendered)
        if (component == null || !ColorCompat.applyGuiDisplayName(meta, component)) {
            meta.setDisplayName(ColorCompat.renderGuiMiniMessage(miniMessage, rendered))
        }
    }

    private fun applyLore(meta: ItemMeta, values: List<String>, replacements: Map<String, String>) {
        val rendered = values.map { replaceText(it, replacements) }
        val components = rendered.mapNotNull { ColorCompat.parseMiniMessage(miniMessage, it) }
        if (components.size != rendered.size || !ColorCompat.applyGuiLore(meta, components)) {
            meta.lore = rendered.map { ColorCompat.renderGuiMiniMessage(miniMessage, it) }
        }
    }

    private fun renderText(value: String, replacements: Map<String, String>): String {
        val rendered = replaceText(value, replacements)
        return ColorCompat.renderGuiMiniMessage(miniMessage, rendered)
    }

    private fun replaceText(value: String, replacements: Map<String, String>): String {
        var rendered = value
        replacements.forEach { (key, replacement) -> rendered = rendered.replace(key, replacement) }
        return rendered
    }

    private fun applyCustomModelData(meta: ItemMeta) {
        val customModelData = Settings.guiPageDisabledCustomModelData
        if (customModelData <= 0) return
        runCatching {
            meta.javaClass.getMethod("setCustomModelData", Int::class.javaPrimitiveType).invoke(meta, customModelData)
        }
    }

    private fun parseMaterial(value: String): Pair<Material, Short> {
        val raw = value.trim()
        val name = raw.substringBefore(':').trim()
        val data = raw.substringAfter(':', "").toShortOrNull() ?: 0
        val material = Material.matchMaterial(name.uppercase())
            ?: if (name.equals("GRAY_STAINED_GLASS_PANE", ignoreCase = true)) {
                Material.matchMaterial("STAINED_GLASS_PANE")
            } else {
                null
            }
            ?: Material.GRAY_STAINED_GLASS_PANE
        val durability = if (raw.contains(':')) data else if (material.name == "STAINED_GLASS_PANE") 7 else 0
        return material to durability.toShort()
    }
}
