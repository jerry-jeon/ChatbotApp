package com.jerryjeon.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SendbirdViewModel @Inject constructor(
    private val sendbirdPreferences: SendbirdPreferences
) : ViewModel() {

    private val _appId = MutableStateFlow("")
    val sendbirdAppId: StateFlow<String> = _appId.asStateFlow()

    private val _userId = MutableStateFlow("")
    val sendbirdUserId: StateFlow<String> = _userId.asStateFlow()

    init {
        viewModelScope.launch {
            sendbirdPreferences.sendbirdAppId.collect { appId ->
                _appId.value = appId
            }
        }

        viewModelScope.launch {
            sendbirdPreferences.sendbirdUserId.collect { userId ->
                _userId.value = userId
            }
        }
    }

    fun updateAppId(appId: String) {
        _appId.value = appId
    }

    fun updateUserId(userId: String) {
        _userId.value = userId
    }

    fun saveAppIdAndUserId(appId: String, userId: String) {
        viewModelScope.launch {
            sendbirdPreferences.saveSendbirdAppId(appId)
            sendbirdPreferences.saveSendbirdUserId(userId)
        }
    }
}
