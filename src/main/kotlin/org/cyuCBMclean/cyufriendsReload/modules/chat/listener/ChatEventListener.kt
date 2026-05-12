package org.cyuCBMclean.cyufriendsReload.modules.chat.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.ui.input.PendingTextInput
import org.cyuCBMclean.cyufriendsReload.ui.input.TextInputTakeResult

class ChatEventListener(
    private val plugin: CyufriendsReload
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        when (val result = PendingTextInput.take(player.uniqueId)) {
            is TextInputTakeResult.Active -> {
                event.isCancelled = true
                val content = event.message.trim()

                if (content.isEmpty() || content.equals("cancel", true) || content.equals("取消", true)) {
                    DebugLogger.debug(1) {
                        "聊天输入已取消: player=${player.name} template=${result.request.commandTemplate} chars=${content.length}"
                    }
                    CyuConcurrency.scheduler.runEntity(plugin, player) {
                        player.sendLang(result.request.cancelMessageKey)
                    }
                    return
                }

                val command = result.request.commandTemplate.replace("%input%", content)
                DebugLogger.debug(1) {
                    "聊天输入已提交: player=${player.name} template=${result.request.commandTemplate} chars=${content.length}"
                }
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    player.performCommand(command)
                }
            }

            is TextInputTakeResult.Expired -> {
                DebugLogger.debug(1) {
                    "聊天输入已过期: player=${player.name} template=${result.request.commandTemplate}"
                }
                CyuConcurrency.scheduler.runEntity(plugin, player) {
                    player.sendLang("text-input-expired")
                }
            }

            TextInputTakeResult.None -> return
        }
    }
}
