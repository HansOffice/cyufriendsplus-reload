package org.cyuCBMclean.cyufriendsReload.ui.compat

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

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
            itemManager = managerClass?.getMethod("instance")?.invoke(null)
            available = true
            resolved = true
        }.onFailure {
            available = false
            resolved = true
        }
    }

    private fun buildFromApi(id: String, keyObject: Any, context: Any?, enginePlayer: Any?): ItemStack? {
        val api = itemsApiClass ?: return null
        val customItem = runCatching { api.getMethod("byId", keyClass).invoke(null, keyObject) }.getOrNull()
            ?: runCatching { api.getMethod("byId", String::class.java).invoke(null, id) }.getOrNull()
            ?: return null

        return buildDefinition(customItem, context, enginePlayer)
    }

    private fun buildDefinition(definition: Any, context: Any?, enginePlayer: Any?): ItemStack? {
        if (context != null) {
            runCatching { definition.javaClass.getMethod("buildBukkitItem", contextClass, Int::class.javaPrimitiveType).invoke(definition, context, 1) as? ItemStack }
                .getOrNull()?.clone()?.let { return it }
            runCatching { definition.javaClass.getMethod("buildBukkitItem", contextClass).invoke(definition, context) as? ItemStack }
                .getOrNull()?.clone()?.let { return it }
            runCatching { definition.javaClass.getMethod("buildItem", contextClass, Int::class.javaPrimitiveType).invoke(definition, context, 1) }
                .getOrNull()?.toBukkitItem()?.let { return it }
        }

        if (enginePlayer != null) {
            runCatching { definition.javaClass.getMethod("buildBukkitItem", enginePlayerClass).invoke(definition, enginePlayer) as? ItemStack }
                .getOrNull()?.clone()?.let { return it }
            runCatching { definition.javaClass.getMethod("buildItem", enginePlayerClass).invoke(definition, enginePlayer) }
                .getOrNull()?.toBukkitItem()?.let { return it }
        }

        runCatching { definition.javaClass.getMethod("buildBukkitItem").invoke(definition) as? ItemStack }
            .getOrNull()?.clone()?.let { return it }
        runCatching { definition.javaClass.getMethod("buildItemStack", Int::class.javaPrimitiveType).invoke(definition, 1) as? ItemStack }
            .getOrNull()?.clone()?.let { return it }
        return runCatching { definition.javaClass.getMethod("buildItemStack").invoke(definition) as? ItemStack }
            .getOrNull()?.clone()
    }

    private fun buildFromManager(keyObject: Any, enginePlayer: Any?): ItemStack? {
        val manager = itemManager ?: return null
        val playerType = enginePlayerClass ?: return null
        return runCatching { manager.javaClass.getMethod("createCustomWrappedItem", keyClass, playerType).invoke(manager, keyObject, enginePlayer) }
            .getOrNull()?.toBukkitItem()
            ?: runCatching { manager.javaClass.getMethod("createWrappedItem", keyClass, playerType).invoke(manager, keyObject, enginePlayer) }
                .getOrNull()?.toBukkitItem()
            ?: runCatching { manager.javaClass.getMethod("buildItemStack", keyClass, playerType).invoke(manager, keyObject, enginePlayer) as? ItemStack }
                .getOrNull()?.clone()
    }

    private fun Any.toBukkitItem(): ItemStack? {
        return when (this) {
            is ItemStack -> this.clone()
            else -> runCatching { javaClass.getMethod("getBukkitItem").invoke(this) as? ItemStack }.getOrNull()?.clone()
                ?: runCatching { javaClass.getMethod("platformItem").invoke(this) as? ItemStack }.getOrNull()?.clone()
        }
    }

    private fun buildContext(enginePlayer: Any?): Any? {
        val clazz = contextClass ?: return null
        if (enginePlayer != null) {
            runCatching { clazz.getMethod("of", enginePlayerClass).invoke(null, enginePlayer) }.getOrNull()?.let { return it }
        }
        return runCatching { clazz.getMethod("empty").invoke(null) }.getOrNull()
            ?: runCatching { clazz.getField("EMPTY").get(null) }.getOrNull()
    }

    private fun adaptPlayer(player: Player?): Any? {
        if (player == null) return null
        val adaptor = adaptorClass ?: return null
        val raw = runCatching { adaptor.getMethod("adapt", Player::class.java).invoke(null, player) }.getOrNull()
        return raw?.takeIf { enginePlayerClass?.isInstance(it) != false }
    }

    private fun createKey(value: String): Any? {
        val clazz = keyClass ?: return null
        return runCatching { clazz.getMethod("of", String::class.java).invoke(null, value) }.getOrNull()
            ?: runCatching { clazz.getMethod("from", String::class.java).invoke(null, value) }.getOrNull()
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
}
