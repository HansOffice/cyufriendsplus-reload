package org.cyuCBMclean.cyufriendsReload.ui.compat

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object CraftEngineItems {

    @Volatile
    private var resolved = false
    @Volatile
    private var available = false
    private var itemManager: Any? = null
    private var keyOfMethod: java.lang.reflect.Method? = null
    private var byIdMethod: java.lang.reflect.Method? = null
    private var itemBuildWithAmountMethod: java.lang.reflect.Method? = null
    private var itemBuildMethod: java.lang.reflect.Method? = null
    private var managerBuildMethod: java.lang.reflect.Method? = null

    fun build(key: String?, player: Player?): ItemStack? {
        val value = key?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!ready()) return null
        return runCatching {
            val keyObject = keyOfMethod?.invoke(null, value) ?: return null
            buildCustomItem(byIdMethod?.invoke(null, keyObject))
                ?: managerBuildMethod?.invoke(itemManager, keyObject, null) as? ItemStack
        }.getOrNull()
    }

    private fun ready(): Boolean {
        if (!resolved) resolve()
        return available
    }

    @Synchronized
    private fun resolve() {
        if (resolved) return
        resolved = true
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine") && !Bukkit.getPluginManager().isPluginEnabled("CE")) return

        runCatching {
            val keyClass = Class.forName("net.momirealms.craftengine.core.util.Key")
            val managerClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemManager")
            val itemsApiClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems")
            val buildableItemClass = Class.forName("net.momirealms.craftengine.core.item.BuildableItem")
            val enginePlayerClass = Class.forName("net.momirealms.craftengine.core.entity.player.Player")

            itemManager = managerClass.getMethod("instance").invoke(null)
            keyOfMethod = keyClass.getMethod("of", String::class.java)
            byIdMethod = itemsApiClass.getMethod("byId", keyClass)
            itemBuildWithAmountMethod = runCatching { buildableItemClass.getMethod("buildItemStack", Int::class.javaPrimitiveType) }.getOrNull()
            itemBuildMethod = runCatching { buildableItemClass.getMethod("buildItemStack") }.getOrNull()
            managerBuildMethod = managerClass.getMethod("buildItemStack", keyClass, enginePlayerClass)
            available = true
        }
    }

    private fun buildCustomItem(customItem: Any?): ItemStack? {
        if (customItem == null) return null
        return when {
            itemBuildWithAmountMethod != null -> itemBuildWithAmountMethod?.invoke(customItem, 1) as? ItemStack
            itemBuildMethod != null -> itemBuildMethod?.invoke(customItem) as? ItemStack
            else -> null
        }
    }
}
