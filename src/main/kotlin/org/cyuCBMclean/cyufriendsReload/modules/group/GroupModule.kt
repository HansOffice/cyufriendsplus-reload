package org.cyuCBMclean.cyufriendsReload.modules.group

import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.module.CyuModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule

class GroupModule(val plugin: CyufriendsReload) : CyuModule {

    override val moduleId = "group"
    override val requiredModules = setOf("friend")

    lateinit var manager: GroupManager
        private set

    val friendModule: FriendModule?
        get() = plugin.moduleManager.getModule("friend")

    override fun onEnable() {
        val friendModule = plugin.moduleManager.getModule<FriendModule>("friend")
            ?: error("Friend module must be enabled before group module")
        manager = GroupManager(friendModule.friendManager)
    }

    override fun onDisable() {}

    override fun reloadConfig() {}
}
