package com.jerryjeon.claude

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.sendbird.android.exception.SendbirdException
import com.sendbird.android.handler.InitResultHandler
import com.sendbird.uikit.SendbirdUIKit
import com.sendbird.uikit.activities.CreateChannelActivity
import com.sendbird.uikit.adapter.SendbirdUIKitAdapter
import com.sendbird.uikit.consts.CreatableChannelType
import com.sendbird.uikit.fragments.ChannelFragment
import com.sendbird.uikit.interfaces.UserInfo
import com.sendbird.uikit.interfaces.providers.ChannelFragmentProvider
import com.sendbird.uikit.interfaces.providers.ChannelListModuleProvider
import com.sendbird.uikit.modules.ChannelListModule
import com.sendbird.uikit.modules.components.HeaderComponent
import com.sendbird.uikit.providers.FragmentProviders
import com.sendbird.uikit.providers.ModuleProviders
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@HiltAndroidApp
class ClaudeApplication : Application() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    companion object {
        val initialized = MutableStateFlow(InitState.INITIALIZING)
    }


    private val sendbirdPreferences: SendbirdPreferences by lazy {
        SendbirdPreferences(this)
    }

    override fun onCreate() {
        super.onCreate()
        coroutineScope.launch {
            initApp()
        }
    }

    private suspend fun initApp() {
        val appId = sendbirdPreferences.sendbirdAppId.firstOrNull()
        val userId = sendbirdPreferences.sendbirdUserId.firstOrNull()
        if (appId?.isNotEmpty() == true && userId?.isNotEmpty() == true) {
            initApp(appId, userId)
        } else {
            initialized.value = InitState.FAILED
        }
    }

    private fun initApp(appId: String, userId: String) {
        initialized.value = InitState.INITIALIZING
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
                        initialized.value = InitState.FAILED
                    }

                    override fun onInitSucceed() {
                        Log.d("TAG", "onInitSucceed")
                        initialized.value = InitState.INITIALIZED
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

        ModuleProviders.channelList = ChannelListModuleProvider { context, _ ->
            ChannelListModule(context).apply {
                setHeaderComponent(ChannelListHeaderComponent())
            }
        }
    }
}

class ChannelListHeaderComponent : HeaderComponent() {
    override fun onCreateView(context: Context, inflater: LayoutInflater, parent: ViewGroup, args: Bundle?): View {
        val headerView = inflater.inflate(R.layout.view_channel_list_header, parent, false)
        val createChannelButton = headerView.findViewById<ImageButton>(R.id.addChannelButton)
        createChannelButton.setOnClickListener { view ->
            view.context.startActivity(CreateChannelActivity.newIntent(view.context, CreatableChannelType.Normal))
        }

        val settingsButton = headerView.findViewById<ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener { view ->
            view.context.startActivity(Intent(view.context, SettingsActivity::class.java))
        }
        return headerView
    }

}


enum class InitState {
    INITIALIZING,
    FAILED,
    INITIALIZED
}
