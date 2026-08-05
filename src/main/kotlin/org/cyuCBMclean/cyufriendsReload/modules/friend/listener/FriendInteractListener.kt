package org.cyuCBMclean.cyufriendsReload.modules.friend.listener

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.AddFriendView
import org.cyuCBMclean.cyufriendsReload.modules.friend.gui.FriendProfileView
import org.cyuCBMclean.cyufriendsReload.integration.compat.NpcCompat
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiLoader
import org.cyuCBMclean.cyufriendsReload.ui.view.ViewTitles
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FriendInteractListener(
    private val plugin: CyufriendsReload,
    private val module: FriendModule
) : Listener {

    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEntityEvent) {
        if (!enabled()) return
        val target = event.rightClicked as? Player ?: return
        val player = event.player
        if (NpcCompat.isNpc(target)) return


        if (!player.isSneaking) return
        if (event.hand != EquipmentSlot.HAND) return
        if (player.inventory.itemInMainHand.type != Material.AIR) return
        if (!checkCooldown(player)) return

        event.isCancelled = true

        if (!player.hasPermission("cyufriends.command.gui")) {
            player.sendLang("no-permission")
            return
        }

        cooldowns[player.uniqueId] = System.currentTimeMillis()
        player.playAudio(soundKey())

        val playerUid = player.uid
        val targetUid = target.uid

        if (playerUid == targetUid) return

        if (module.friendManager.isFriend(playerUid, targetUid)) {
            openFriendProfile(player, targetUid, target.name)
        } else {
            openAddFriend(player, target.name)
        }
    }

    private fun openFriendProfile(player: Player, targetUid: String, targetName: String) {
        val guiData = GuiLoader.load(plugin, "friend_profile.yml") ?: run {
            player.sendLang("gui-open-failed")
            return
        }
        val title = guiData.resolveTitle(
            player,
            ViewTitles.friendProfile(targetName),
            mapOf("%target_name%" to targetName, "%friend_name%" to targetName, "%raw_name%" to targetName)
        )
        FriendProfileView(player, guiData.pattern, guiData.items, module, targetUid, targetName, title).open()
    }

    private fun openAddFriend(player: Player, targetName: String) {
        val guiData = GuiLoader.load(plugin, "add_friend.yml") ?: run {
            player.sendLang("gui-open-failed")
            return
        }
        val title = guiData.resolveTitle(
            player,
            ViewTitles.addFriend(targetName),
            mapOf("%target_name%" to targetName, "%friend_name%" to targetName, "%raw_name%" to targetName)
        )
        AddFriendView(player, guiData.pattern, guiData.items, targetName, title).open()
    }

    private fun enabled(): Boolean {
        return plugin.config.getBoolean(
            "interactionSettings.shiftRightClickMenu",
            plugin.config.getBoolean("interaction_settings.shift_right_click_menu", true)
        )
    }

    private fun soundKey(): String {
        return plugin.config.getString(
            "interactionSettings.sound",
            plugin.config.getString("interaction_settings.sound_action", "quick-interact")
        ) ?: "quick-interact"
    }

    private fun checkCooldown(player: Player): Boolean {
        val seconds = plugin.config.getLong(
            "interactionSettings.cooldownSeconds",
            plugin.config.getLong("interaction_settings.cooldown_seconds", 3L)
        )
        if (seconds <= 0L) return true
        val now = System.currentTimeMillis()
        val last = cooldowns[player.uniqueId] ?: return true
        return now - last >= seconds * 1000L
    }
}
