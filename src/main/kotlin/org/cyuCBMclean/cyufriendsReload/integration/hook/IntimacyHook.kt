package org.cyuCBMclean.cyufriendsReload.integration.hook

import org.bukkit.Bukkit

object IntimacyHook {

    private val serviceClass: Class<*>? by lazy {
        runCatching { Class.forName("org.cyuCBMclean.cyufriendsIntimacy.api.CyuIntimacyService") }.getOrNull()
    }

    fun snapshot(firstUid: String, secondUid: String): IntimacySnapshot? {
        val service = service() ?: return null
        return runCatching {
            val raw = service.javaClass.getMethod("snapshot", String::class.java, String::class.java)
                .invoke(service, firstUid, secondUid)
                ?: return null
            IntimacySnapshot(
                points = raw.value<Int>("points") ?: 0,
                levelName = raw.value<String>("levelName") ?: "好友",
                levelColor = raw.value<String>("levelColor") ?: "&7",
                nextLevelName = raw.value<String>("nextLevelName"),
                nextLevelRemaining = raw.value<Int>("nextLevelRemaining") ?: 0,
                friendshipDays = raw.value<Int>("friendshipDays") ?: 0,
                rank = raw.value<Int>("rank")
            )
        }.getOrNull()
    }

    private fun service(): Any? {
        val clazz = serviceClass ?: return null
        return runCatching { Bukkit.getServicesManager().load(clazz) }.getOrNull()
    }

    private inline fun <reified T> Any.value(name: String): T? {
        return runCatching {
            val method = javaClass.methods.firstOrNull { it.name == "get${name.replaceFirstChar(Char::uppercaseChar)}" && it.parameterCount == 0 }
                ?: javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            method?.invoke(this) as? T
        }.getOrNull()
    }
}

data class IntimacySnapshot(
    val points: Int,
    val levelName: String,
    val levelColor: String,
    val nextLevelName: String?,
    val nextLevelRemaining: Int,
    val friendshipDays: Int,
    val rank: Int?
)
