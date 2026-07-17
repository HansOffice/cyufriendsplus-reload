package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPersonalPreferences
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPersonalType
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class FriendProfileSocialView(
    player: Player,
    pattern: GuiPattern,
    itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val targetName: String,
    title: String
) : CyuView(player, title, pattern, itemsMap) {

    private val dynamicSlots = mutableMapOf<Int, LayoutBinding>()
    private val toggleSymbols = mapOf(
        'L' to FriendPersonalType.STATUS_LIKE_NOTICE,
        'C' to FriendPersonalType.STATUS_COMMENT_NOTICE,
        'W' to FriendPersonalType.WALL_POST_NOTICE,
        'K' to FriendPersonalType.WALL_LIKE_NOTICE,
        'R' to FriendPersonalType.WALL_COMMENT_NOTICE
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
        val targetUid = CyuIdHook.getUidByName(targetName) ?: return
        val proxyGateway = module.plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        val ownerUid = player.uid
        CyuConcurrency.scheduler.runAsync(module.plugin) {
            runBlocking { module.preferencesManager.togglePersonal(ownerUid, targetUid, type) }
            proxyGateway?.invalidateSettings(ownerUid)
            CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                player.playAudio("success")
                onRender()
            }
        }
    }

    override fun viewReplacements(): Map<String, String> {
        val targetUid = CyuIdHook.getUidByName(targetName) ?: return emptyMap()
        val rawName = CyuIdHook.getName(targetUid) ?: targetName
        val displayName = module.friendManager.getFriendDataCached(player.uid, targetUid)?.noteName ?: rawName
        val preferences = module.preferencesManager.snapshotPersonalCached(player.uid, targetUid)
        return mapOf(
            "%raw_name%" to rawName,
            "%friend_name%" to displayName,
            "%state_status_like%" to state(preferences, FriendPersonalType.STATUS_LIKE_NOTICE),
            "%state_status_comment%" to state(preferences, FriendPersonalType.STATUS_COMMENT_NOTICE),
            "%state_wall_post%" to state(preferences, FriendPersonalType.WALL_POST_NOTICE),
            "%state_wall_like%" to state(preferences, FriendPersonalType.WALL_LIKE_NOTICE),
            "%state_wall_comment%" to state(preferences, FriendPersonalType.WALL_COMMENT_NOTICE)
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

    private fun state(preferences: FriendPersonalPreferences, type: FriendPersonalType): String {
        return when (type) {
            FriendPersonalType.STATUS_LIKE_NOTICE -> preferences.statusLikeNotice.displayName(type)
            FriendPersonalType.STATUS_COMMENT_NOTICE -> preferences.statusCommentNotice.displayName(type)
            FriendPersonalType.WALL_POST_NOTICE -> preferences.wallPostNotice.displayName(type)
            FriendPersonalType.WALL_LIKE_NOTICE -> preferences.wallLikeNotice.displayName(type)
            FriendPersonalType.WALL_COMMENT_NOTICE -> preferences.wallCommentNotice.displayName(type)
            else -> preferences.teleport.displayName(FriendPersonalType.TELEPORT)
        }
    }
}
