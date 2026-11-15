/* SPDX-License-Identifier: GPL-3.0-or-later */
package me.kavishdevar.librepods.utils

/** Conservative classification of the documented AAP 0x4B speech-status byte. */
internal enum class ConversationalAwarenessEvent {
    SPEECH_STARTED,
    SPEECH_ENDED,
    INTERMEDIATE_OR_UNKNOWN,
}

internal object ConversationalAwarenessEventParser {
    fun classify(status: Byte): ConversationalAwarenessEvent = classify(status.toInt() and 0xff)

    fun classify(status: Int): ConversationalAwarenessEvent = when (status) {
        1, 2 -> ConversationalAwarenessEvent.SPEECH_STARTED
        3, 8, 9 -> ConversationalAwarenessEvent.SPEECH_ENDED
        else -> ConversationalAwarenessEvent.INTERMEDIATE_OR_UNKNOWN
    }
}
