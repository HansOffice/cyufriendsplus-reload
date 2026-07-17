package org.cyuCBMclean.cyufriendsReload.modules.chat.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatConversationSummary
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
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

class MessagesView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: ChatModule,
    title: String = "Unread Messages"
) : PaginatedView<ChatConversationSummary>(player, title, pattern, itemsMap, 'M', 'P', 'N') {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    private val ownerUid = player.uid
    private var cachedConversations: List<ChatConversationSummary> = emptyList()

    override suspend fun prepareData() {
        cachedConversations = module.manager.getConversationSummaries(ownerUid, 56)
    }

    override fun getSource(): List<ChatConversationSummary> = cachedConversations

    override fun viewReplacements(): Map<String, String> {
        val unreadTotal = cachedConversations.sumOf { it.unreadCount }
        return mapOf(
            "%conversation_count%" to cachedConversations.size.toString(),
            "%unread_count%" to unreadTotal.toString()
        )
    }

    override fun mapElement(element: ChatConversationSummary): ItemStack {
        val template = itemsMap['M'] ?: return ItemStack(Material.PAPER)
        val partnerName = CyuIdHook.getName(element.partnerUid) ?: "未知玩家"
        val timeString = dateFormat.format(Instant.ofEpochMilli(element.latestAt))
        val preview = preview(element.latestContent)
        val direction = if (element.latestSenderUid == ownerUid) "我发出的最后一句" else "对方发来的最后一句"
        val replacements = mapOf(
            "%sender_name%" to partnerName,
            "%sender_uid%" to element.partnerUid,
            "%time%" to timeString,
            "%unread_amount%" to element.unreadCount.toString(),
            "%direction%" to direction,
            "%online_scope%" to module.plugin.onlineScope(element.partnerUid),
            "%server_name%" to module.plugin.onlineServerName(element.partnerUid)
        )
        val baseItem = template.render(player, replacements).clone()
        val meta = baseItem.itemMeta ?: return baseItem

        if (meta.hasLore()) {
            meta.lore = meta.lore
                ?.map { line ->
                    line
                        .replace("%content%", GuiTextFormatter.renderUserText(preview))
                        .replace("%latest_preview%", GuiTextFormatter.renderUserText(preview))
                }
        }

        baseItem.itemMeta = meta
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element.partnerUid, player)
    }

    override fun onElementClick(element: ChatConversationSummary, clickType: CyuClickType) {
        val template = itemsMap['M'] ?: return
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return

        val partnerName = CyuIdHook.getName(element.partnerUid) ?: "未知玩家"
        if (clickType == CyuClickType.SHIFT_RIGHT) {
            runAsyncOperation(
                operation = {
                    module.manager.clearUnreadFromSenderSync(ownerUid, element.partnerUid)
                    module.manager.getConversationSummaries(ownerUid, 56)
                },
                onSuccess = {
                    cachedConversations = it
                    refreshOpenView()
                }
            )
            return
        }

        ActionRegistry.execute(player, nodes.map { node ->
            node.copy(
                payload = node.payload
                    .replace("%sender_name%", partnerName)
                    .replace("%sender_uid%", element.partnerUid)
            )
        })
    }

    private fun preview(content: String): String {
        val clean = content.trim()
        if (clean.isEmpty()) return "暂无内容"
        return if (clean.length <= 30) clean else clean.take(30) + "..."
    }
}
