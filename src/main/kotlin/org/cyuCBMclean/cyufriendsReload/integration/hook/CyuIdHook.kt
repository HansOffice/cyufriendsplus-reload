package org.cyuCBMclean.cyufriendsReload.integration.hook

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CyuIdHook {

    private val knownNamesByUid = ConcurrentHashMap<String, String>()
    private val knownNamesByUuid = ConcurrentHashMap<UUID, String>()
    private val knownUidsByLowerName = ConcurrentHashMap<String, String>()
    private val localOnlinePlayers = ConcurrentHashMap<String, Player>()
    private val localOnlineNames = ConcurrentHashMap<String, String>()

    private val apiProvider by lazy {
        runCatching {
            Class.forName("org.cyuCBMclean.cyuidReload.CyuidReload").getMethod("getApi")
        }.getOrNull()
    }

    private fun api(): Any? {
        return runCatching { apiProvider?.invoke(null) }.getOrNull()
    }

    fun install(plugin: Plugin) {
        refreshOnlinePlayers()
        Bukkit.getPluginManager().registerEvents(
            object : Listener {
                @EventHandler(priority = EventPriority.MONITOR)
                fun onJoin(event: PlayerJoinEvent) {
                    cachePlayer(event.player)
                }

                @EventHandler(priority = EventPriority.MONITOR)
                fun onQuit(event: PlayerQuitEvent) {
                    uncachePlayer(event.player)
                }
            },
            plugin
        )
    }

    fun clearOnlinePlayers() {
        localOnlinePlayers.clear()
        localOnlineNames.clear()
    }

    fun getUid(uuid: UUID): String {
        getInt("getUid", UUID::class.java, uuid)?.let { return it.toString() }
        return uuid.toString()
    }

    fun getUid(player: Player): String = getUid(player.uniqueId)

    fun displayLabel(uid: String): String {
        return if (isUuidFallback(uid)) "档案ID" else "UID"
    }

    fun displayValue(uid: String): String {
        return if (isUuidFallback(uid)) uid.replace("-", "").take(8) else uid
    }

    fun getUidByName(name: String): String? {
        val input = name.trim()
        if (input.isBlank()) return null

        input.toIntOrNull()?.let { uid ->
            getString("getNameByUid", Int::class.javaPrimitiveType!!, uid)?.let { return uid.toString() }
        }

        runCatching { UUID.fromString(input) }.getOrNull()?.let { uuid ->
            knownNamesByUuid[uuid]?.let { knownUidsByLowerName.putIfAbsent(it.lowercase(), uuid.toString()) }
            return uuid.toString()
        }

        knownUidsByLowerName[input.lowercase()]?.let { return it }

        getInt("getUidByName", String::class.java, input)?.let {
            val uid = it.toString()
            cacheIdentity(uid, null, input, false)
            return uid
        }

        localOnlinePlayers.entries.firstOrNull { it.value.name.equals(input, ignoreCase = true) }?.let { return it.key }

        if (canUseBukkitFallbacks()) {
            Bukkit.getPlayerExact(input)?.let {
                cachePlayer(it)
                return it.uniqueId.toString()
            }

            val offlinePlayer = Bukkit.getOfflinePlayer(input)
            if (offlinePlayer.hasPlayedBefore()) {
                val uid = getUid(offlinePlayer.uniqueId)
                cacheIdentity(uid, offlinePlayer.uniqueId, offlinePlayer.name ?: input, false)
                return uid
            }
        }

        return knownUidsByLowerName[input.lowercase()]
    }

    fun getName(uid: String): String? {
        localOnlineNames[uid]?.let { return it }
        knownNamesByUid[uid]?.let { return it }

        uid.toIntOrNull()?.let { intUid ->
            getString("getNameByUid", Int::class.javaPrimitiveType!!, intUid)?.let {
                cacheIdentity(uid, null, it, false)
                return it
            }
        }

        val uuid = runCatching { UUID.fromString(uid) }.getOrNull() ?: return null
        knownNamesByUuid[uuid]?.let {
            knownNamesByUid.putIfAbsent(uid, it)
            return it
        }
        if (!canUseBukkitFallbacks()) return null

        return Bukkit.getOfflinePlayer(uuid).name?.also {
            cacheIdentity(uid, uuid, it, false)
        }
    }

    fun getOnlinePlayer(uid: String): Player? {
        return localOnlinePlayers[uid]
    }

    fun getOnlineName(uid: String): String? {
        return localOnlineNames[uid]
    }

    fun isOnlineLocally(uid: String): Boolean {
        return localOnlinePlayers.containsKey(uid)
    }

    fun onlineEntriesSnapshot(): List<Pair<String, String>> {
        return localOnlineNames.entries.map { it.key to it.value }
    }

    fun getOfflinePlayer(uid: String): OfflinePlayer? {
        localOnlinePlayers[uid]?.let { return it }
        val uuid = runCatching { UUID.fromString(uid) }.getOrNull()
            ?: return uid.toIntOrNull()
                ?.let { getString("getNameByUid", Int::class.javaPrimitiveType!!, it) }
                ?.let { name ->
                    cacheIdentity(uid, null, name, false)
                    if (canUseBukkitFallbacks()) Bukkit.getOfflinePlayer(name) else null
                }
        knownNamesByUuid[uuid]?.let { name ->
            if (!canUseBukkitFallbacks()) return null
            return Bukkit.getOfflinePlayer(name)
        }
        return if (canUseBukkitFallbacks()) Bukkit.getOfflinePlayer(uuid) else null
    }

    fun remapUid(playerUuid: UUID?, playerName: String?, oldUid: String, newUid: String) {
        val resolvedName = playerName
            ?: localOnlineNames[oldUid]
            ?: knownNamesByUid[oldUid]
            ?: playerUuid?.let { knownNamesByUuid[it] }

        knownNamesByUid.remove(oldUid)
        localOnlineNames.remove(oldUid)
        localOnlinePlayers.remove(oldUid)?.let { localOnlinePlayers[newUid] = it }

        if (resolvedName != null) {
            knownUidsByLowerName[resolvedName.lowercase()] = newUid
            cacheIdentity(newUid, playerUuid, resolvedName, isOnlineLocally(newUid))
        }
    }

    private fun refreshOnlinePlayers() {
        clearOnlinePlayers()
        Bukkit.getOnlinePlayers().forEach(::cachePlayer)
    }

    private fun cachePlayer(player: Player) {
        cacheIdentity(getUid(player), player.uniqueId, player.name, true)
        localOnlinePlayers[getUid(player)] = player
    }

    private fun uncachePlayer(player: Player) {
        val uid = getUid(player)
        cacheIdentity(uid, player.uniqueId, player.name, false)
        localOnlinePlayers.remove(uid)
    }

    private fun cacheIdentity(uid: String, uuid: UUID?, name: String, online: Boolean) {
        val normalizedName = name.trim()
        if (normalizedName.isNotEmpty()) {
            knownNamesByUid[uid] = normalizedName
            knownUidsByLowerName[normalizedName.lowercase()] = uid
            uuid?.let { knownNamesByUuid[it] = normalizedName }
            if (online) {
                localOnlineNames[uid] = normalizedName
            } else {
                localOnlineNames.remove(uid)
            }
        }
    }

    private fun canUseBukkitFallbacks(): Boolean {
        return runCatching { Bukkit.isPrimaryThread() }.getOrDefault(true)
    }

    private fun isUuidFallback(uid: String): Boolean {
        return runCatching { UUID.fromString(uid) }.isSuccess
    }

    private fun getInt(methodName: String, parameterType: Class<*>, parameter: Any): Int? {
        val value = invoke(methodName, parameterType, parameter) as? Int ?: return null
        return value.takeIf { it != -1 }
    }

    private fun getString(methodName: String, parameterType: Class<*>, parameter: Any): String? {
        return invoke(methodName, parameterType, parameter) as? String
    }

    private fun invoke(methodName: String, parameterType: Class<*>, parameter: Any): Any? {
        val api = api() ?: return null
        return runCatching {
            api.javaClass.getMethod(methodName, parameterType).invoke(api, parameter)
        }.getOrNull()
    }
}
