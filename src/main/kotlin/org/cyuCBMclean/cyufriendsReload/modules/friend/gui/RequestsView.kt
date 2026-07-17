package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRequestEntry
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRequestNotes
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RequestsView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    title: String = "Requests List"
) : PaginatedView<FriendRequestEntry>(player, title, pattern, itemsMap, 'R', 'P', 'N') {

    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private var cachedRequests: List<FriendRequestEntry> = emptyList()

    override suspend fun prepareData() {
        cachedRequests = module.requestManager.getRequestsFromDbForSync(player.uid)
    }

    override fun getSource(): List<FriendRequestEntry> = cachedRequests

    override fun viewReplacements(): Map<String, String> {
        return mapOf(
            "%page%" to page.toString(),
            "%total_pages%" to totalPagesFor(cachedRequests.size).toString(),
            "%entry_count%" to cachedRequests.size.toString(),
            "%pending_count%" to cachedRequests.size.toString()
        )
    }

    override fun mapElement(element: FriendRequestEntry): ItemStack {
        val template = itemsMap['R'] ?: return ItemStack(Material.PLAYER_HEAD)
        val senderName = CyuIdHook.getName(element.senderUid) ?: "未知玩家"
        val replacements = replacements(element, senderName)
        val baseItem = template.render(player, replacements).clone()
        val meta = baseItem.itemMeta
        if (meta != null && meta.hasLore()) {
            val preview = FriendRequestNotes.preview(CyufriendsReload.instance, element.note)
            meta.lore = meta.lore?.map {
                it.replace("%request_note%", GuiTextFormatter.renderUserText(entryNote(element)))
                    .replace("%request_note_preview%", GuiTextFormatter.renderUserText(preview))
            }
            baseItem.itemMeta = meta
        }
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element.senderUid, player)
    }

    override fun onElementClick(element: FriendRequestEntry, clickType: CyuClickType) {
        val senderName = CyuIdHook.getName(element.senderUid) ?: "未知玩家"
        val template = itemsMap['R'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return

        ActionRegistry.execute(player, nodes.map { node ->
            replacements(element, senderName).entries.fold(node) { current, entry ->
                current.copy(payload = current.payload.replace(entry.key, entry.value))
            }
        })
    }

    private fun replacements(entry: FriendRequestEntry, senderName: String): Map<String, String> {
        return mapOf(
            "%sender_name%" to senderName,
            "%sender_uid%" to entry.senderUid,
            "%request_time%" to timeFormat.format(Instant.ofEpochMilli(entry.createdAt))
        )
    }

    private fun entryNote(entry: FriendRequestEntry): String {
        return entry.note ?: "未填写"
    }
}
