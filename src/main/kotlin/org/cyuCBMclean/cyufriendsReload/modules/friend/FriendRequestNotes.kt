package org.cyuCBMclean.cyufriendsReload.modules.friend

import org.cyuCBMclean.cyufriendsReload.CyufriendsReload

object FriendRequestNotes {

    private const val DEFAULT_MAX_LENGTH = 48
    private const val DEFAULT_PREVIEW_LENGTH = 24

    fun maxLength(plugin: CyufriendsReload): Int {
        return plugin.config.getInt("requestNotes.max-length", DEFAULT_MAX_LENGTH).coerceAtLeast(8)
    }

    fun previewLength(plugin: CyufriendsReload): Int {
        return plugin.config.getInt("requestNotes.preview-length", DEFAULT_PREVIEW_LENGTH).coerceAtLeast(8)
    }

    fun normalize(plugin: CyufriendsReload, raw: String?): String? {
        val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.take(maxLength(plugin))
    }

    fun preview(plugin: CyufriendsReload, note: String?): String {
        val normalized = note?.trim().orEmpty()
        if (normalized.isEmpty()) return "未填写"
        val previewLength = previewLength(plugin)
        return if (normalized.length <= previewLength) {
            normalized
        } else {
            normalized.take(previewLength) + "..."
        }
    }
}
