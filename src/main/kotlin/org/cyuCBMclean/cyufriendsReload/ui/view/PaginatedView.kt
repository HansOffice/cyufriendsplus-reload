package org.cyuCBMclean.cyufriendsReload.ui.view

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.config.Settings
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import kotlin.math.ceil

abstract class PaginatedView<T>(
    player: Player,
    title: String,
    pattern: GuiPattern,
    private val viewItems: Map<Char, ItemTemplate>,
    private val listChar: Char,
    private val prevChar: Char,
    private val nextChar: Char
) : CyuView(player, title, pattern, viewItems) {

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
        val element = currentItems[slot]
        DebugLogger.debug(2) {
            "GUI动态点击: player=${player.name}, view=${javaClass.simpleName}, slot=$slot, click=$clickType, matched=${element != null}"
        }
        element?.let { onElementClick(it, clickType) }
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
            val template = if (previous) Settings.guiPageDisabledPreviousTemplate else Settings.guiPageDisabledNextTemplate
            setItem(slot, template?.render(player, resolvedReplacements(if (previous) prevChar else nextChar, slot)))
        }
    }
}
