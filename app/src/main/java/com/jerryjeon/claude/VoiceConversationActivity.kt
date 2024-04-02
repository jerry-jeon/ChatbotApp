package com.jerryjeon.claude

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jerryjeon.claude.ui.theme.ClaudeAppTheme
import com.sendbird.android.SendbirdChat
import com.sendbird.android.channel.BaseChannel
import com.sendbird.android.channel.GroupChannel
import com.sendbird.android.collection.GroupChannelContext
import com.sendbird.android.collection.MessageContext
import com.sendbird.android.handler.GroupChannelHandler
import com.sendbird.android.handler.MessageCollectionHandler
import com.sendbird.android.ktx.extension.channel.getChannel
import com.sendbird.android.message.BaseMessage
import com.sendbird.android.params.MessageCollectionCreateParams
import com.sendbird.android.params.MessageListParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class VoiceConversationActivity : ComponentActivity() {

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p0: Bundle?) {
            stateFlow.value = VoiceConversationState.Listening
        }

        override fun onPartialResults(p0: Bundle?) {}
        override fun onEvent(p0: Int, p1: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(p0: Float) {}
        override fun onBufferReceived(p0: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            stateFlow.value = VoiceConversationState.Error
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.getOrNull(0)

            if (text != null) {
                stateFlow.value = VoiceConversationState.Processing(text)
            } else {
                stateFlow.value = VoiceConversationState.Error
            }
        }

    }

    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
        setRecognitionListener(listener)
    }

    val stateFlow: MutableStateFlow<VoiceConversationState> = MutableStateFlow(VoiceConversationState.NoPermission)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelUrl = intent.getStringExtra("channelUrl") ?: throw IllegalArgumentException("channelUrl is required")
        setUp(channelUrl)

        // When the state is changed to Processing, send the spoken text to the bot
        stateFlow
            .onEach { println(it) }
            .onEach { state ->
                if (state is VoiceConversationState.Processing) {
                    val spokenText = state.spokenText

                    val channel = GroupChannel.getChannel(channelUrl)
                    channel.sendUserMessage(spokenText) { message, error ->
                        if (error != null) {
                            stateFlow.value = VoiceConversationState.Error
                        } else if (message != null) {
                            stateFlow.value = VoiceConversationState.Sending(message.message)
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)

        setContent {
            ClaudeAppTheme {
                // Creates an permission request
                val recordAudioLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            stateFlow.value = VoiceConversationState.Idle
                        } else {
                            stateFlow.value = VoiceConversationState.NoPermission
                        }
                    }
                )

                LaunchedEffect(key1 = recordAudioLauncher) {
                    // Launches the permission request
                    recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }


                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!SpeechRecognizer.isRecognitionAvailable(this@VoiceConversationActivity)) {
                        // Inform the user that speech recognition is not available
                        RecognitionNotAvailable()
                    } else {
                        val state by stateFlow.collectAsState()

                        VoiceConversationScreen(
                            state = state,
                            startListening = this::startListening
                        )
                    }
                }
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1) // You can specify the number of results you want.
        }
        speechRecognizer.startListening(intent)
    }

    private fun setUp(channelUrl: String) {
        SendbirdChat.addChannelHandler(channelUrl, object : GroupChannelHandler() {
            override fun onMessageReceived(channel: BaseChannel, message: BaseMessage) {
                if (channel.url != channelUrl) return
                if (message.sender?.userId == SendbirdChat.currentUser?.userId) return

                stateFlow.value = VoiceConversationState.Speaking(message.message)
            }
        })
    }
}

@Composable
private fun VoiceConversationScreen(
    state: VoiceConversationState,
    startListening: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            VoiceConversationState.NoPermission -> {
                Text("Permission denied")
            }

            VoiceConversationState.Idle -> {
                IdleState(startListening)
            }

            VoiceConversationState.Listening -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            is VoiceConversationState.Processing -> {
                Column {
                    Text("Processing...")
                    Text(state.spokenText)
                }
            }

            is VoiceConversationState.Sending -> {
                Column {
                    Text("Bot is typing...")
                    Text(state.spokenText)
                }
            }

            is VoiceConversationState.Speaking -> {
                Text(state.spokenText)
            }

            VoiceConversationState.Error -> {
                Text("An error occurred")
            }
        }
    }
}

@Composable
private fun IdleState(
    startListening: () -> Unit
) {
    Button(onClick = startListening) {
        Text("Start Listening")
    }
}

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
        val spokenText: String
    ) : VoiceConversationState()

    data object Error : VoiceConversationState()
}

@Composable
fun RecognitionNotAvailable() {
    // Show a message that speech recognition is not available
    Text("Speech recognition is not available on this device")
}

@Preview
@Composable
fun PreviewVoiceConversationActivity() {
    VoiceConversationActivity()
}

