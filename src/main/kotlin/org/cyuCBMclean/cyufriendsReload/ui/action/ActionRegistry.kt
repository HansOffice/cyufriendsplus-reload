package org.cyuCBMclean.cyufriendsReload.ui.action

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRequestNotes
import org.cyuCBMclean.cyufriendsReload.modules.profile.ProfileModule
import org.cyuCBMclean.cyufriendsReload.ui.input.PendingTextInput
import org.cyuCBMclean.cyufriendsReload.ui.input.TextInputRequest
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 这里只做点击分发，真正动作交给各自的 Action 处理
 */
object ActionRegistry {

    private val handlers = ConcurrentHashMap<String, (Player, String) -> Unit>()

    init {
        register("console") { _, payload -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload) }
        register("player") { player, payload -> player.performCommand(payload) }
        register("close") { player, _ -> player.closeInventory() }
        register("sound") { player, payload ->
            val sound = runCatching { Sound.valueOf(payload.trim().uppercase().replace('.', '_')) }.getOrNull() ?: return@register
            player.playSound(player.location, sound, 0.8f, 1.0f)
        }
        register("pm_input") { player, payload ->
            val target = payload.trim()
            if (target.isEmpty()) return@register
            beginInput(player, TextInputRequest("msg $target %input%", "pm-input-cancelled"), "pm-input-started", mapOf("target" to target))
        }
        register("note_input") { player, payload ->
            val target = payload.trim()
            if (target.isEmpty()) return@register
            beginInput(player, TextInputRequest("friend note $target %input%", "text-input-cancelled"), "note-input-started", mapOf("target" to target))
        }
        register("note_detail_input") { player, payload ->
            val target = payload.trim()
            if (target.isEmpty()) return@register
            beginInput(player, TextInputRequest("friend notedetail $target %input%", "text-input-cancelled"), "note-detail-input-started", mapOf("target" to target))
        }
        register("status_input") { player, _ ->
            beginInput(player, TextInputRequest("status publish %input%", "text-input-cancelled"), "status-input-started")
        }
        register("wall_input") { player, payload ->
            val target = payload.trim()
            if (target.isEmpty()) return@register
            beginInput(player, TextInputRequest("wall post $target %input%", "text-input-cancelled"), "wall-input-started", mapOf("target" to target))
        }
        register("status_comment_input") { player, payload ->
            val statusId = payload.trim()
            if (statusId.isEmpty()) return@register
            beginInput(
                player,
                TextInputRequest("status comment $statusId %input%", "text-input-cancelled"),
                "status-comment-input-started",
                mapOf("id" to statusId)
            )
        }
        register("wall_reply_input") { player, payload ->
            val wallId = payload.trim()
            if (wallId.isEmpty()) return@register
            beginInput(
                player,
                TextInputRequest("wall comment $wallId %input%", "text-input-cancelled"),
                "wall-comment-input-started",
                mapOf("id" to wallId)
            )
        }
        register("friend_request_note_input") { player, payload ->
            val target = payload.trim()
            if (target.isEmpty()) return@register
            beginInput(
                player,
                TextInputRequest("friend add $target %input%", "text-input-cancelled"),
                "friend-request-note-input-started",
                mapOf(
                    "target" to target,
                    "limit" to FriendRequestNotes.maxLength(CyufriendsReload.instance).toString()
                )
            )
        }
        register("friend_list_search_input") { player, _ ->
            beginInput(player, TextInputRequest("friend list search %input%", "text-input-cancelled"), "friend-list-search-input-started")
        }
        register("birthday_input") { player, _ ->
            beginInput(player, TextInputRequest("birthday %input%", "text-input-cancelled"), "birthday-input-started")
        }
        register("bio_input") { player, _ ->
            val limit = CyufriendsReload.instance.moduleManager
                .getModule<ProfileModule>("profile")
                ?.manager
                ?.bioMaxLength()
                ?.toString()
                ?: "64"
            beginInput(
                player,
                TextInputRequest("bio %input%", "text-input-cancelled"),
                "bio-input-started",
                mapOf("limit" to limit)
            )
        }
        register("tag_color_input") { player, payload ->
            val parts = payload.split("||", limit = 2)
            val target = parts.getOrNull(0)?.trim().orEmpty()
            val tag = parts.getOrNull(1)?.trim().orEmpty()
            if (target.isEmpty() || tag.isEmpty()) return@register
            beginInput(
                player,
                TextInputRequest("friend tagcolor $target %input% $tag", "text-input-cancelled"),
                "tag-color-input-started",
                mapOf("target" to target, "tag" to tag)
            )
        }

        register("next_page") { player, _ ->
            (player.openInventory.topInventory.holder as? PaginatedView<*>)?.nextPage()
        }
        register("prev_page") { player, _ ->
            (player.openInventory.topInventory.holder as? PaginatedView<*>)?.prevPage()
        }
    }

    fun register(id: String, handler: (Player, String) -> Unit) {
        handlers[id.lowercase()] = handler
        DebugLogger.debug(2) { "动作执行器已注册: ${id.lowercase()}" }
    }

    fun execute(player: Player, nodes: List<ActionNode>) {
        nodes.forEach { node ->
            if (node.chance < 100.0 && Random.nextDouble(100.0) >= node.chance) {
                DebugLogger.debug(2) { "动作已跳过(概率未命中): ${player.name} -> ${node.executorId}:${summarize(node.payload)}" }
                return@forEach
            }

            val handler = handlers[node.executorId]
            if (handler == null) {
                DebugLogger.warning("未找到动作执行器: ${node.executorId}，玩家=${player.name}，payload=${summarize(node.payload)}")
                return@forEach
            }

            if (node.delayTicks > 0) {
                DebugLogger.debug(2) {
                    "动作延迟执行: ${player.name} -> ${node.executorId}:${summarize(node.payload)} | delay=${node.delayTicks}t"
                }
                val location = player.location.clone()
                CyuConcurrency.scheduler.runLaterAsync(CyufriendsReload.instance, node.delayTicks) {
                    CyuConcurrency.scheduler.runRegion(CyufriendsReload.instance, location) {
                        if (player.isOnline) {
                            DebugLogger.debug(2) {
                                "动作开始执行: ${player.name} -> ${node.executorId}:${summarize(node.payload)}"
                            }
                            handler(player, node.payload)
                        }
                    }
                }
            } else {
                DebugLogger.debug(2) {
                    "动作开始执行: ${player.name} -> ${node.executorId}:${summarize(node.payload)}"
                }
                handler(player, node.payload)
            }
        }
    }

    private fun beginInput(
        player: Player,
        request: TextInputRequest,
        startedMessageKey: String,
        placeholders: Map<String, String> = emptyMap()
    ) {
        val timeoutSeconds = CyufriendsReload.instance.config
            .getLong("settings.text-input-timeout-seconds", 90L)
            .coerceAtLeast(5L)
        PendingTextInput.put(
            player.uniqueId,
            request.copy(expireAtMillis = System.currentTimeMillis() + timeoutSeconds * 1000L)
        )
        DebugLogger.debug(1) {
            "聊天输入模式开启: ${player.name} -> ${request.commandTemplate} | timeout=${timeoutSeconds}s"
        }
        player.closeInventory()
        player.sendLang(startedMessageKey, placeholders)
    }

    private fun summarize(payload: String, limit: Int = 72): String {
        val compact = payload.replace('\n', ' ').trim()
        return if (compact.length <= limit) compact else compact.take(limit) + "..."
    }
}
