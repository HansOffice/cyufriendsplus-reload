package org.cyuCBMclean.cyufriendsReload.modules.profile.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.social.SocialInteractionNoticeType
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class SocialSettingsView(
    player: Player,
    pattern: GuiPattern,
    itemsMap: Map<Char, ItemTemplate>,
    private val module: ProfileModule,
    title: String
) : CyuView(player, title, pattern, itemsMap) {

    private val dynamicSlots = mutableMapOf<Int, LayoutBinding>()
    private val toggleSymbols = mapOf(
        'L' to SocialInteractionNoticeType.STATUS_LIKE,
        'C' to SocialInteractionNoticeType.STATUS_COMMENT,
        'W' to SocialInteractionNoticeType.WALL_POST,
        'K' to SocialInteractionNoticeType.WALL_LIKE,
        'R' to SocialInteractionNoticeType.WALL_COMMENT
    )

    override fun onRender() {
        bindDynamicSlots()
        rerenderLayoutBindings()
        val replacements = viewReplacements()
        dynamicSlots.forEach { (slot, binding) ->
            setItem(slot, binding.template.render(player, replacements))
        }
    }

    override fun onDynamicClick(slot: Int, clickType: CyuClickType) {
        val binding = dynamicSlots[slot] ?: return
        val type = toggleSymbols[binding.symbol] ?: return
        val uid = player.uid
        val proxyGateway = module.plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        CyuConcurrency.scheduler.runAsync(module.plugin) {
            val profile = runBlocking {
                module.manager.updateSocialNotificationSetting(uid, type, !module.manager.canReceiveSocialNoticeSync(uid, type))
            }
            proxyGateway?.invalidateProfile(uid)
            CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                player.playAudio(if (isEnabled(profile, type)) "notify-enabled" else "notify-disabled")
                onRender()
            }
        }
    }

    override fun viewReplacements(): Map<String, String> {
        val profile = module.manager.getProfileStoredSync(player.uid)
        return mapOf(
            "%state_status_like%" to state(profile.notifyStatusLike),
            "%state_status_comment%" to state(profile.notifyStatusComment),
            "%state_wall_post%" to state(profile.notifyWallPost),
            "%state_wall_like%" to state(profile.notifyWallLike),
            "%state_wall_comment%" to state(profile.notifyWallComment)
        )
    }

    private fun bindDynamicSlots() {
        if (dynamicSlots.isNotEmpty()) return
        layoutActions.toMap().forEach { (slot, binding) ->
            if (binding.symbol in toggleSymbols.keys) {
                dynamicSlots[slot] = binding
                layoutActions.remove(slot)
            }
        }
    }

    private fun state(enabled: Boolean): String {
        return if (enabled) "开启" else "关闭"
    }

    private fun isEnabled(profile: org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileData, type: SocialInteractionNoticeType): Boolean {
        return when (type) {
            SocialInteractionNoticeType.STATUS_LIKE -> profile.notifyStatusLike
            SocialInteractionNoticeType.STATUS_COMMENT -> profile.notifyStatusComment
            SocialInteractionNoticeType.WALL_POST -> profile.notifyWallPost
            SocialInteractionNoticeType.WALL_LIKE -> profile.notifyWallLike
            SocialInteractionNoticeType.WALL_COMMENT -> profile.notifyWallComment
        }
    }
}
