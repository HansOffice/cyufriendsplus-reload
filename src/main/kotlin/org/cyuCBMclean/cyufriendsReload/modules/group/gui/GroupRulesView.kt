package org.cyuCBMclean.cyufriendsReload.modules.group.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendPersonalType
import org.cyuCBMclean.cyufriendsReload.modules.group.GroupModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.CyuView

class GroupRulesView(
    player: Player,
    pattern: GuiPattern,
    itemsMap: Map<Char, ItemTemplate>,
    private val groupModule: GroupModule,
    private val friendModule: FriendModule,
    private val groupName: String,
    title: String
) : CyuView(player, title, pattern, itemsMap) {

    private val dynamicSlots = mutableMapOf<Int, LayoutBinding>()
    private val toggleSymbols = setOf('T', 'N', 'O', 'P')

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
        val proxyGateway = groupModule.plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        val ownerUid = player.uid
        CyuConcurrency.scheduler.runAsync(groupModule.plugin) {
            when (binding.symbol) {
                'T' -> runBlocking { friendModule.preferencesManager.toggleGroup(ownerUid, groupName, FriendPersonalType.TELEPORT) }
                'N' -> runBlocking { friendModule.preferencesManager.toggleGroup(ownerUid, groupName, FriendPersonalType.NOTIFY_RECEIVE) }
                'O' -> runBlocking { friendModule.preferencesManager.toggleGroup(ownerUid, groupName, FriendPersonalType.NOTIFY_BROADCAST) }
                'P' -> runBlocking { friendModule.preferencesManager.toggleGroupPinned(ownerUid, groupName) }
            }
            proxyGateway?.invalidateSettings(ownerUid)
            CyuConcurrency.scheduler.runEntity(groupModule.plugin, player) {
                player.playAudio("success")
                onRender()
            }
        }
    }

    override fun viewReplacements(): Map<String, String> {
        val settings = friendModule.preferencesManager.snapshotGroupStoredSync(player.uid, groupName)
        val memberCount = groupModule.manager.friendsInGroup(player.uid, groupName).size
        return mapOf(
            "%group_name%" to groupName,
            "%group_count%" to memberCount.toString(),
            "%state_tp%" to settings.teleport.displayName(FriendPersonalType.TELEPORT),
            "%state_notify%" to settings.notifyReceive.displayName(FriendPersonalType.NOTIFY_RECEIVE),
            "%state_notifyme%" to settings.notifyBroadcast.displayName(FriendPersonalType.NOTIFY_BROADCAST),
            "%state_pin%" to if (settings.pinned) "置顶显示" else "普通显示"
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
}
