package org.cyuCBMclean.cyufriendsReload.extension

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.RemotePresence

fun CommandSender.sendLang(key: String, placeholders: Map<String, String> = emptyMap()) {
    DebugLogger.debug(2) {
        val details = if (placeholders.isEmpty()) "-" else placeholders.entries.joinToString(", ") { "${it.key}=${it.value}" }
        "语言消息: to=${name} key=$key placeholders=$details"
    }
    val resolvers = placeholders.map { Placeholder.unparsed(it.key, it.value) }.toTypedArray()
    CyufriendsReload.instance.langEngine.send(this, key, *resolvers)
}

fun Player.playAudio(key: String) {
    CyufriendsReload.instance.soundEngine.play(this, key)
}

val Player.uid: String
    get() = CyuIdHook.getUid(this)

fun CyufriendsReload.proxyModule(): ProxyModule? {
    return runCatching { moduleManager.getModule<ProxyModule>("proxy") }.getOrNull()
}

fun CyufriendsReload.remotePresence(uid: String): RemotePresence? {
    return proxyModule()?.remotePresence?.find(uid)
}

fun CyufriendsReload.isPlayerOnlineGlobally(uid: String): Boolean {
    return CyuIdHook.isOnlineLocally(uid) || remotePresence(uid) != null
}

fun CyufriendsReload.onlineCountGlobally(uids: Iterable<String>): Int {
    return uids.count(::isPlayerOnlineGlobally)
}

data class GlobalOnlineEntry(
    val uid: String,
    val name: String,
    val serverId: String?,
    val remote: Boolean
)

fun CyufriendsReload.globalOnlineEntries(): List<GlobalOnlineEntry> {
    val localServerId = proxyModule()?.settings?.serverId ?: "local"
    val local = CyuIdHook.onlineEntriesSnapshot().map { (uid, name) ->
        GlobalOnlineEntry(uid = uid, name = name, serverId = localServerId, remote = false)
    }
    val localUids = local.mapTo(hashSetOf()) { it.uid }
    val remote = proxyModule()
        ?.remotePresence
        ?.all()
        ?.asSequence()
        ?.filter { it.uid !in localUids }
        ?.map { GlobalOnlineEntry(uid = it.uid, name = it.name, serverId = it.serverId, remote = true) }
        ?.toList()
        ?: emptyList()
    return local + remote
}

fun CyufriendsReload.globalOnlineCount(): Int {
    return globalOnlineEntries().size
}

fun CyufriendsReload.onlineServerId(uid: String): String? {
    return if (CyuIdHook.isOnlineLocally(uid)) {
        proxyModule()?.settings?.serverId ?: "local"
    } else {
        remotePresence(uid)?.serverId
    }
}

fun CyufriendsReload.displayServerName(serverId: String?): String {
    val clean = serverId?.trim().orEmpty()
    if (clean.isEmpty()) return "未知服务器"
    val mapped = config.getString("proxy.server-names.$clean")?.trim().orEmpty()
    return mapped.ifEmpty { clean }
}

fun CyufriendsReload.onlineServerName(uid: String): String {
    return displayServerName(onlineServerId(uid))
}

fun CyufriendsReload.onlineScope(uid: String): String {
    if (CyuIdHook.isOnlineLocally(uid)) return "本服在线"
    if (remotePresence(uid) != null) return "跨服在线"
    return "离线"
}

fun CyufriendsReload.isRemoteOnline(uid: String): Boolean {
    return !CyuIdHook.isOnlineLocally(uid) && remotePresence(uid) != null
}

fun CyufriendsReload.resolvePlayerName(uid: String): String? {
    return CyuIdHook.getOnlineName(uid)
        ?: remotePresence(uid)?.name
        ?: CyuIdHook.getName(uid)
}
