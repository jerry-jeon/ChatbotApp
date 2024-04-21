package com.jerryjeon.chatbot

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sendbird.android.SendbirdChat
import com.sendbird.android.channel.BaseChannel
import com.sendbird.android.channel.GroupChannel
import com.sendbird.android.handler.GroupChannelHandler
import com.sendbird.android.ktx.extension.channel.getChannel
import com.sendbird.android.message.BaseMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceConversationViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private var startListeningJob: Job? = null

    private val channelUrl: String = savedStateHandle.get<String>("channelUrl")
        ?: throw IllegalArgumentException("Channel URL is required")

    private val function: (status: Int) -> Unit = { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    viewModelScope.launch {
                        stopSpeaking()
                    }
                }

                override fun onError(utteranceId: String?) {}
                override fun onError(utteranceId: String?, errorCode: Int) {}
                override fun onStart(utteranceId: String?) {}
            })
        }
    }
    private val tts: TextToSpeech = TextToSpeech(getApplication(), function)
    private var speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
        this.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(bundle: Bundle?) {
                _stateFlow.value = VoiceConversationState.Listening
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(bundle: Bundle?) {
                val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                text?.let {
                    _stateFlow.value = VoiceConversationState.Processing(it)
                } ?: run {
                    _stateFlow.value = VoiceConversationState.Error("No text found")
                }
            }


            override fun onError(errorCode: Int) {
                val errorMessage = when (errorCode) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Error from server"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                _stateFlow.value = VoiceConversationState.Error("Error: $errorMessage")
            }
        })
    }

    private val _stateFlow = MutableStateFlow<VoiceConversationState>(VoiceConversationState.NoPermission)
    val stateFlow = _stateFlow.asStateFlow()

    init {
        setUp(channelUrl)
        // When the state is changed to Processing, send the spoken text to the bot
        stateFlow.onEach { println(it) }.onEach { state ->
            when (state) {
                is VoiceConversationState.Processing -> {
                    val spokenText = state.spokenText

                    val channel = GroupChannel.getChannel(channelUrl)
                    channel.sendUserMessage(spokenText) { message, error ->
                        if (error != null) {
                            _stateFlow.value = VoiceConversationState.Error(error.message ?: "Error")
                        } else if (message != null) {
                            _stateFlow.value = VoiceConversationState.Sending(message.message)
                        }
                    }
                }

                is VoiceConversationState.Speaking -> {
                    if (state.continueProgress == 100) {
                        startListening()
                    } else if (state.isSpeaking) {
                        tts.speak(state.spokenText, TextToSpeech.QUEUE_FLUSH, null, "id")
                    } else {
                        tts.stop()
                    }
                }

                else -> {}
            }
        }.launchIn(viewModelScope)
    }

    fun startListening() {
        startListeningJob?.cancel()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun stopSpeaking() {
        val state = stateFlow.value
        if (state is VoiceConversationState.Speaking) {
            startListeningJob = viewModelScope.launch {
                for (progress in state.continueProgress..100 step 1) {
                    _stateFlow.value = state.copy(
                        isSpeaking = false,
                        continueProgress = progress
                    )
                    delay(30)
                }
            }
        }
    }

    fun updatePermissionState(permissionGranted: Boolean) {
        _stateFlow.value = if (permissionGranted) {
            VoiceConversationState.Idle
        } else {
            VoiceConversationState.NoPermission
        }
    }

    fun handleBackPress(): Boolean {
        return when (val state = stateFlow.value) {
            is VoiceConversationState.Listening -> {
                speechRecognizer.stopListening()
                true
            }

            is VoiceConversationState.Speaking -> {
                if (state.isSpeaking) {
                    stopSpeaking()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun setUp(channelUrl: String) {
        SendbirdChat.addChannelHandler(channelUrl, object : GroupChannelHandler() {
            override fun onMessageReceived(channel: BaseChannel, message: BaseMessage) {
                if (channel.url != channelUrl) return
                if (message.sender?.userId == SendbirdChat.currentUser?.userId) return

                _stateFlow.value = VoiceConversationState.Speaking(message.message, isSpeaking = true)
            }
        })
    }
    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        SendbirdChat.removeChannelHandler(channelUrl)
    }
}

