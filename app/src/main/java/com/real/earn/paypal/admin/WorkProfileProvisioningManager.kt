package com.real.earn.paypal.admin

import android.os.UserHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class WorkProfileSetupState {
    object Idle : WorkProfileSetupState()
    object InProgress : WorkProfileSetupState()
    data class Success(val profileUserHandle: UserHandle?) : WorkProfileSetupState()
    data class Failed(val reason: String) : WorkProfileSetupState()
}

object WorkProfileProvisioningManager {
    private val _setupState = MutableStateFlow<WorkProfileSetupState>(WorkProfileSetupState.Idle)
    val setupState = _setupState.asStateFlow()

    fun markStarted() {
        _setupState.value = WorkProfileSetupState.InProgress
    }

    fun markSuccess(handle: UserHandle? = null) {
        _setupState.value = WorkProfileSetupState.Success(handle)
    }

    fun markFailed(reason: String) {
        _setupState.value = WorkProfileSetupState.Failed(reason)
    }

    fun reset() {
        _setupState.value = WorkProfileSetupState.Idle
    }
}
