package org.cyuCBMclean.cyufriendsReload.api

import org.bukkit.Bukkit
import org.cyuCBMclean.cyufriendsReload.api.service.CyuFriendsService
import org.bukkit.plugin.Plugin

object CyuFriendsApi {

    @JvmStatic
    fun plugin(): Plugin? {
        return Bukkit.getPluginManager().getPlugin("cyufriends-reload")
    }

    @JvmStatic
    fun service(): CyuFriendsService? {
        return Bukkit.getServicesManager().load(CyuFriendsService::class.java)
    }
}
