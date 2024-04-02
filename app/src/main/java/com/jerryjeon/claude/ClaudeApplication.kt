package com.jerryjeon.claude

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import com.sendbird.android.exception.SendbirdException
import com.sendbird.android.handler.InitResultHandler
import com.sendbird.uikit.SendbirdUIKit
import com.sendbird.uikit.adapter.SendbirdUIKitAdapter
import com.sendbird.uikit.fragments.ChannelFragment
import com.sendbird.uikit.interfaces.UserInfo
import com.sendbird.uikit.interfaces.providers.ChannelFragmentProvider
import com.sendbird.uikit.providers.FragmentProviders
import kotlinx.coroutines.flow.MutableStateFlow

class ClaudeApplication : Application() {
    companion object {

        internal val initialized = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()

        SendbirdUIKit.init(object : SendbirdUIKitAdapter {
            override fun getAppId(): String {
                return BuildConfig.APP_ID
            }

            override fun getAccessToken(): String {
                return ""
            }

            override fun getUserInfo(): UserInfo {
                return object : UserInfo {
                    override fun getUserId(): String {
                        return BuildConfig.USER_ID
                        // Use the ID of a user you've created on the dashboard.
                        // If there isn't one, specify a unique ID so that a new user can be created with the value.
                    }

                    override fun getNickname(): String {
                        return BuildConfig.USER_ID // Specify your user nickname. Optional.
                    }

                    override fun getProfileUrl(): String {
                        return ""
                    }
                }
            }

            override fun getInitResultHandler(): InitResultHandler {
                return object : InitResultHandler {
                    override fun onMigrationStarted() {
                        // DB migration has started.
                    }

                    override fun onInitFailed(e: SendbirdException) {
                        // If DB migration fails, this method is called.
                    }

                    override fun onInitSucceed() {
                        // If DB migration is successful, this method is called and you can proceed to the next step.
                        // In the sample app, the `LiveData` class notifies you on the initialization progress
                        // And observes the `MutableLiveData<InitState> initState` value in `SplashActivity()`.
                        // If successful, the `LoginActivity` screen
                        // Or the `HomeActivity` screen will show.
                        println("initialized")
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
