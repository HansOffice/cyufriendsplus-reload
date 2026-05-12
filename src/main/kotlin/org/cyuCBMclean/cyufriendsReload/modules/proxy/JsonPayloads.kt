package org.cyuCBMclean.cyufriendsReload.modules.proxy

import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendTeleportMode

object JsonPayloads {

    fun join(name: String, headSource: String? = null): String {
        return buildString {
            append('{')
            append("\"name\":\"").append(escape(name)).append('"')
            append(",\"headSource\":")
            if (headSource == null) append("null") else append('"').append(escape(headSource)).append('"')
            append('}')
        }
    }

    fun uidChanged(newUid: String): String {
        return "{\"newUid\":\"${escape(newUid)}\"}"
    }

    fun headUpdated(headSource: String?): String {
        return buildString {
            append('{')
            append("\"headSource\":")
            if (headSource == null) append("null") else append('"').append(escape(headSource)).append('"')
            append('}')
        }
    }

    fun directMessage(senderUid: String, senderName: String, content: String): String {
        return buildString {
            append('{')
            append("\"senderUid\":\"").append(escape(senderUid)).append("\",")
            append("\"senderName\":\"").append(escape(senderName)).append("\",")
            append("\"content\":\"").append(escape(content)).append('"')
            append('}')
        }
    }

    fun friendRequest(senderUid: String, senderName: String, note: String? = null): String {
        return buildString {
            append('{')
            append("\"senderUid\":\"").append(escape(senderUid)).append("\",")
            append("\"senderName\":\"").append(escape(senderName)).append('"')
            if (!note.isNullOrBlank()) {
                append(",\"note\":\"").append(escape(note)).append('"')
            }
            append('}')
        }
    }

    fun friendRequestResult(actorName: String): String {
        return "{\"actorName\":\"${escape(actorName)}\"}"
    }

    fun socialInteraction(kind: String, actorUid: String, actorName: String, preview: String? = null): String {
        return buildString {
            append('{')
            append("\"kind\":\"").append(escape(kind)).append("\",")
            append("\"actorUid\":\"").append(escape(actorUid)).append("\",")
            append("\"actorName\":\"").append(escape(actorName)).append('"')
            if (!preview.isNullOrBlank()) {
                append(",\"preview\":\"").append(escape(preview)).append('"')
            }
            append('}')
        }
    }

    fun birthdayNotify(playerName: String, daysAhead: Int): String {
        return "{\"player\":\"${escape(playerName)}\",\"daysAhead\":$daysAhead}"
    }

    fun intValue(json: String, key: String): Int? {
        val pattern = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun teleportRequest(requesterUid: String, requesterName: String): String {
        return buildString {
            append('{')
            append("\"requesterUid\":\"").append(escape(requesterUid)).append("\",")
            append("\"requesterName\":\"").append(escape(requesterName)).append('"')
            append('}')
        }
    }

    fun teleportPrecheckResult(
        status: String,
        targetUid: String? = null,
        targetName: String? = null,
        mode: FriendTeleportMode? = null
    ): String {
        return buildString {
            append('{')
            append("\"status\":\"").append(escape(status)).append('"')
            if (!targetUid.isNullOrBlank()) {
                append(",\"targetUid\":\"").append(escape(targetUid)).append('"')
            }
            if (!targetName.isNullOrBlank()) {
                append(",\"targetName\":\"").append(escape(targetName)).append('"')
            }
            if (mode != null) {
                append(",\"mode\":\"").append(escape(mode.id)).append('"')
            }
            append('}')
        }
    }

    fun teleportExecute(targetUid: String, targetName: String): String {
        return buildString {
            append('{')
            append("\"targetUid\":\"").append(escape(targetUid)).append("\",")
            append("\"targetName\":\"").append(escape(targetName)).append('"')
            append('}')
        }
    }

    fun status(status: String): String {
        return "{\"status\":\"${escape(status)}\"}"
    }

    fun reject(reason: String, actorName: String?): String {
        return buildString {
            append('{')
            append("\"reason\":\"").append(escape(reason)).append('"')
            if (!actorName.isNullOrBlank()) {
                append(",\"actorName\":\"").append(escape(actorName)).append('"')
            }
            append('}')
        }
    }

    fun reject(reason: String): String {
        return reject(reason, null)
    }

    fun stringValue(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        val match = pattern.find(json) ?: return null
        return unescape(match.groupValues[1])
    }

    fun presenceSnapshot(json: String): List<RemotePresence> {
        val pattern = Regex("\\{\"uid\":\"((?:\\\\.|[^\"])*)\",\"name\":\"((?:\\\\.|[^\"])*)\",\"serverId\":\"((?:\\\\.|[^\"])*)\",\"headSource\":(null|\"((?:\\\\.|[^\"])*)\"),\"onlineAt\":(\\d+),\"lastSeenAt\":(\\d+)\\}")
        return pattern.findAll(json).map { match ->
            RemotePresence(
                uid = unescape(match.groupValues[1]),
                name = unescape(match.groupValues[2]),
                serverId = unescape(match.groupValues[3]),
                headSource = match.groupValues[4].takeIf { it != "null" }?.let { unescape(match.groupValues[5]) },
                onlineAt = match.groupValues[6].toLong(),
                lastSeenAt = match.groupValues[7].toLong()
            )
        }.toList()
    }

    private fun escape(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}
