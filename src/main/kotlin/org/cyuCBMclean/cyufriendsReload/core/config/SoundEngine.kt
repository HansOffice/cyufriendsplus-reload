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

        val yaml = YamlConfiguration.loadConfiguration(file)
        soundCache.clear()

        yaml.getKeys(false).forEach { key ->
            val soundName = yaml.getString("$key.sound")?.uppercase() ?: return@forEach
            val volume = yaml.getDouble("$key.volume", 1.0).toFloat()
            val pitch = yaml.getDouble("$key.pitch", 1.0).toFloat()

            runCatching {
                soundCache[key] = SoundData(Sound.valueOf(soundName), volume, pitch)
            }.onFailure {
                plugin.logger.warning("Invalid sound enum detected in sounds.yml at key: $key")
            }
        }
    }

    fun play(player: Player, key: String) {
        val data = soundCache[key] ?: return
        player.playSound(player.location, data.sound, data.volume, data.pitch)
    }
}