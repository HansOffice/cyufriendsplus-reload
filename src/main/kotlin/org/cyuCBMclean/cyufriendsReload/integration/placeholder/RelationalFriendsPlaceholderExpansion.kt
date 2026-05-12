package org.cyuCBMclean.cyufriendsReload.integration.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.clip.placeholderapi.expansion.Relational
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule

class RelationalFriendsPlaceholderExpansion(
    private val plugin: CyufriendsReload,
    private val placeholderId: String = "rel_cyufriends"
) : PlaceholderExpansion(), Relational {

    override fun getIdentifier(): String = placeholderId

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, target: Player?, params: String): String {
        if (player == null || target == null) return ""

        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend") ?: return ""
        val uid1 = player.uid
        val uid2 = target.uid

        return when (params.lowercase()) {
            "is_friend" -> friendModule.friendManager.isFriendStable(uid1, uid2).toString()
            "is_friend_bool" -> friendModule.friendManager.isFriendStable(uid1, uid2).toString()
            else -> ""
        }
    }
}
