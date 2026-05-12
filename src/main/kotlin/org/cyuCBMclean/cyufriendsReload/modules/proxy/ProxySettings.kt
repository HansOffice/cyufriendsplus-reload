package org.cyuCBMclean.cyufriendsReload.modules.proxy

import org.bukkit.configuration.file.FileConfiguration

data class ProxySettings(
    val enabled: Boolean,
    val serverId: String,
    val channel: String,
    val protocolVersion: Int,
    val secret: String,
    val maxClockSkewSeconds: Long,
    val directMessageTimeoutSeconds: Long,
    val teleportPrecheckTimeoutSeconds: Long
) {
    companion object {
        const val DEFAULT_SECRET = "change-this-secret"

        fun from(config: FileConfiguration): ProxySettings {
            return ProxySettings(
                enabled = config.getBoolean("proxy.enabled", false),
                serverId = config.getString("proxy.server-id", "server")!!.trim().ifBlank { "server" },
                channel = config.getString("proxy.channel", "cyufriends:gateway")!!.trim().ifBlank { "cyufriends:gateway" },
                protocolVersion = config.getInt("proxy.protocol-version", 1).coerceAtLeast(1),
                secret = config.getString("proxy.secret", DEFAULT_SECRET)!!.trim(),
                maxClockSkewSeconds = config.getLong("proxy.max-clock-skew-seconds", 15L).coerceAtLeast(1L),
                directMessageTimeoutSeconds = config.getLong("proxy.direct-message-timeout-seconds", 8L).coerceAtLeast(1L),
                teleportPrecheckTimeoutSeconds = config.getLong("proxy.teleport-precheck-timeout-seconds", 6L).coerceAtLeast(1L)
            )
        }
    }

    fun hasSecureSecret(): Boolean {
        return secret.isNotBlank() && !secret.equals(DEFAULT_SECRET, ignoreCase = true)
    }
}
