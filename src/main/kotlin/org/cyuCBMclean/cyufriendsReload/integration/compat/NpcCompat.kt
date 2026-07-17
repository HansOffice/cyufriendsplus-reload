package org.cyuCBMclean.cyufriendsReload.integration.compat

import org.bukkit.entity.Entity

object NpcCompat {

    private val citizensRegistry by lazy {
        runCatching {
            val api = Class.forName("net.citizensnpcs.api.CitizensAPI")
            api.getMethod("getNPCRegistry").invoke(null)
        }.getOrNull()
    }

    fun isNpc(entity: Entity): Boolean {
        if (entity.hasMetadata("NPC")) return true
        val registry = citizensRegistry ?: return false
        return runCatching {
            registry.javaClass.getMethod("isNPC", Entity::class.java).invoke(registry, entity) == true
        }.getOrDefault(false)
    }
}
