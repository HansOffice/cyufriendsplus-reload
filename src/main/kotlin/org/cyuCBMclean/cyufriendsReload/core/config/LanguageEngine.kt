package org.cyuCBMclean.cyufriendsReload.core.config

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

class LanguageEngine(private val plugin: Plugin) {

    lateinit var audiences: BukkitAudiences
        private set

    private val miniMessage = MiniMessage.miniMessage()
    private val messageCache = mutableMapOf<String, String>()
    private val missingKeysWarned = mutableSetOf<String>()

    fun initialize() {
        audiences = BukkitAudiences.create(plugin)
        reload()
    }

    fun reload() {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false)
        }

        val yaml = YamlConfiguration().apply { load(file) }
        val nextMessages = yaml.getKeys(true)
            .filter { yaml.isString(it) }
            .associateWith { yaml.getString(it)!! }
        require(nextMessages.isNotEmpty()) { "messages.yml 没有可用语言键" }

        messageCache.clear()
        messageCache.putAll(nextMessages)
        missingKeysWarned.clear()
    }

    fun send(sender: CommandSender, key: String, vararg placeholders: TagResolver) {
        val raw = messageCache[key] ?: run {
            if (missingKeysWarned.add(key)) {
                plugin.logger.warning("messages.yml 缺少语言键: $key")
            }
            return
        }
        if (raw.isBlank()) return

        val prefix = messageCache["prefix"] ?: ""
        sendComponent(sender, deserializeSafely(prefix + raw, *placeholders))
    }

    fun sendRaw(sender: CommandSender, raw: String, vararg placeholders: TagResolver) {
        if (raw.isBlank()) return
        sendComponent(sender, deserializeSafely(raw, *placeholders))
    }

    fun component(key: String, placeholders: Map<String, String> = emptyMap(), includePrefix: Boolean = false): Component? {
        val raw = messageCache[key] ?: run {
            if (missingKeysWarned.add(key)) {
                plugin.logger.warning("messages.yml 缺少语言键: $key")
            }
            return null
        }
        if (raw.isBlank()) return null
        val content = if (includePrefix) (messageCache["prefix"] ?: "") + raw else raw
        return deserializeSafely(content, *toResolvers(placeholders))
    }

    private fun toResolvers(placeholders: Map<String, String>): Array<TagResolver> {
        return placeholders.map { Placeholder.unparsed(it.key, it.value) }.toTypedArray()
    }

    private fun deserializeSafely(raw: String, vararg placeholders: TagResolver): Component {
        return runCatching {
            miniMessage.deserialize(raw, *placeholders)
        }.getOrElse { exception ->
            plugin.logger.warning("无法解析 messages.yml 中的 MiniMessage 文本：$raw")
            plugin.logger.warning("原因：${exception.message}")
            Component.text(raw)
        }
    }

    private fun sendComponent(sender: CommandSender, component: Component) {
        if (ColorCompat.rgbSupported) {
            audiences.sender(sender).sendMessage(component)
        } else {
            sender.sendMessage(ColorCompat.serialize(component))
        }
    }

    fun shutdown() {
        if (::audiences.isInitialized) {
            audiences.close()
        }
    }
}
