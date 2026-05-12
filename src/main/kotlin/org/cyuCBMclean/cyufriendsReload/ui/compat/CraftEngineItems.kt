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
    private var engine: Any? = null
    private var keyOfMethod: java.lang.reflect.Method? = null
    private var adaptMethod: java.lang.reflect.Method? = null
    private var buildMethod: java.lang.reflect.Method? = null

    fun build(key: String?, player: Player?): ItemStack? {
        val value = key?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!ready()) return null
        return runCatching {
            val keyObject = keyOfMethod?.invoke(null, value) ?: return null
            val enginePlayer = player?.let { adaptMethod?.invoke(engine, it) }
            buildMethod?.invoke(itemManager, keyObject, enginePlayer) as? ItemStack
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
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) return

        runCatching {
            val keyClass = Class.forName("net.momirealms.craftengine.core.util.Key")
            val managerClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemManager")
            val mainClass = Class.forName("net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine")
            val enginePlayerClass = Class.forName("net.momirealms.craftengine.core.entity.player.Player")

            itemManager = managerClass.getMethod("instance").invoke(null)
            engine = mainClass.getMethod("instance").invoke(null)
            keyOfMethod = keyClass.getMethod("of", String::class.java)
            adaptMethod = mainClass.getMethod("adapt", Player::class.java)
            buildMethod = managerClass.getMethod("buildItemStack", keyClass, enginePlayerClass)
            available = true
        }
    }
}
