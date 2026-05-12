package org.cyuCBMclean.cyufriendsReload.modules.profile.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendTeleportMode
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileData
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class SettingsView(
    player: Player,
    pattern: GuiPattern,
    itemsMap: Map<Char, ItemTemplate>,
    private val module: ProfileModule,
    title: String
) : CyuView(player, title, pattern, itemsMap) {

    private val dynamicSlots = mutableMapOf<Int, LayoutBinding>()
    private val toggleSymbols = setOf('R', 'M', 'N', 'O', 'T')

    override fun onRender() {
        bindDynamicSlots()
        rerenderLayoutBindings()
        if (!module.plugin.moduleManager.isEnabled("social")) {
            hideStaticSymbol('S')
        }
        val replacements = viewReplacements()
        dynamicSlots.forEach { (slot, binding) ->
            setItem(slot, binding.template.render(player, replacements))
        }
    }

    override fun onDynamicClick(slot: Int, clickType: CyuClickType) {
        val binding = dynamicSlots[slot] ?: return
        val uid = player.uid
        val profile = module.manager.getProfileStoredSync(uid)

        if (binding.symbol == 'R') {
            profile.allowRequests = !profile.allowRequests
            updateProfile(profile, invalidateProfile = true)
            return
        }

        if (binding.symbol == 'M') {
            profile.allowPrivateMsg = !profile.allowPrivateMsg
            updateProfile(profile, invalidateProfile = true)
            return
        }

        val friendModule = module.plugin.moduleManager.getModule<FriendModule>("friend") ?: return
        val proxyGateway = module.plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(module.plugin) {
            val sound = runBlocking {
                when (binding.symbol) {
                    'N' -> if (friendModule.preferencesManager.toggleNotifyOnJoin(uid)) "notify-enabled" else "notify-disabled"
                    'O' -> if (friendModule.preferencesManager.toggleNotifyOwnFriends(uid)) "notifyme-enabled" else "notifyme-disabled"
                    'T' -> teleportModeSound(friendModule.preferencesManager.cycleTeleportMode(uid))
                    else -> null
                }
            }
            if (sound != null) {
                proxyGateway?.invalidateSettings(uid)
            }
            CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                sound?.let(player::playAudio)
                onRender()
            }
        }
    }

    override fun viewReplacements(): Map<String, String> {
        val profile = module.manager.getProfileStoredSync(player.uid)
        val preferences = module.plugin.moduleManager.getModule<FriendModule>("friend")?.preferencesManager?.snapshotStoredSync(player.uid)
        val socialSummary = if (module.plugin.moduleManager.isEnabled("social")) {
            val socialEnabledCount = listOf(
                profile.notifyStatusLike,
                profile.notifyStatusComment,
                profile.notifyWallPost,
                profile.notifyWallLike,
                profile.notifyWallComment
            ).count { it }
            "$socialEnabledCount/5 已开启"
        } else {
            "模块已关闭"
        }
        return mapOf(
            "%state_req%" to state(profile.allowRequests),
            "%state_msg%" to state(profile.allowPrivateMsg),
            "%state_notify%" to state(preferences?.notifyOnJoin ?: true),
            "%state_notifyme%" to state(preferences?.notifyOwnFriends ?: true),
            "%state_tp%" to teleportMode(preferences?.teleportMode),
            "%state_social_summary%" to socialSummary
        )
    }

    private fun bindDynamicSlots() {
        if (dynamicSlots.isNotEmpty()) return
        layoutActions.toMap().forEach { (slot, binding) ->
            if (binding.symbol in toggleSymbols) {
                dynamicSlots[slot] = binding
                layoutActions.remove(slot)
            }
        }
    }

    private fun updateProfile(profile: ProfileData, invalidateProfile: Boolean) {
        val proxyGateway = module.plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(module.plugin) {
            runBlocking { module.manager.updateProfile(profile) }
            if (invalidateProfile) {
                proxyGateway?.invalidateProfile(profile.uid)
            }
            CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                player.playAudio("success")
                onRender()
            }
        }
    }

    private fun state(enabled: Boolean): String {
        return if (enabled) "开启" else "关闭"
    }

    private fun teleportMode(mode: FriendTeleportMode?): String {
        return when (mode) {
            FriendTeleportMode.CONFIRM -> "需要确认"
            FriendTeleportMode.DENY -> "拒绝传送"
            else -> "允许直达"
        }
    }

    private fun teleportModeSound(mode: FriendTeleportMode): String {
        return when (mode) {
            FriendTeleportMode.DIRECT -> "tp-toggle-enabled"
            FriendTeleportMode.CONFIRM -> "tp-toggle-confirm"
            FriendTeleportMode.DENY -> "tp-toggle-disabled"
        }
    }
}
