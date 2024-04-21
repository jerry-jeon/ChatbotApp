package com.jerryjeon.chatbot.voice

sealed class VoiceConversationState {
    data object NoPermission : VoiceConversationState()
    data object Idle : VoiceConversationState()
    data object Listening : VoiceConversationState()
    data class Processing(
        val spokenText: String
    ) : VoiceConversationState()

    data class Sending(
        val spokenText: String
    ) : VoiceConversationState()

    data class Speaking(
        val spokenText: String,
        val isSpeaking: Boolean,
        val continueProgress: Int = 0,
    ) : VoiceConversationState()

    data class Error(val errorMessage: String) : VoiceConversationState()
}
