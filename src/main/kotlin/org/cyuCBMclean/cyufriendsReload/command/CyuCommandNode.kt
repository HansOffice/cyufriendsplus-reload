package org.cyuCBMclean.cyufriendsReload.command

import org.bukkit.command.CommandSender
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger

class CyuCommandNode(val name: String) {

    var permission: String? = null
    var requirePlayer: Boolean = false

    var onNoPermission: ((CommandSender) -> Unit)? = null
    var onNotPlayer: ((CommandSender) -> Unit)? = null

    private var executeAction: (CommandContext.() -> Unit)? = null
    private var tabAction: (CommandContext.() -> List<String>)? = null
    private val subNodes = mutableMapOf<String, CyuCommandNode>()
    private val canonicalSubNodes = linkedMapOf<String, CyuCommandNode>()
    private val aliases = mutableListOf<String>()

    fun alias(vararg alias: String) {
        aliases.addAll(alias)
    }

    fun executes(action: CommandContext.() -> Unit) {
        executeAction = action
    }

    fun tabComplete(action: CommandContext.() -> List<String>) {
        tabAction = action
    }

    fun subCommand(name: String, builder: CyuCommandNode.() -> Unit) {
        val node = CyuCommandNode(name).apply(builder)
        canonicalSubNodes[name.lowercase()] = node
        subNodes[name.lowercase()] = node
        node.aliases.forEach { subNodes[it.lowercase()] = node }
    }

    fun describeSubCommands(): List<String> {
        return canonicalSubNodes.keys.sorted()
    }

    fun executeNode(context: CommandContext, path: String = "/$name"): Boolean {
        if (permission != null && !context.sender.hasPermission(permission!!)) {
            DebugLogger.debug(1) { "命令拒绝: ${context.senderLabel} 无权限执行 $path | need=$permission" }
            onNoPermission?.invoke(context.sender)
            return true
        }

        if (requirePlayer && !context.isPlayer) {
            DebugLogger.debug(1) { "命令拒绝: ${context.senderLabel} 不是玩家，无法执行 $path" }
            onNotPlayer?.invoke(context.sender)
            return true
        }

        if (context.args.isNotEmpty()) {
            val subArg = context.args[0].lowercase()
            val subNode = subNodes[subArg]
            if (subNode != null) {
                val subContext = CommandContext(context.sender, context.args.drop(1))
                return subNode.executeNode(subContext, "$path $subArg")
            }
        }

        if (executeAction == null && context.args.isNotEmpty()) {
            DebugLogger.debug(1) { "命令未命中: ${context.senderLabel} -> $path ${context.joinedArgs}" }
        }
        executeAction?.invoke(context) ?: return false
        return true
    }

    fun completeNode(context: CommandContext, path: String = "/$name"): List<String> {
        if (permission != null && !context.sender.hasPermission(permission!!)) {
            DebugLogger.debug(2) { "补全拒绝: ${context.senderLabel} 无权限查看 $path" }
            return emptyList()
        }

        if (context.args.size > 1) {
            val subArg = context.args[0].lowercase()
            val subNode = subNodes[subArg]
            if (subNode != null) {
                val subContext = CommandContext(context.sender, context.args.drop(1))
                return subNode.completeNode(subContext, "$path $subArg")
            }
            return emptyList()
        }

        if (context.args.size == 1) {
            val current = context.args[0].lowercase()
            val subCompletions = subNodes.keys.filter { it.startsWith(current) }
            val customCompletions = tabAction?.invoke(context)?.filter { it.lowercase().startsWith(current) } ?: emptyList()
            return subCompletions + customCompletions
        }

        return tabAction?.invoke(context) ?: emptyList()
    }
}
