package com.jerryjeon.chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jerryjeon.chatbot.ui.theme.ChatbotAppTheme
import com.sendbird.uikit.activities.ChannelListActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            ChatbotApplication.initialized
                .collectLatest {
                    if (it == InitState.INITIALIZED) {
                        startActivity(ChannelListActivity.newIntent(this@MainActivity))
                        finish()
                    }
                }
        }

        setContent {
            ChatbotAppTheme {
                val initState by ChatbotApplication.initialized.collectAsState()
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (initState) {
                        InitState.FAILED -> {
                            AppIdUserIdScreen()
                        }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                // Show loading screen
                                CircularProgressIndicator(modifier = Modifier.size(120.dp))
                            }
                        }

                    }
                }
            }
        }
    }
}
