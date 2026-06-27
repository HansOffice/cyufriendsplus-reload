package org.cyuCBMclean.cyufriendsReload.ui.compat

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Method

object ExternalMenuItems {

    fun build(spec: String?): ItemStack? {
        val value = spec?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = value.lowercase()
        return when {
            lower.startsWith("itemsadder:") -> itemsAdder(value.substringAfter(':'))
            lower.startsWith("ia:") -> itemsAdder(value.substringAfter(':'))
            lower.startsWith("oraxen:") -> oraxen(value.substringAfter(':'))
            lower.startsWith("ox:") -> oraxen(value.substringAfter(':'))
            lower.startsWith("nexo:") -> nexo(value.substringAfter(':'))
            else -> null
        }
    }

    fun isExternal(spec: String?): Boolean {
        val lower = spec?.trim()?.lowercase() ?: return false
        return lower.startsWith("itemsadder:")
            || lower.startsWith("ia:")
            || lower.startsWith("oraxen:")
            || lower.startsWith("ox:")
            || lower.startsWith("nexo:")
    }

    private fun itemsAdder(id: String): ItemStack? {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return null
        return runCatching {
            val api = Class.forName("dev.lone.itemsadder.api.CustomStack")
            val customStack = api.getMethod("getInstance", String::class.java).invoke(null, id.trim()) ?: return null
            customStack.javaClass.getMethod("getItemStack").invoke(customStack) as? ItemStack
        }.getOrNull()?.clone()
    }

    private fun oraxen(id: String): ItemStack? {
        if (!Bukkit.getPluginManager().isPluginEnabled("Oraxen")) return null
        return runCatching {
            val api = Class.forName("io.th0rgal.oraxen.api.OraxenItems")
            val builder = api.getMethod("getItemById", String::class.java).invoke(null, id.trim()) ?: return null
            buildKnownItem(builder)
        }.getOrNull()
    }

    private fun nexo(id: String): ItemStack? {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) return null
        return nexoFrom("com.nexomc.nexo.api.NexoItems", id)
            ?: nexoFrom("com.nexomc.nexo.api.NexoItemsAPI", id)
    }

    private fun nexoFrom(className: String, id: String): ItemStack? {
        return runCatching {
            val api = Class.forName(className)
            val raw = invokeFirstStringMethod(api, id.trim(), "itemFromId", "getItemById", "getItem", "byId") ?: return null
            buildKnownItem(raw)
        }.getOrNull()
    }

    private fun buildKnownItem(raw: Any?): ItemStack? {
        val item = when (raw) {
            null -> null
            is ItemStack -> raw.clone()
            else -> {
                invokeNoArg(raw, "build") as? ItemStack
                    ?: invokeNoArg(raw, "getItemStack") as? ItemStack
                    ?: invokeNoArg(raw, "itemStack") as? ItemStack
            }
        }
        return item?.clone()
    }

    private fun invokeFirstStringMethod(clazz: Class<*>, value: String, vararg names: String): Any? {
        for (name in names) {
            val result = runCatching { clazz.getMethod(name, String::class.java).invoke(null, value) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun invokeNoArg(target: Any, name: String): Any? {
        val method: Method = runCatching { target.javaClass.getMethod(name) }.getOrNull() ?: return null
        return runCatching { method.invoke(target) }.getOrNull()
    }
}
