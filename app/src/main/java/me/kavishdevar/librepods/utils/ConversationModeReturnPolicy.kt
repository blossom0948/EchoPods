/* SPDX-License-Identifier: GPL-3.0-or-later */
package me.kavishdevar.librepods.utils

/** A host-side best-effort plan; firmware may still change listening mode independently. */
internal data class ConversationModeReturnPlan(
    val temporaryMode: Int,
    val restoreMode: Int,
    val delaySeconds: Int,
)

enum class ConversationReturnBehavior(val preferenceValue: Int) {
    AIRPODS_DEFAULT(0),
    AFTER_SPEECH_ENDS(1),
    FIXED_AFTER_SPEECH_START(2),
    MANUAL(3);

    companion object {
        fun fromPreference(value: Int): ConversationReturnBehavior =
            values().firstOrNull { it.preferenceValue == value } ?: AIRPODS_DEFAULT
    }
}

internal object ConversationModeReturnPolicy {
    const val TRANSPARENCY_MODE = 3

    fun sanitizeDelay(seconds: Int): Int = when {
        seconds == 0 -> 0
        seconds < 0 -> 0
        else -> seconds.coerceIn(MIN_CUSTOM_DELAY_SECONDS, MAX_CUSTOM_DELAY_SECONDS)
    }

    fun createPlan(delaySeconds: Int, originalMode: Int?): ConversationModeReturnPlan? {
        val safeDelay = sanitizeDelay(delaySeconds)
        val safeOriginalMode = originalMode?.takeIf { it in 1..4 } ?: return null
        if (safeDelay == 0 || safeOriginalMode == TRANSPARENCY_MODE) return null
        return ConversationModeReturnPlan(
            temporaryMode = TRANSPARENCY_MODE,
            restoreMode = safeOriginalMode,
            delaySeconds = safeDelay,
        )
    }

    const val MIN_CUSTOM_DELAY_SECONDS = 10
    const val MAX_CUSTOM_DELAY_SECONDS = 120
}
