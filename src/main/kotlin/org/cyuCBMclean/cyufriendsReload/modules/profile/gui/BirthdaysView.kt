package org.cyuCBMclean.cyufriendsReload.modules.profile.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.profile.BirthdayReminderEntry
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView

class BirthdaysView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val plugin: CyufriendsReload,
    private val profileModule: ProfileModule,
    title: String
) : PaginatedView<BirthdayReminderEntry>(player, title, pattern, itemsMap, 'Y', 'P', 'N') {

    private var cachedEntries: List<BirthdayReminderEntry> = emptyList()

    override fun getSource(): List<BirthdayReminderEntry> {
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: return emptyList()
        val friendUids = friendModule.friendManager.getFriendEntriesStoredSync(player.uid)
            .mapTo(linkedSetOf(), org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData::friendUid)
        val entries = profileModule.manager.birthdayEntriesSync(friendUids)
        cachedEntries = entries
        return entries
    }

    override fun viewReplacements(): Map<String, String> {
        val today = cachedEntries.count { it.daysAhead == 0 }
        val upcoming = cachedEntries.count { it.daysAhead > 0 }
        return mapOf(
            "%birthday_today_count%" to today.toString(),
            "%birthday_upcoming_count%" to upcoming.toString(),
            "%birthday_entry_count%" to cachedEntries.size.toString(),
            "%birthday_summary_hint%" to "左键资料，右键联系，中键${middleActionName()}"
        )
    }

    override fun mapElement(element: BirthdayReminderEntry): ItemStack {
        val template = itemsMap['Y'] ?: return ItemStack(Material.PLAYER_HEAD)
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val rawName = CyuIdHook.getName(element.uid) ?: element.uid
        val displayName = friendModule?.friendManager?.getFriendDataStoredSync(player.uid, element.uid)?.noteName ?: rawName
        val replacements = mapOf(
            "%friend_name%" to displayName,
            "%raw_name%" to rawName,
            "%friend_uid%" to element.uid,
            "%birthday%" to element.birthday,
            "%birthday_state%" to if (element.daysAhead == 0) "今天生日" else "${element.daysAhead} 天后生日",
            "%days_ahead%" to element.daysAhead.toString(),
            "%online_scope%" to plugin.onlineScope(element.uid),
            "%server_name%" to plugin.onlineServerName(element.uid),
            "%birthday_middle_action%" to middleActionName()
        )
        val baseItem = template.render(player, replacements).clone()
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element.uid, player)
    }

    override fun onElementClick(element: BirthdayReminderEntry, clickType: CyuClickType) {
        val rawName = CyuIdHook.getName(element.uid) ?: element.uid
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
        val displayName = friendModule?.friendManager?.getFriendDataStoredSync(player.uid, element.uid)?.noteName ?: rawName
        when (clickType) {
            CyuClickType.LEFT -> player.performCommand("friend profile $rawName")
            CyuClickType.RIGHT -> player.performCommand("friend contact ${element.uid} $displayName")
            CyuClickType.MIDDLE -> {
                if (plugin.moduleManager.isEnabled("social")) {
                    player.performCommand("wall $rawName")
                } else {
                    player.performCommand("friend profile $rawName")
                }
            }
            else -> Unit
        }
    }

    private fun middleActionName(): String {
        return if (plugin.moduleManager.isEnabled("social")) "留言墙" else "资料"
    }
}
