package org.cyuCBMclean.cyufriendsReload.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandContext(
    val sender: CommandSender,
    val args: List<String>
) {
    val player: Player
        get() = sender as Player

    val isPlayer: Boolean
        get() = sender is Player

    val senderLabel: String
        get() = if (isPlayer) player.name else sender.name

    val joinedArgs: String
        get() = if (args.isEmpty()) "<empty>" else args.joinToString(" ")

    fun getArg(index: Int): String? = args.getOrNull(index)

    fun getArg(index: Int, default: String): String = args.getOrNull(index) ?: default
}
