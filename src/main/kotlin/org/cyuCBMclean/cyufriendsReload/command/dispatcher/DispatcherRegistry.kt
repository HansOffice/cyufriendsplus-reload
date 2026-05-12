package org.cyuCBMclean.cyufriendsReload.command.dispatcher

import java.util.concurrent.ConcurrentHashMap

object DispatcherRegistry {
    private val dispatchers = ConcurrentHashMap<String, DispatcherDescriptor>()

    fun register(descriptor: DispatcherDescriptor) {
        dispatchers[descriptor.rootName.lowercase()] = descriptor
    }

    fun all(): List<DispatcherDescriptor> {
        return dispatchers.values.sortedBy { it.rootName.lowercase() }
    }

    fun clear() {
        dispatchers.clear()
    }
}
