package org.cyuCBMclean.cyufriendsReload.core.module

interface CyuModule {
    val moduleId: String
    val requiredModules: Set<String>
        get() = emptySet()
    fun onEnable()
    fun onDisable()
    fun reloadConfig()
}
