package org.cyuCBMclean.cyufriendsReload.modules.social

enum class StatusVisibility(val id: String, val displayName: String) {
    PUBLIC("PUBLIC", "公开"),
    FRIENDS("FRIENDS", "好友"),
    PRIVATE("PRIVATE", "仅自己");

    companion object {
        fun fromValue(value: String?): StatusVisibility? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.id.equals(value, true) ||
                    it.name.equals(value, true) ||
                    it.displayName.equals(value, true)
            }
        }
    }
}
