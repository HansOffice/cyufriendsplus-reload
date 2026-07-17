package org.cyuCBMclean.cyufriendsReload.modules.group.gui

import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendGroupPreferences
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
    private lateinit var cachedSettings: FriendGroupPreferences

    override suspend fun prepareData() {
        friendModule.preferencesManager.loadPlayer(player.uid)
        cachedSettings = friendModule.preferencesManager.snapshotGroupCached(player.uid, groupName)
    }

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
        runAsyncOperation(
            operation = {
                when (binding.symbol) {
                    'T' -> friendModule.preferencesManager.toggleGroup(ownerUid, groupName, FriendPersonalType.TELEPORT)
                    'N' -> friendModule.preferencesManager.toggleGroup(ownerUid, groupName, FriendPersonalType.NOTIFY_RECEIVE)
                    'O' -> friendModule.preferencesManager.toggleGroup(ownerUid, groupName, FriendPersonalType.NOTIFY_BROADCAST)
                    'P' -> friendModule.preferencesManager.toggleGroupPinned(ownerUid, groupName)
                }
                friendModule.preferencesManager.snapshotGroupCached(ownerUid, groupName)
            },
            onSuccess = {
                cachedSettings = it
                proxyGateway?.invalidateSettings(ownerUid)
                player.playAudio("success")
                refreshOpenView()
            }
        )
    }

    override fun viewReplacements(): Map<String, String> {
        val memberCount = groupModule.manager.friendsInGroup(player.uid, groupName).size
        return mapOf(
            "%group_name%" to groupName,
            "%group_count%" to memberCount.toString(),
            "%state_tp%" to cachedSettings.teleport.displayName(FriendPersonalType.TELEPORT),
            "%state_notify%" to cachedSettings.notifyReceive.displayName(FriendPersonalType.NOTIFY_RECEIVE),
            "%state_notifyme%" to cachedSettings.notifyBroadcast.displayName(FriendPersonalType.NOTIFY_BROADCAST),
            "%state_pin%" to if (cachedSettings.pinned) "置顶显示" else "普通显示"
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
