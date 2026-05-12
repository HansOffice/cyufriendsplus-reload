package org.cyuCBMclean.cyufriendsReload.modules.chat.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatMessage
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiTextFormatter
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class MessageChatView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: ChatModule,
    private val friendName: String,
    title: String
) : PaginatedView<ChatMessage>(player, title, pattern, itemsMap, 'M', 'P', 'N') {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val targetUid = CyuIdHook.getUidByName(friendName)
    private val ownerUid = player.uid
    private val staticTemplates = mutableMapOf<Int, ItemTemplate>()

    @Volatile
    private var loading = false
    @Volatile
    private var readSyncing = false

    override fun getSource(): List<ChatMessage> {
        val uid = targetUid ?: return emptyList()
        val cached = module.manager.conversationCached(ownerUid, uid)
        if (cached.isNotEmpty()) return cached
        if (!loading) {
            loading = true
            CyuConcurrency.scheduler.runAsync(module.plugin) {
                runCatching { runBlocking { module.manager.getConversation(ownerUid, uid, 90) } }
                CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                    loading = false
                    onRender()
                }
            }
        }
        return emptyList()
    }

    override fun mapElement(element: ChatMessage): ItemStack {
        val template = itemsMap['M'] ?: return ItemStack(Material.PAPER)
        val senderName = CyuIdHook.getName(element.senderUid) ?: "未知玩家"
        val receiverName = CyuIdHook.getName(element.receiverUid) ?: "未知玩家"
        val direction = if (element.senderUid == ownerUid) "我 -> $receiverName" else "$senderName -> 我"
        val time = dateFormat.format(Date(element.timestamp))
        val replacements = mapOf(
            "%direction%" to direction,
            "%sender_name%" to senderName,
            "%receiver_name%" to receiverName,
            "%time%" to time
        )
        val item = template.render(player, replacements).clone()
        val meta = item.itemMeta ?: return item
        if (meta.hasLore()) {
            meta.lore = meta.lore?.map { it.replace("%content%", GuiTextFormatter.renderUserText(element.content)) }
        }
        item.itemMeta = meta
        return if (template.hasHeadSource()) item else GuiHeads.applyForUid(item, element.senderUid, player)
    }

    override fun onRender() {
        super.onRender()
        syncConversationReadState()
        val rawName = targetUid?.let { CyuIdHook.getName(it) } ?: friendName
        val serverName = targetUid?.let { CyufriendsReload.instance.onlineServerName(it) } ?: "未知服务器"
        val onlineScope = targetUid?.let { CyufriendsReload.instance.onlineScope(it) } ?: "离线"
        layoutActions.keys.toList().forEach { slot ->
            val template = layoutActions.remove(slot)?.template ?: return@forEach
            staticTemplates[slot] = template
            val replacements = mapOf(
                "%raw_name%" to rawName,
                "%friend_name%" to rawName,
                "%server_name%" to serverName,
                "%online_scope%" to onlineScope
            )
            val item = template.render(player, replacements)
            setItem(slot, item)
        }
    }

    override fun onElementClick(element: ChatMessage, clickType: CyuClickType) {}

    override fun onDynamicClick(slot: Int, clickType: CyuClickType) {
        val template = staticTemplates[slot]
        if (template == null) {
            super.onDynamicClick(slot, clickType)
            return
        }
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val rawName = targetUid?.let { CyuIdHook.getName(it) } ?: friendName
        val serverName = targetUid?.let { CyufriendsReload.instance.onlineServerName(it) } ?: "未知服务器"
        val onlineScope = targetUid?.let { CyufriendsReload.instance.onlineScope(it) } ?: "离线"
        val processed = nodes.map {
            it.copy(
                payload = it.payload
                    .replace("%raw_name%", rawName)
                    .replace("%friend_name%", rawName)
                    .replace("%server_name%", serverName)
                    .replace("%online_scope%", onlineScope)
            )
        }
        ActionRegistry.execute(player, processed)
    }

    private fun syncConversationReadState() {
        val uid = targetUid ?: return
        if (readSyncing) return
        readSyncing = true
        CyuConcurrency.scheduler.runAsync(module.plugin) {
            module.manager.clearUnreadFromSenderSync(ownerUid, uid)
            CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                readSyncing = false
            }
        }
    }
}
