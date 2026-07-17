package org.cyuCBMclean.cyufriendsReload.modules.profile.gui

import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPreferences
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
    private lateinit var cachedProfile: ProfileData
    private var cachedPreferences: FriendPreferences? = null

    override suspend fun prepareData() {
        val uid = player.uid
        cachedProfile = module.manager.loadProfile(uid)
        val friendModule = module.plugin.moduleManager.getModule<FriendModule>("friend")
        friendModule?.preferencesManager?.loadPlayer(uid)
        cachedPreferences = friendModule?.preferencesManager?.snapshotCached(uid)
    }

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

        if (binding.symbol == 'R') {
            cachedProfile.allowRequests = !cachedProfile.allowRequests
            updateProfile(cachedProfile, invalidateProfile = true)
            return
        }

        if (binding.symbol == 'M') {
            cachedProfile.allowPrivateMsg = !cachedProfile.allowPrivateMsg
            updateProfile(cachedProfile, invalidateProfile = true)
            return
        }

        val friendModule = module.plugin.moduleManager.getModule<FriendModule>("friend") ?: return
        val proxyGateway = module.plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        runAsyncOperation(
            operation = {
                val sound = when (binding.symbol) {
                    'N' -> if (friendModule.preferencesManager.toggleNotifyOnJoin(uid)) "notify-enabled" else "notify-disabled"
                    'O' -> if (friendModule.preferencesManager.toggleNotifyOwnFriends(uid)) "notifyme-enabled" else "notifyme-disabled"
                    'T' -> teleportModeSound(friendModule.preferencesManager.cycleTeleportMode(uid))
                    else -> null
                }
                sound to friendModule.preferencesManager.snapshotCached(uid)
            },
            onSuccess = { (sound, preferences) ->
                cachedPreferences = preferences
                if (sound != null) proxyGateway?.invalidateSettings(uid)
                sound?.let(player::playAudio)
                refreshOpenView()
            }
        )
    }

    override fun viewReplacements(): Map<String, String> {
        val profile = cachedProfile
        val preferences = cachedPreferences
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
        runAsyncOperation(
            operation = {
                module.manager.updateProfile(profile)
                profile
            },
            onSuccess = {
                cachedProfile = it
                if (invalidateProfile) proxyGateway?.invalidateProfile(it.uid)
                player.playAudio("success")
                refreshOpenView()
            }
        )
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
