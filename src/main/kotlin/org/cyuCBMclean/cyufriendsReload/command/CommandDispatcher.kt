package org.cyuCBMclean.cyufriendsReload.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.cyuCBMclean.cyufriendsReload.command.dispatcher.DispatcherDescriptor
import org.cyuCBMclean.cyufriendsReload.command.dispatcher.DispatcherRegistry
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger

class CommandDispatcher(
    private val plugin: Plugin,
    private val rootName: String,
    builder: CyuCommandNode.() -> Unit
) : CommandExecutor, TabCompleter {

    private val rootNode = CyuCommandNode(rootName).apply(builder)

    fun register() {
        plugin.server.getPluginCommand(rootName)?.let {
            it.setExecutor(this)
            it.tabCompleter = this
            DispatcherRegistry.register(DispatcherDescriptor(rootName, rootNode.describeSubCommands()))
            DebugLogger.debug(1) { "命令根节点已注册: /$rootName" }
        } ?: plugin.logger.severe("Command '$rootName' not found in plugin.yml!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val context = CommandContext(sender, args.toList())
        val rawCommand = buildString {
            append('/').append(label)
            if (args.isNotEmpty()) append(' ').append(args.joinToString(" "))
        }
        val startedAt = System.nanoTime()
        DebugLogger.debug(0) { "命令执行: ${context.senderLabel} -> $rawCommand" }
        val result = rootNode.executeNode(context, "/$rootName")
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        DebugLogger.debug(1) { "命令结束: ${context.senderLabel} -> $rawCommand | result=$result | ${elapsedMs}ms" }
        return result
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val context = CommandContext(sender, args.toList())
        val completions = rootNode.completeNode(context, "/$rootName")
        DebugLogger.debug(2) {
            "Tab补全: ${context.senderLabel} -> /$alias ${context.joinedArgs} | count=${completions.size}"
        }
        return completions
    }
}
