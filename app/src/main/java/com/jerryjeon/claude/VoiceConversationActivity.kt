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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
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

    private lateinit var viewModel: VoiceConversationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            MyViewModelFactory(application, this, intent.extras ?: Bundle())
        ).get(VoiceConversationViewModel::class.java)

        val recordAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.updatePermissionState(true)
            } else {
                viewModel.updatePermissionState(false)
            }
        }

        setContent {
            ClaudeAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!SpeechRecognizer.isRecognitionAvailable(this@VoiceConversationActivity)) {
                        // Inform the user that speech recognition is not available
                        RecognitionNotAvailable()
                    } else {
                        VoiceConversationScreen(viewModel)
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.handleBackPress()) {
                    isEnabled = false   // Disable this callback
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true    // Re-enable this callback if the Activity isn't finished
                }
            }
        })

        // Check and request the RECORD_AUDIO permission
        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
private fun VoiceConversationScreen(
    viewModel: VoiceConversationViewModel
) {
    val state by viewModel.stateFlow.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startListening()
    }

    VoiceConversationScreen(
        state = state,
        startListening = viewModel::startListening,
        stopListening = viewModel::stopListening,
        stopSpeaking = viewModel::stopSpeaking
    )
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
