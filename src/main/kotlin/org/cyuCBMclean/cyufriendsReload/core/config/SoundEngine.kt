package org.cyuCBMclean.cyufriendsReload.core.config

import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.io.File

class SoundEngine(private val plugin: Plugin) {

    private val soundCache = mutableMapOf<String, SoundData>()

    data class SoundData(val sound: Sound, val volume: Float, val pitch: Float)

    fun reload() {
        val file = File(plugin.dataFolder, "sounds.yml")
        if (!file.exists()) {
            plugin.saveResource("sounds.yml", false)
        }

        val yaml = YamlConfiguration().apply { load(file) }
        val nextSounds = mutableMapOf<String, SoundData>()
        yaml.getKeys(false).forEach { key ->
            val soundName = yaml.getString("$key.sound")?.uppercase() ?: return@forEach
            val volume = yaml.getDouble("$key.volume", 1.0).toFloat()
            val pitch = yaml.getDouble("$key.pitch", 1.0).toFloat()
            val sound = runCatching { Sound.valueOf(soundName) }.getOrElse {
                throw IllegalArgumentException("sounds.yml 的音效 $key 无效: $soundName", it)
            }
            nextSounds[key] = SoundData(sound, volume, pitch)
        }

        soundCache.clear()
        soundCache.putAll(nextSounds)
    }
    fun play(player: Player, key: String) {
        val data = soundCache[key] ?: return
        player.playSound(player.location, data.sound, data.volume, data.pitch)
    }
}