package com.mobile.app.thalessdktest.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobile.app.d1core.D1Client
import com.mobile.app.d1core.config.D1Config
import com.mobile.app.d1core.error.D1Failure
import com.mobile.app.d1core.session.D1SessionState
import com.mobile.app.thalessdktest.di.D1Locator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(clientResult: Result<D1Client>) : ViewModel() {

    private val client = clientResult.getOrNull()

    val configError: Throwable? = clientResult.exceptionOrNull()
    val isUsable: Boolean = client != null
    // Falls back to the raw values so the screen can still name the environment
    // and its file when validation rejected them.
    val config: D1Config? =
        client?.config ?: runCatching { D1Config.fromBuildConfig() }.getOrNull()
    val sdkVersions: Map<String, String> =
        runCatching { client?.sdkVersions().orEmpty() }.getOrDefault(emptyMap())
    val d1PayAvailable: Boolean = D1Locator.d1Pay.isAvailable

    val sessionState: StateFlow<D1SessionState> =
        client?.state ?: MutableStateFlow(D1SessionState.Idle).asStateFlow()

    private val _warnings = MutableStateFlow<List<D1Failure>>(emptyList())
    val warnings: StateFlow<List<D1Failure>> = _warnings.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    fun configure() = launchGuarded {
        _warnings.value = requireClient().configure()
    }

    fun login() = launchGuarded { requireClient().login() }

    fun logout() = launchGuarded { requireClient().logout() }

    fun logoutAll() = launchGuarded { requireClient().logoutAll() }

    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it }
        }
    }

    private fun requireClient(): D1Client =
        client ?: error("D1 client unavailable")

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SessionViewModel(D1Locator.client(app))
            }
        }
    }
}
