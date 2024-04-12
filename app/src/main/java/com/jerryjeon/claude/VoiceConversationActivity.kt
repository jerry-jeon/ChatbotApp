package com.jerryjeon.claude

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextAlign.Companion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jerryjeon.claude.ui.theme.ClaudeAppTheme
import com.sendbird.android.SendbirdChat
import com.sendbird.android.channel.BaseChannel
import com.sendbird.android.channel.GroupChannel
import com.sendbird.android.handler.GroupChannelHandler
import com.sendbird.android.ktx.extension.channel.getChannel
import com.sendbird.android.message.BaseMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceConversationActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech

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
            val errorMessage = when (error) {
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
            stateFlow.value = VoiceConversationState.Error("Error: $errorMessage")
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)

            if (text != null) {
                stateFlow.value = VoiceConversationState.Processing(text)
            } else {
                stateFlow.value = VoiceConversationState.Error("No text found")
            }
        }

    }

    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
        setRecognitionListener(listener)
    }

    val stateFlow: MutableStateFlow<VoiceConversationState> = MutableStateFlow(VoiceConversationState.NoPermission)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String?) {
                        lifecycleScope.launch {
                            stopSpeaking()
                        }
                    }

                    override fun onError(utteranceId: String?) {}
                    override fun onStart(utteranceId: String?) {}
                })
            }
        }

        val channelUrl = intent.getStringExtra("channelUrl") ?: throw IllegalArgumentException("channelUrl is required")
        setUp(channelUrl)

        // When the state is changed to Processing, send the spoken text to the bot
        stateFlow.onEach { println(it) }.onEach { state ->
            when (state) {
                is VoiceConversationState.Processing -> {
                    val spokenText = state.spokenText

                    val channel = GroupChannel.getChannel(channelUrl)
                    channel.sendUserMessage(spokenText) { message, error ->
                        if (error != null) {
                            stateFlow.value = VoiceConversationState.Error(error.message ?: "Error")
                        } else if (message != null) {
                            stateFlow.value = VoiceConversationState.Sending(message.message)
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
        }.launchIn(lifecycleScope)

        setContent {
            ClaudeAppTheme {
                // Creates an permission request
                val recordAudioLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(), onResult = { isGranted ->
                    if (isGranted) {
                        stateFlow.value = VoiceConversationState.Idle
                    } else {
                        stateFlow.value = VoiceConversationState.NoPermission
                    }
                })

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
                            startListening = this::startListening,
                            stopListening = speechRecognizer::stopListening,
                            stopSpeaking = this::stopSpeaking
                        )
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (val state = stateFlow.value) {
                    is VoiceConversationState.Listening -> {
                        speechRecognizer.stopListening()
                    }

                    is VoiceConversationState.Speaking -> {
                        if (state.isSpeaking) {
                            stopSpeaking()
                        } else {
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }

                    else -> onBackPressedDispatcher.onBackPressed()
                }

            }
        })
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

                stateFlow.value = VoiceConversationState.Speaking(message.message, isSpeaking = true)
            }
        })
    }

    private fun stopSpeaking() {
        val state = stateFlow.value
        if (state is VoiceConversationState.Speaking) {
            lifecycleScope.launch {
                for (progress in state.continueProgress..100 step 1) {
                    stateFlow.value = state.copy(
                        isSpeaking = false,
                        continueProgress = progress
                    )
                    delay(30)
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

}

@Composable
private fun VoiceConversationScreen(
    state: VoiceConversationState,
    startListening: () -> Unit,
    stopListening: () -> Unit,
    stopSpeaking: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            VoiceConversationState.NoPermission -> {
                Text("Permission denied")
            }

            VoiceConversationState.Idle -> {
                IdleState(message = null, startListening = startListening)
            }

            VoiceConversationState.Listening -> {
                ListeningScreen(stopListening = stopListening)
            }

            is VoiceConversationState.Processing -> {
                ProcessingScreen(state)
            }

            is VoiceConversationState.Sending -> {
                SendingScreen(state)
            }

            is VoiceConversationState.Speaking -> {
                SpeakingView(state, stopSpeaking, startListening)
            }

            is VoiceConversationState.Error -> {
                IdleState(
                    message = state.errorMessage, startListening = startListening
                )
            }
        }
    }
}

@Composable
private fun IdleState(
    message: String? = null,
    startListening: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .background(Color.LightGray, CircleShape)
            .clickable { startListening() }
            .size(150.dp)
            .padding(16.dp)
            .align(Alignment.Center),
            contentAlignment = Alignment.Center) {
            Text("Tap to speak")
        }
        if (message != null) {
            Box(
                modifier = Modifier
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Text(text = message)
            }
        }
    }
}

@Composable
private fun ListeningScreen(
    stopListening: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoomingAnimation()
        }
        Box(
            modifier = Modifier
                .height(120.dp)
                .align(Alignment.BottomCenter)
        ) {
            Button(onClick = stopListening) {
                Text("Stop Listening")
            }
        }
    }
}

@Composable
fun BoomingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "InfiniteTransition")
    val size by infiniteTransition.animateValue(
        initialValue = 150.dp,
        targetValue = 200.dp,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        typeConverter = Dp.VectorConverter,
        label = ""
    )

    Box(
        modifier = Modifier
            .background(Color.LightGray, CircleShape)
            .size(size)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Listening...")
    }
}

@Composable
private fun ProcessingScreen(state: VoiceConversationState.Processing) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ProgressView(informationText = "Processing the response", spokenText = state.spokenText)
    }
}

@Composable
private fun SendingScreen(state: VoiceConversationState.Sending) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ProgressView(informationText = "Waiting for the response", spokenText = state.spokenText)
    }
}

@Composable
private fun SpeakingView(state: VoiceConversationState.Speaking, stopSpeaking: () -> Unit, startListening: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isSpeaking) {
            ProgressView(modifier = Modifier.align(Alignment.Center), informationText = "Speaking", spokenText = state.spokenText)
        } else {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Text(
                    text = state.spokenText, modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), textAlign = TextAlign.Center
                )
            }
        }

        // Add a button to stop speaking
        Box(
            modifier = Modifier
                .height(120.dp)
                .align(Alignment.BottomCenter)
        ) {
            if (state.isSpeaking) {
                Button(onClick = stopSpeaking) {
                    Text("Stop")
                }
            } else {
                IconButton(onClick = startListening) {
                    // Show progress around the icon
                    Box(contentAlignment = Alignment.Center) {
                        // Circular progress that continues around the icon
                        CircularProgressIndicator(
                            progress = state.continueProgress.toFloat() / 100f,
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // The icon itself
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start listening")
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start listening")
                }
            }
        }
    }
}

@Composable
private fun ProgressView(modifier: Modifier = Modifier, informationText: String, spokenText: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(informationText)
        Spacer(modifier = Modifier.height(16.dp))
        // Center text
        Text(
            text = spokenText, modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), textAlign = TextAlign.Center
        )
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
        val spokenText: String,
        val isSpeaking: Boolean,
        val continueProgress: Int = 0,
    ) : VoiceConversationState()

    data class Error(val errorMessage: String) : VoiceConversationState()
}

@Composable
fun RecognitionNotAvailable() {
    // Show a message that speech recognition is not available
    Text("Speech recognition is not available on this device")
}

@Preview(showBackground = true)
@Composable
private fun PreviewIdleState() {
    IdleState(startListening = {})
}

@Preview(showBackground = true)
@Composable
private fun PreviewSendingScreen() {
    SendingScreen(VoiceConversationState.Sending("Hello"))
}

@Preview(showBackground = true)
@Composable
private fun PreviewProcessingScreen() {
    ProcessingScreen(VoiceConversationState.Processing("Hello"))
}

@Preview(showBackground = true)
@Composable
private fun PreviewListeningScreen() {
    ListeningScreen(stopListening = { })
}

@Preview(showBackground = true)
@Composable
private fun PreviewSpeakingView() {
    SpeakingView(VoiceConversationState.Speaking("Hello", isSpeaking = true), stopSpeaking = { }, startListening = { })
}
