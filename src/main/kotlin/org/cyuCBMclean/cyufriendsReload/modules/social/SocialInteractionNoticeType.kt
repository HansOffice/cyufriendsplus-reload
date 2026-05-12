package org.cyuCBMclean.cyufriendsReload.modules.social

enum class SocialInteractionNoticeType(
    val id: String,
    val configKey: String,
    val messageKey: String,
    val soundKey: String
) {
    STATUS_LIKE("status.like", "status-like", "status-like-notice", "status-like"),
    STATUS_COMMENT("status.comment", "status-comment", "status-comment-notice", "status-comment"),
    WALL_POST("wall.post", "wall-post", "wall-post-notice", "wall-post-notice"),
    WALL_LIKE("wall.like", "wall-like", "wall-like-notice", "wall-like"),
    WALL_COMMENT("wall.comment", "wall-comment", "wall-comment-notice", "wall-comment");

    companion object {
        fun fromId(id: String?): SocialInteractionNoticeType? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}
