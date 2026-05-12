package org.cyuCBMclean.cyufriendsReload.modules.friend

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.ClickEvent as BungeeClickEvent
import net.md_5.bungee.api.chat.HoverEvent as BungeeHoverEvent
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.config.ColorCompat
import org.cyuCBMclean.cyufriendsReload.modules.chat.ChatConversationSummary
import org.cyuCBMclean.cyufriendsReload.modules.social.PendingWallReplyEntry
import org.cyuCBMclean.cyufriendsReload.modules.social.WallEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.cyuCBMclean.cyufriendsReload.extension.sendLang

object FriendRichMessages {

    private val lineTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private fun message(key: String, placeholders: Map<String, String> = emptyMap()): Component {
        return CyufriendsReload.instance.langEngine.component(key, placeholders) ?: Component.empty()
    }

    private fun legacyMessage(key: String, placeholders: Map<String, String> = emptyMap()): String {
        return ColorCompat.serialize(message(key, placeholders))
    }

    private fun clickable(
        buttonKey: String,
        hoverKey: String,
        command: String,
        placeholders: Map<String, String> = emptyMap()
    ): Component {
        return message(buttonKey, placeholders)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(message(hoverKey, placeholders)))
    }

    private fun spigotClickable(
        buttonKey: String,
        hoverKey: String,
        command: String,
        placeholders: Map<String, String> = emptyMap()
    ): Array<BaseComponent> {
        val button = TextComponent.fromLegacyText(legacyMessage(buttonKey, placeholders))
        val hover = TextComponent.fromLegacyText(legacyMessage(hoverKey, placeholders))
        val clickEvent = BungeeClickEvent(BungeeClickEvent.Action.RUN_COMMAND, command)
        val hoverEvent = BungeeHoverEvent(BungeeHoverEvent.Action.SHOW_TEXT, hover)
        button.forEach {
            it.clickEvent = clickEvent
            it.hoverEvent = hoverEvent
        }
        return button
    }

    private fun append(target: MutableList<BaseComponent>, components: Array<BaseComponent>) {
        components.forEach(target::add)
    }

    private fun preview(content: String, limit: Int = 24): String {
        val clean = content.trim()
        if (clean.isEmpty()) return "暂无内容"
        return if (clean.length <= limit) clean else clean.take(limit) + "..."
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "未知时间"
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(lineTimeFormatter)
    }

    fun sendHelpPager(player: Player, currentPage: Int, totalPages: Int) {
        val previousPage = if (currentPage <= 1) totalPages else currentPage - 1
        val nextPage = if (currentPage >= totalPages) 1 else currentPage + 1
        val line = mutableListOf<BaseComponent>()
        append(line, spigotClickable("help-json-button-prev", "help-json-hover-prev", "/friend help $previousPage"))
        append(line, TextComponent.fromLegacyText(" "))
        append(
            line,
            TextComponent.fromLegacyText(
                legacyMessage(
                    "help-json-page-info",
                    mapOf("current_page" to currentPage.toString(), "total_pages" to totalPages.toString())
                )
            )
        )
        append(line, TextComponent.fromLegacyText(" "))
        append(line, spigotClickable("help-json-button-next", "help-json-hover-next", "/friend help $nextPage"))
        player.spigot().sendMessage(*line.toTypedArray())
    }

    fun sendFriendRequestPrompt(player: Player, requesterName: String, requesterUid: String, note: String? = null) {
        if (!note.isNullOrBlank()) {
            val preview = FriendRequestNotes.preview(CyufriendsReload.instance, note)
            player.sendLang("request-note-line", mapOf("note" to preview))
        }
        val placeholders = mapOf("requester_name" to requesterName)
        val line = Component.empty()
            .append(message("friend-request-json-prefix", placeholders))
            .append(
                clickable(
                    "friend-request-json-button-accept",
                    "friend-request-json-hover-accept",
                    "/friend accept $requesterUid",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "friend-request-json-button-deny",
                    "friend-request-json-hover-deny",
                    "/friend deny $requesterUid",
                    placeholders
                )
            )
        CyufriendsReload.instance.langEngine.audiences.player(player).sendMessage(line)
    }

    fun sendTeleportRequestPrompt(player: Player, requesterName: String, timeoutSeconds: Long) {
        val placeholders = mapOf(
            "requester_name" to requesterName,
            "timeout_seconds" to timeoutSeconds.toString()
        )
        val line = Component.empty()
            .append(message("teleport-request-json-prefix", placeholders))
            .append(message("teleport-request-json-timeout", placeholders))
            .append(
                clickable(
                    "teleport-request-json-button-accept",
                    "teleport-request-json-hover-accept",
                    "/friend tpaccept",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "teleport-request-json-button-deny",
                    "teleport-request-json-hover-deny",
                    "/friend tpdeny",
                    placeholders
                )
            )
        CyufriendsReload.instance.langEngine.audiences.player(player).sendMessage(line)
    }

    fun sendRequestEntry(player: Player, requesterName: String, requesterUid: String, createdAt: Long, notePreview: String) {
        val placeholders = mapOf(
            "requester_name" to requesterName,
            "time" to formatTime(createdAt),
            "note_preview" to notePreview
        )
        val line = Component.empty()
            .append(message("request-list-json-entry", placeholders))
            .append(Component.text(" "))
            .append(
                clickable(
                    "request-list-json-button-accept",
                    "request-list-json-hover-accept",
                    "/friend accept $requesterUid",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "request-list-json-button-deny",
                    "request-list-json-hover-deny",
                    "/friend deny $requesterUid",
                    placeholders
                )
            )
        CyufriendsReload.instance.langEngine.audiences.player(player).sendMessage(line)
    }

    fun sendSentRequestEntry(player: Player, targetName: String, targetUid: String, createdAt: Long, notePreview: String) {
        val placeholders = mapOf(
            "target_name" to targetName,
            "time" to formatTime(createdAt),
            "note_preview" to notePreview
        )
        val line = Component.empty()
            .append(message("sent-request-json-entry", placeholders))
            .append(Component.text(" "))
            .append(
                clickable(
                    "sent-request-json-button-revoke",
                    "sent-request-json-hover-revoke",
                    "/friend revoke $targetUid",
                    placeholders
                )
            )
        CyufriendsReload.instance.langEngine.audiences.player(player).sendMessage(line)
    }

    fun sendConversationEntry(player: Player, summary: ChatConversationSummary, partnerName: String) {
        val placeholders = mapOf(
            "partner_name" to partnerName,
            "time" to formatTime(summary.latestAt),
            "unread_amount" to summary.unreadCount.toString(),
            "latest_preview" to preview(summary.latestContent, 30)
        )
        val line = Component.empty()
            .append(message("messages-json-entry", placeholders))
            .append(Component.text(" "))
            .append(
                clickable(
                    "messages-json-button-open",
                    "messages-json-hover-open",
                    "/friend chat ${summary.partnerUid}",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "messages-json-button-read",
                    "messages-json-hover-read",
                    "/messages read ${summary.partnerUid}",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "messages-json-button-profile",
                    "messages-json-hover-profile",
                    "/friend profiledetail ${summary.partnerUid}",
                    placeholders
                )
            )
        CyufriendsReload.instance.langEngine.audiences.player(player).sendMessage(line)
    }

    fun sendPendingWallEntry(player: Player, entry: WallEntry, authorName: String) {
        val placeholders = mapOf(
            "wall_id" to entry.id.toString(),
            "author_name" to authorName,
            "time" to formatTime(entry.timestamp),
            "visibility" to entry.visibility.displayName,
            "content_preview" to preview(entry.content)
        )
        val line = Component.empty()
            .append(message("wall-pending-json-entry", placeholders))
            .append(Component.text(" "))
            .append(
                clickable(
                    "wall-pending-json-button-approve",
                    "wall-pending-json-hover-approve",
                    "/wall approve ${entry.id}",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "wall-pending-json-button-reject",
                    "wall-pending-json-hover-reject",
                    "/wall reject ${entry.id}",
                    placeholders
                )
            )
        CyufriendsReload.instance.langEngine.audiences.player(player).sendMessage(line)
    }

    fun sendPendingReplyEntry(player: Player, entry: PendingWallReplyEntry, authorName: String) {
        val placeholders = mapOf(
            "reply_id" to entry.id.toString(),
            "wall_id" to entry.wallId.toString(),
            "author_name" to authorName,
            "time" to formatTime(entry.timestamp),
            "content_preview" to preview(entry.content)
        )
        val line = Component.empty()
            .append(message("wall-comment-pending-json-entry", placeholders))
            .append(Component.text(" "))
            .append(
                clickable(
                    "wall-comment-pending-json-button-approve",
                    "wall-comment-pending-json-hover-approve",
                    "/wall commentapprove ${entry.id}",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "wall-comment-pending-json-button-reject",
                    "wall-comment-pending-json-hover-reject",
                    "/wall commentreject ${entry.id}",
                    placeholders
                )
            )
            .append(Component.text(" "))
            .append(
                clickable(
                    "wall-comment-pending-json-button-view",
                    "wall-comment-pending-json-hover-view",
                    "/wall comments ${entry.wallId}",
                    placeholders
                )
            )
        CyufriendsReload.instance.langEngine.audiences.player(player).sendMessage(line)
    }
}
