package org.cyuCBMclean.cyufriendsReload.modules.profile

import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuTask
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.proxyModule
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CompletableFuture

class BirthdayReminderTask(
    private val plugin: CyufriendsReload,
    private val profileModule: ProfileModule
) {
    private var task: CyuTask? = null

    private data class OnlinePlayerSnapshot(
        val player: Player,
        val name: String,
        val worldName: String
    )

    fun start() {
        stop()
        if (!plugin.config.getBoolean("birthdayReminder.enabled", false)) return

        val checkTime = runCatching {
            LocalTime.parse(plugin.config.getString("birthdayReminder.checkTime", "00:05") ?: "00:05")
        }.getOrDefault(LocalTime.of(0, 5))

        val now = LocalTime.now()
        val seconds = Duration.between(now, checkTime).seconds.let {
            if (it < 0) it + 86400 else it
        }

        task = CyuConcurrency.scheduler.runTimerAsync(plugin, seconds * 20L, 1728000L) {
            runBlocking { execute() }
        }
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    suspend fun remindOnJoin(player: Player, uid: String, worldName: String) {
        if (!plugin.config.getBoolean("birthdayReminder.enabled", false)) return
        if (isDisabledWorld(worldName)) return
        if (!profileModule.manager.isBirthdayToday(uid)) return
        if (!profileModule.manager.checkAndMarkBirthdayReminder(uid)) return

        CyuConcurrency.scheduler.runEntity(plugin, player) {
            player.sendLang("birthday-reminder-self")
            player.playAudio("birthday-reminder-self")
        }
    }

    private suspend fun execute() {
        if (!plugin.config.getBoolean("birthdayReminder.enabled", false)) return

        val today = LocalDate.now()
        val offsets = reminderOffsets()
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val chatModule = plugin.moduleManager.getModule<ChatModule>("chat")
        val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        val localPlayers = captureOnlinePlayers()

        offsets.forEach { offset ->
            val birthdays = profileModule.manager.getBirthdaysAfter(offset.toLong())
            birthdays.forEach { uid ->
                val name = localPlayers[uid]?.name ?: CyuIdHook.getName(uid) ?: return@forEach
                val scope = "${today}:$offset"
                if (!profileModule.manager.checkAndMarkBirthdayBroadcast(uid, scope)) return@forEach

                if (offset == 0) {
                    localPlayers[uid]?.let { player ->
                        remindOnJoin(player.player, uid, player.worldName)
                    }
                }

                val message = birthdayMessage(name, offset) ?: return@forEach
                val friends = friendModule?.friendManager?.getFriends(uid) ?: emptySet()
                friends.forEach { friendUid ->
                    if (friendUid == uid) return@forEach
                    val onlineFriend = localPlayers[friendUid]
                    if (onlineFriend != null) {
                        if (isDisabledWorld(onlineFriend.worldName)) return@forEach
                        CyuConcurrency.scheduler.runEntity(plugin, onlineFriend.player) {
                            if (offset == 0) {
                                onlineFriend.player.sendLang("birthday-reminder-friend", mapOf("player" to name))
                            } else {
                                onlineFriend.player.sendLang("birthday-reminder-upcoming", mapOf("player" to name, "days" to offset.toString()))
                            }
                            onlineFriend.player.playAudio("birthday-reminder-friend")
                        }
                    } else if (proxyGateway != null && plugin.proxyModule()?.remotePresence?.find(friendUid) != null) {
                        if (proxyGateway.sendBirthdayNotify(friendUid, name, offset) == null) {
                            chatModule?.manager?.sendOfflineMessage("0", friendUid, message)
                        }
                    } else {
                        chatModule?.manager?.sendOfflineMessage("0", friendUid, message)
                    }
                }
            }
        }
    }

    private fun reminderOffsets(): List<Int> {
        val configured = plugin.config.getIntegerList("birthdayReminder.advance-days")
            .map { it.coerceAtLeast(0) }
        return (configured + 0).distinct().sorted()
    }

    private fun birthdayMessage(playerName: String, daysAhead: Int): String? {
        val path = if (daysAhead <= 0) "birthdayReminder.friendMessage" else "birthdayReminder.advanceMessage"
        val fallback = if (daysAhead <= 0) {
            "今天是 {player} 的生日，别忘了送上祝福。"
        } else {
            "{player} 还有 {days} 天就生日了，记得提前准备祝福。"
        }
        return plugin.config.getString(path, fallback)
            ?.replace("{player}", playerName)
            ?.replace("{days}", daysAhead.toString())
    }

    private fun isDisabledWorld(worldName: String): Boolean {
        val worlds = plugin.config.getStringList("birthdayReminder.disabledWorlds")
        return worlds.any { it.equals(worldName, ignoreCase = true) }
    }

    private fun captureOnlinePlayers(): Map<String, OnlinePlayerSnapshot> {
        val future = CompletableFuture<Map<String, OnlinePlayerSnapshot>>()
        CyuConcurrency.scheduler.runGlobal(plugin) {
            future.complete(
                Bukkit.getOnlinePlayers().associate { player ->
                    player.uid to OnlinePlayerSnapshot(player, player.name, player.world.name)
                }
            )
        }
        return future.join()
    }
}
