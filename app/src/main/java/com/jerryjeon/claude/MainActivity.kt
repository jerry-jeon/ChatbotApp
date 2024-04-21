package com.jerryjeon.claude

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.jerryjeon.claude.ui.theme.ClaudeAppTheme
import com.sendbird.android.exception.SendbirdException
import com.sendbird.android.handler.InitResultHandler
import com.sendbird.uikit.SendbirdUIKit
import com.sendbird.uikit.activities.ChannelListActivity
import com.sendbird.uikit.adapter.SendbirdUIKitAdapter
import com.sendbird.uikit.fragments.ChannelFragment
import com.sendbird.uikit.interfaces.UserInfo
import com.sendbird.uikit.interfaces.providers.ChannelFragmentProvider
import com.sendbird.uikit.providers.FragmentProviders
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: SendbirdViewModel by viewModels()

    private val initialized = MutableStateFlow(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            initApp()
        }
        lifecycleScope.launch {
            initialized
                .collectLatest {
                    if (it) {
                        startActivity(ChannelListActivity.newIntent(this@MainActivity))
                        finish()
                    }
                }
        }

        setContent {
            ClaudeAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (BuildConfig.SENDBIRD_APP_ID.isEmpty() || BuildConfig.SENDBIRD_USER_ID.isEmpty()) {
                        AppIdUserIdScreen(viewModel)
                        return@Surface
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // Show loading screen
                            CircularProgressIndicator(modifier = Modifier.size(120.dp))
                        }
                    }
                }
            }
        }

    }

    private suspend fun initApp() {
        Log.d("TAG", "???")
        viewModel.sendbirdAppId.combine(viewModel.sendbirdUserId) { appId, userId -> appId to userId }
            .collect { (appId, userId) ->
                Log.d("TAG", "appId: $appId, userId: $userId")
                if (appId.isNotEmpty() && userId.isNotEmpty()) {
                    Log.d("TAG", "initApp")
                    initApp(appId, userId)
                }
            }
    }

    private fun initApp(appId: String, userId: String) {
        SendbirdUIKit.init(object : SendbirdUIKitAdapter {
            override fun getAppId(): String {
                return appId
            }

            override fun getAccessToken(): String {
                return ""
            }

            override fun getUserInfo(): UserInfo {
                return object : UserInfo {
                    override fun getUserId(): String {
                        return userId
                        // Use the ID of a user you've created on the dashboard.
                        // If there isn't one, specify a unique ID so that a new user can be created with the value.
                    }

                    override fun getNickname(): String {
                        return userId // Specify your user nickname. Optional.
                    }

                    override fun getProfileUrl(): String {
                        return ""
                    }
                }
            }

            override fun getInitResultHandler(): InitResultHandler {
                return object : InitResultHandler {
                    override fun onMigrationStarted() {
                    }

                    override fun onInitFailed(e: SendbirdException) {
                    }

                    override fun onInitSucceed() {
                        Log.d("TAG", "onInitSucceed")
                        initialized.value = true
                    }
                }
            }
        }, this)

        FragmentProviders.channel = ChannelFragmentProvider { channelUrl, args ->
            ChannelFragment.Builder(channelUrl)
                .withArguments(args)
                .setHeaderRightButtonIconResId(R.drawable.baseline_record_voice_over_24)
                .setOnHeaderRightButtonClickListener {
                    val intent = Intent(it.context, VoiceConversationActivity::class.java)
                    intent.putExtra("channelUrl", channelUrl)
                    it.context.startActivity(intent)
                }
                .build()
        }
    }
}


@Composable
private fun AppIdUserIdScreen(
    viewModel: SendbirdViewModel = hiltViewModel()
) {
    val appId by viewModel.sendbirdAppId.collectAsState(initial = "")
    val userId by viewModel.sendbirdUserId.collectAsState(initial = "")
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Please enter your App ID and User ID.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // App ID input field
        OutlinedTextField(
            value = appId,
            onValueChange = { viewModel.updateAppId(it) },
            label = { Text("App ID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // User ID input field
        OutlinedTextField(
            value = userId,
            onValueChange = { viewModel.updateUserId(it) },
            label = { Text("User ID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Save button
        Button(
            onClick = {
                viewModel.saveAppIdAndUserId(appId, userId)
                // Navigate to the next screen or perform any other desired action
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Link to the Sendbird dashboard
        Text(
            text = "Go to Sendbird Dashboard",
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dashboard.sendbird.com"))
                context.startActivity(intent)
            }
        )
    }
}
