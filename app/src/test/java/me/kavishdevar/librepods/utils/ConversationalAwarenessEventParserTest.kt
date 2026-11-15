package me.kavishdevar.librepods.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationalAwarenessEventParserTest {
    @Test
    fun documentedSpeechStartValuesAreClassifiedAsStart() {
        assertEquals(ConversationalAwarenessEvent.SPEECH_STARTED, ConversationalAwarenessEventParser.classify(1))
        assertEquals(ConversationalAwarenessEvent.SPEECH_STARTED, ConversationalAwarenessEventParser.classify(2.toByte()))
    }

    @Test
    fun documentedSpeechEndAndNormalValuesAreClassifiedAsEnd() {
        listOf(3, 8, 9).forEach { status ->
            assertEquals(ConversationalAwarenessEvent.SPEECH_ENDED, ConversationalAwarenessEventParser.classify(status))
        }
    }

    @Test
    fun undocumentedAndIntermediateValuesDoNotEndTheConversation() {
        listOf(0, 4, 5, 6, 7, 10, 255).forEach { status ->
            assertEquals(
                "status=$status",
                ConversationalAwarenessEvent.INTERMEDIATE_OR_UNKNOWN,
                ConversationalAwarenessEventParser.classify(status),
            )
        }
    }
}
