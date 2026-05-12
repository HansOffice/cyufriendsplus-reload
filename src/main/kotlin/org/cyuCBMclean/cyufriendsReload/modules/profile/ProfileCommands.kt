package org.cyuCBMclean.cyufriendsReload.modules.profile

import kotlinx.coroutines.runBlocking
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.command.CommandDispatcher
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.modules.profile.gui.SettingsView
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiLoader
import org.cyuCBMclean.cyufriendsReload.ui.view.ViewTitles

object ProfileCommands {

    fun register(plugin: CyufriendsReload, module: ProfileModule) {

        CommandDispatcher(plugin, "settings") {
            requirePlayer = true
            permission = "cyufriends.command.settings"

            executes {
                val guiData = GuiLoader.load(plugin, "settings_panel.yml") ?: return@executes player.sendLang("gui-open-failed")
                val title = guiData.resolveTitle(player, ViewTitles.settings())
                SettingsView(player, guiData.pattern, guiData.items, module, title).open()
            }
        }.register()

        CommandDispatcher(plugin, "bio") {
            requirePlayer = true
            permission = "cyufriends.command.bio"
            executes {
                if (args.isEmpty()) return@executes player.sendLang("usage-bio")
                val content = module.manager.normalizeBio(args.joinToString(" "))
                if (content.isEmpty()) return@executes player.sendLang("usage-bio")
                val maxLength = module.manager.bioMaxLength()
                if (content.length > maxLength) {
                    return@executes player.sendLang("bio-too-long", mapOf("max" to maxLength.toString()))
                }
                val profile = module.manager.getProfileStoredSync(player.uid)
                profile.bio = content
                val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
                CyuConcurrency.scheduler.runAsync(plugin) {
                    runBlocking { module.manager.updateProfile(profile) }
                    proxyGateway?.invalidateProfile(profile.uid)
                    CyuConcurrency.scheduler.runEntity(plugin, player) {
                        player.sendLang("bio-updated")
                        player.playAudio("success")
                    }
                }
            }
        }.register()

        CommandDispatcher(plugin, "birthday") {
            requirePlayer = true
            permission = "cyufriends.command.birthday"

            executes {
                val birthday = getArg(0) ?: return@executes player.sendLang("usage-birthday")
                val uid = player.uid
                val limit = module.manager.birthdayLimit(player)
                val proxyGateway = plugin.moduleManager.getModule<ProxyModule>("proxy")?.gateway
                CyuConcurrency.scheduler.runAsync(plugin) {
                    val result = runBlocking { module.manager.setBirthday(uid, birthday, limit) }
                    if (result == BirthdaySetResult.SUCCESS) {
                        proxyGateway?.invalidateProfile(uid)
                    }
                    CyuConcurrency.scheduler.runEntity(plugin, player) {
                        when (result) {
                            BirthdaySetResult.SUCCESS -> {
                                player.sendLang("birthday-set-success", mapOf("birthday" to birthday))
                                player.playAudio("birthday-set-success")
                            }
                            BirthdaySetResult.INVALID_FORMAT -> {
                                player.sendLang("birthday-invalid-format")
                                player.playAudio("birthday-set-failed")
                            }
                            BirthdaySetResult.LIMIT_REACHED -> {
                                player.sendLang("birthday-limit-reached")
                                player.playAudio("birthday-set-failed")
                            }
                        }
                    }
                }
            }

            tabComplete {
                filterCompletions(listOf("2000-01-01"), args.getOrElse(0) { "" })
            }
        }.register()
    }

    private fun filterCompletions(values: Iterable<String>, prefix: String): List<String> {
        val normalized = prefix.lowercase()
        return values.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it.lowercase().startsWith(normalized) }
            .sortedBy { it.lowercase() }
            .take(30)
            .toList()
    }
}
