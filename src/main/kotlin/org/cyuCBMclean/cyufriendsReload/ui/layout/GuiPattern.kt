package org.cyuCBMclean.cyufriendsReload.ui.layout

class GuiPattern(private val rows: List<String>) {

    val size: Int = rows.size * 9

    init {
        require(rows.all { it.length == 9 })
    }

    fun compileSlots(char: Char): List<Int> {
        val slots = mutableListOf<Int>()
        rows.forEachIndexed { rowIndex, rowString ->
            rowString.forEachIndexed { colIndex, c ->
                if (c == char) {
                    slots.add(rowIndex * 9 + colIndex)
                }
            }
        }
        return slots
    }

    fun mapLayout(): Map<Char, List<Int>> {
        val layoutMap = mutableMapOf<Char, MutableList<Int>>()
        rows.forEachIndexed { rowIndex, rowString ->
            rowString.forEachIndexed { colIndex, c ->
                if (c != ' ' && c != '.') {
                    layoutMap.computeIfAbsent(c) { mutableListOf() }.add(rowIndex * 9 + colIndex)
                }
            }
        }
        return layoutMap
    }
}