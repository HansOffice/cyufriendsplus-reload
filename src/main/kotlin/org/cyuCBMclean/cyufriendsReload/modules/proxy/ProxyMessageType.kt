package org.cyuCBMclean.cyufriendsReload.modules.proxy

enum class ProxyMessageType(val id: String) {
    PRESENCE_JOIN("presence.join"),
    PRESENCE_QUIT("presence.quit"),
    PRESENCE_SNAPSHOT_REQUEST("presence.snapshot.request"),
    PRESENCE_SNAPSHOT_FULL("presence.snapshot.full"),
    IDENTITY_UID_CHANGED("identity.uid.changed"),
    IDENTITY_HEAD_UPDATED("identity.head.updated"),
    CACHE_INVALIDATE_RELATION("cache.invalidate.relation"),
    CACHE_INVALIDATE_PROFILE("cache.invalidate.profile"),
    CACHE_INVALIDATE_REQUEST("cache.invalidate.request"),
    CACHE_INVALIDATE_SETTINGS("cache.invalidate.settings"),
    CACHE_INVALIDATE_MESSAGE("cache.invalidate.message"),
    CACHE_INVALIDATE_STATUS("cache.invalidate.status"),
    CACHE_INVALIDATE_WALL("cache.invalidate.wall"),
    CHAT_DIRECT_DELIVER("chat.direct.deliver"),
    CHAT_DIRECT_ACK("chat.direct.ack"),
    CHAT_DIRECT_REJECT("chat.direct.reject"),
    FRIEND_REQUEST_NOTIFY("friend.request.notify"),
    FRIEND_REQUEST_ACCEPTED("friend.request.accepted"),
    FRIEND_REQUEST_DENIED("friend.request.denied"),
    FRIEND_REQUEST_REVOKED("friend.request.revoked"),
    NOTIFY_SOCIAL_INTERACTION("notify.social"),
    TELEPORT_REQUEST("teleport.request"),
    TELEPORT_PRECHECK("teleport.precheck"),
    TELEPORT_PRECHECK_RESULT("teleport.precheck.result"),
    TELEPORT_TRANSFER("teleport.transfer"),
    TELEPORT_EXECUTE("teleport.execute"),
    TELEPORT_EXECUTE_ACK("teleport.execute.ack"),
    TELEPORT_FAIL("teleport.fail"),
    NOTIFY_BIRTHDAY("notify.birthday");

    companion object {
        fun fromId(id: String): ProxyMessageType? = entries.firstOrNull { it.id == id }
    }
}
