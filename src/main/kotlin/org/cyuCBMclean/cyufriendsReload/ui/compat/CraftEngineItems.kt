package org.cyuCBMclean.cyufriendsReload.ui.compat

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import java.lang.reflect.Field
import java.lang.reflect.Method

object CraftEngineItems {

    @Volatile
    private var resolved = false
    @Volatile
    private var available = false
    private var keyClass: Class<*>? = null
    private var itemsApiClass: Class<*>? = null
    private var contextClass: Class<*>? = null
    private var enginePlayerClass: Class<*>? = null
    private var adaptorClass: Class<*>? = null
    private var managerClass: Class<*>? = null
    private var itemManager: Any? = null
    private var keyOf: Method? = null
    private var keyFrom: Method? = null
    private var byIdKey: Method? = null
    private var byIdString: Method? = null
    private var adaptPlayerMethod: Method? = null
    private var contextOfPlayer: Method? = null
    private var contextEmpty: Method? = null
    private var contextEmptyField: Field? = null
    private var buildBukkitItemContextAmount: Method? = null
    private var buildBukkitItemContext: Method? = null
    private var buildItemContextAmount: Method? = null
    private var buildBukkitItemEnginePlayer: Method? = null
    private var buildItemEnginePlayer: Method? = null
    private var buildBukkitItemNoArg: Method? = null
    private var buildItemStackAmount: Method? = null
    private var buildItemStackNoArg: Method? = null
    private var managerCreateCustomWrapped: Method? = null
    private var managerCreateWrapped: Method? = null
    private var managerBuildItemStack: Method? = null
    private var getBukkitItem: Method? = null
    private var platformItem: Method? = null

    fun build(key: String?, player: Player?): ItemStack? {
        val value = normalizeKey(key) ?: return null
        if (!ready()) return null

        val keyObject = createKey(value) ?: return null
        val enginePlayer = adaptPlayer(player)
        val context = buildContext(enginePlayer)

        return buildFromApi(value, keyObject, context, enginePlayer)
            ?: buildFromManager(keyObject, enginePlayer)
    }

    private fun ready(): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine") && !Bukkit.getPluginManager().isPluginEnabled("CE")) {
            return false
        }
        if (!resolved) resolve()
        return available
    }

    @Synchronized
    private fun resolve() {
        if (resolved) return
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine") && !Bukkit.getPluginManager().isPluginEnabled("CE")) return

        runCatching {
            keyClass = Class.forName("net.momirealms.craftengine.core.util.Key")
            itemsApiClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems")
            contextClass = Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext")
            enginePlayerClass = Class.forName("net.momirealms.craftengine.core.entity.player.Player")
            adaptorClass = runCatching { Class.forName("net.momirealms.craftengine.bukkit.api.BukkitAdaptor") }
                .getOrElse { Class.forName("net.momirealms.craftengine.bukkit.api.BukkitAdaptors") }
            managerClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemManager")
            val definitionClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemDefinition")
            val bukkitItemClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItem")

            keyOf = keyClass?.methodOrNull("of", String::class.java)
            keyFrom = keyClass?.methodOrNull("from", String::class.java)
            byIdKey = itemsApiClass?.methodOrNull("byId", keyClass)
            byIdString = itemsApiClass?.methodOrNull("byId", String::class.java)
            adaptPlayerMethod = adaptorClass?.methodOrNull("adapt", Player::class.java)
            contextOfPlayer = contextClass?.methodOrNull("of", enginePlayerClass)
            contextEmpty = contextClass?.methodOrNull("empty")
            contextEmptyField = runCatching { contextClass?.getField("EMPTY") }.getOrNull()
            buildBukkitItemContextAmount = definitionClass.methodOrNull("buildBukkitItem", contextClass, Int::class.javaPrimitiveType)
            buildBukkitItemContext = definitionClass.methodOrNull("buildBukkitItem", contextClass)
            buildItemContextAmount = definitionClass.methodOrNull("buildItem", contextClass, Int::class.javaPrimitiveType)
            buildBukkitItemEnginePlayer = definitionClass.methodOrNull("buildBukkitItem", enginePlayerClass)
            buildItemEnginePlayer = definitionClass.methodOrNull("buildItem", enginePlayerClass)
            buildBukkitItemNoArg = definitionClass.methodOrNull("buildBukkitItem")
            buildItemStackAmount = definitionClass.methodOrNull("buildItemStack", Int::class.javaPrimitiveType)
            buildItemStackNoArg = definitionClass.methodOrNull("buildItemStack")
            managerCreateCustomWrapped = managerClass?.methodOrNull("createCustomWrappedItem", keyClass, enginePlayerClass)
            managerCreateWrapped = managerClass?.methodOrNull("createWrappedItem", keyClass, enginePlayerClass)
            managerBuildItemStack = managerClass?.methodOrNull("buildItemStack", keyClass, enginePlayerClass)
            getBukkitItem = bukkitItemClass.methodOrNull("getBukkitItem")
            platformItem = bukkitItemClass.methodOrNull("platformItem")
            itemManager = managerClass?.getMethod("instance")?.invoke(null)

            require(keyOf != null || keyFrom != null)
            require(byIdKey != null || byIdString != null)
            available = true
            resolved = true
        }.onFailure {
            available = false
            resolved = true
            DebugLogger.debug(1) { "CraftEngine 物品桥接未启用: ${it.message}" }
        }
    }

    private fun buildFromApi(id: String, keyObject: Any, context: Any?, enginePlayer: Any?): ItemStack? {
        val customItem = runCatching { byIdKey?.invoke(null, keyObject) }.getOrNull()
            ?: runCatching { byIdString?.invoke(null, id) }.getOrNull()
            ?: return null

        return buildDefinition(customItem, context, enginePlayer)
    }

    private fun buildDefinition(definition: Any, context: Any?, enginePlayer: Any?): ItemStack? {
        if (context != null) {
            runCatching { buildBukkitItemContextAmount?.invoke(definition, context, 1) as? ItemStack }
                .getOrNull()?.clone()?.let { return it }
            runCatching { buildBukkitItemContext?.invoke(definition, context) as? ItemStack }
                .getOrNull()?.clone()?.let { return it }
            runCatching { buildItemContextAmount?.invoke(definition, context, 1) }
                .getOrNull()?.toBukkitItem()?.let { return it }
        }

        if (enginePlayer != null) {
            runCatching { buildBukkitItemEnginePlayer?.invoke(definition, enginePlayer) as? ItemStack }
                .getOrNull()?.clone()?.let { return it }
            runCatching { buildItemEnginePlayer?.invoke(definition, enginePlayer) }
                .getOrNull()?.toBukkitItem()?.let { return it }
        }

        runCatching { buildBukkitItemNoArg?.invoke(definition) as? ItemStack }
            .getOrNull()?.clone()?.let { return it }
        runCatching { buildItemStackAmount?.invoke(definition, 1) as? ItemStack }
            .getOrNull()?.clone()?.let { return it }
        return runCatching { buildItemStackNoArg?.invoke(definition) as? ItemStack }
            .getOrNull()?.clone()
    }

    private fun buildFromManager(keyObject: Any, enginePlayer: Any?): ItemStack? {
        val manager = itemManager ?: return null
        return runCatching { managerCreateCustomWrapped?.invoke(manager, keyObject, enginePlayer) }
            .getOrNull()?.toBukkitItem()
            ?: runCatching { managerCreateWrapped?.invoke(manager, keyObject, enginePlayer) }
                .getOrNull()?.toBukkitItem()
            ?: runCatching { managerBuildItemStack?.invoke(manager, keyObject, enginePlayer) as? ItemStack }
                .getOrNull()?.clone()
    }

    private fun Any.toBukkitItem(): ItemStack? {
        return when (this) {
            is ItemStack -> this.clone()
            else -> runCatching { getBukkitItem?.invoke(this) as? ItemStack }.getOrNull()?.clone()
                ?: runCatching { platformItem?.invoke(this) as? ItemStack }.getOrNull()?.clone()
        }
    }

    private fun buildContext(enginePlayer: Any?): Any? {
        val clazz = contextClass ?: return null
        if (enginePlayer != null) {
            runCatching { contextOfPlayer?.invoke(null, enginePlayer) }.getOrNull()?.let { return it }
        }
        return runCatching { contextEmpty?.invoke(null) }.getOrNull()
            ?: runCatching { contextEmptyField?.get(null) }.getOrNull()
    }

    private fun adaptPlayer(player: Player?): Any? {
        if (player == null) return null
        val raw = runCatching { adaptPlayerMethod?.invoke(null, player) }.getOrNull()
        return raw?.takeIf { enginePlayerClass?.isInstance(it) != false }
    }

    private fun createKey(value: String): Any? {
        return runCatching { keyOf?.invoke(null, value) }.getOrNull()
            ?: runCatching { keyFrom?.invoke(null, value) }.getOrNull()
    }

    private fun normalizeKey(key: String?): String? {
        val value = key?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = value.lowercase()
        return when {
            lower.startsWith("craftengine:") -> value.substringAfter(':').trim().takeIf { it.isNotEmpty() }
            lower.startsWith("ce:") -> value.substringAfter(':').trim().takeIf { it.isNotEmpty() }
            else -> value
        }
    }

    private fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>?): Method? {
        if (parameterTypes.any { it == null }) return null
        val params = parameterTypes.filterNotNull().toTypedArray()
        return runCatching { getMethod(name, *params) }.getOrNull()
    }
}
