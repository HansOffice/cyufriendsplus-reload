package org.cyuCBMclean.cyufriendsReload.api.event

import org.bukkit.Bukkit
import org.bukkit.event.Event

abstract class CyuFriendsEvent : Event(!Bukkit.isPrimaryThread())
