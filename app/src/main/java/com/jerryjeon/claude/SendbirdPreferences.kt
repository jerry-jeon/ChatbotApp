package com.jerryjeon.claude

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject


class SendbirdPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore: DataStore<Preferences> = context.sendbirdDataStore

    companion object {
        private val Context.sendbirdDataStore by preferencesDataStore(
            name = "sendbird_prefs"
        )
        private val SENDBIRD_APP_ID_KEY = stringPreferencesKey("sendbird_app_id")
        private val SENDBIRD_USER_ID_KEY = stringPreferencesKey("sendbird_user_id")
    }

    val sendbirdAppId: Flow<String>
        get() = dataStore.data.map { preferences ->
            preferences[SENDBIRD_APP_ID_KEY] ?: BuildConfig.SENDBIRD_APP_ID
        }

    val sendbirdUserId: Flow<String>
        get() = dataStore.data.map { preferences ->
            preferences[SENDBIRD_USER_ID_KEY] ?: BuildConfig.SENDBIRD_USER_ID
        }

    suspend fun saveSendbirdAppId(appId: String) {
        dataStore.edit { preferences ->
            preferences[SENDBIRD_APP_ID_KEY] = appId
        }
    }

    suspend fun saveSendbirdUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[SENDBIRD_USER_ID_KEY] = userId
        }
    }
}
