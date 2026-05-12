package org.cyuCBMclean.cyufriendsReload.modules.social

enum class WallVisibility(val id: String, val displayName: String) {
    PUBLIC("PUBLIC", "公开"),
    FRIENDS("FRIENDS", "好友可见"),
    PRIVATE("PRIVATE", "仅墙主与留言者");

    companion object {
        fun fromValue(value: String?): WallVisibility? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.id.equals(value, true) ||
                    it.name.equals(value, true) ||
                    it.displayName.equals(value, true)
            }
        }
    }
}
