package org.cyuCBMclean.cyufriendsReload.ui.action

data class ActionNode(
    val executorId: String,
    val payload: String,
    val delayTicks: Long = 0,
    val chance: Double = 100.0
) {
    companion object {
        fun parse(raw: String): ActionNode {
            var currentStr = raw.trim()
            var delay: Long = 0
            var chance = 100.0

            while (currentStr.startsWith("[")) {
                val endIdx = currentStr.indexOf("]")
                if (endIdx == -1) break
                val tag = currentStr.substring(1, endIdx)
                val remaining = currentStr.substring(endIdx + 1).trim()

                when {
                    tag.startsWith("delay=", true) -> {
                        delay = tag.substring(6).toLongOrNull() ?: 0
                        currentStr = remaining
                    }
                    tag.startsWith("chance=", true) -> {
                        chance = tag.substring(7).toDoubleOrNull() ?: 100.0
                        currentStr = remaining
                    }
                    else -> break
                }
            }

            val executorEndIdx = currentStr.indexOf("]")
            if (currentStr.startsWith("[") && executorEndIdx != -1) {
                val executorId = currentStr.substring(1, executorEndIdx).lowercase()
                val payload = currentStr.substring(executorEndIdx + 1).trim()
                return ActionNode(executorId, payload, delayTicks = delay, chance = chance)
            }

            return ActionNode("console", currentStr, delayTicks = delay, chance = chance)
        }
    }
}