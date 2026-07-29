package com.mobile.app.thalessdktest.ui.wallet

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobile.app.d1core.D1Client
import com.mobile.app.d1core.service.DigitalCardUpdate
import com.mobile.app.thalessdktest.di.D1Locator
import com.mobile.app.thalessdktest.ui.common.UiState
import com.thalesgroup.gemalto.d1.card.CardAction
import com.thalesgroup.gemalto.d1.card.DigitalCard
import com.thalesgroup.gemalto.d1.card.OEMPayType
import com.thalesgroup.gemalto.d1.pushprovisioning.CardDigitizationState
import com.thalesgroup.gemalto.d1.pushprovisioning.TokenRequestor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val ADD_TO_WALLET_REQUEST_CODE = 4401

class WalletViewModel(private val client: D1Client?) : ViewModel() {

    private val _digitization = MutableStateFlow<UiState<CardDigitizationState>>(UiState.Idle)
    val digitization: StateFlow<UiState<CardDigitizationState>> = _digitization.asStateFlow()

    private val _tokenRequestors = MutableStateFlow<UiState<List<TokenRequestor>>>(UiState.Idle)
    val tokenRequestors: StateFlow<UiState<List<TokenRequestor>>> = _tokenRequestors.asStateFlow()

    private val _digitalCards = MutableStateFlow<UiState<List<DigitalCard>>>(UiState.Idle)
    val digitalCards: StateFlow<UiState<List<DigitalCard>>> = _digitalCards.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    /** Resolved locally with no network call, so prefer the last-4 form. */
    fun checkDigitizationByLast4(last4: String) = guard {
        _digitization.value = UiState.Loading
        _digitization.value = UiState.Ready(
            requireClient().pushProvisioning
                .digitizationStateByLast4(OEMPayType.GOOGLE_PAY, last4)
        )
    }

    fun checkDigitizationByCardId(cardId: String) = guard {
        _digitization.value = UiState.Loading
        _digitization.value = UiState.Ready(
            requireClient().pushProvisioning
                .digitizationStateByCardId(cardId, OEMPayType.GOOGLE_PAY)
        )
    }

    /** Only meaningful when the state is NOT_DIGITIZED. */
    fun addToGooglePay(cardId: String, activity: Activity) = guard {
        requireClient().pushProvisioning.addToWallet(
            cardId = cardId,
            wallet = OEMPayType.GOOGLE_PAY,
            activity = activity,
            requestCode = ADD_TO_WALLET_REQUEST_CODE,
        )
        _message.value = "Provisioning started; the wallet result arrives via onActivityResult."
    }

    fun loadTokenRequestors(cardId: String) = guard {
        _tokenRequestors.value = UiState.Loading
        _tokenRequestors.value =
            UiState.Ready(requireClient().pushProvisioning.tokenRequestors(cardId))
    }

    fun pushToScheme(
        cardId: String,
        requestor: TokenRequestor,
        callbackUrl: String,
        onDeepLink: (String) -> Unit,
    ) = guard {
        val url = requireClient().pushProvisioning
            .addToScheme(cardId, requestor, callbackUrl, termsAccepted = true)
        onDeepLink(url)
    }

    fun loadDigitalCards(cardId: String) = guard {
        _digitalCards.value = UiState.Loading
        _digitalCards.value = UiState.Ready(requireClient().digitalCards.list(cardId))
    }

    fun updateDigitalCard(cardId: String, card: DigitalCard, action: CardAction) = guard {
        val outcome = requireClient().digitalCards.update(cardId, card, action)
        _message.value = when (outcome) {
            DigitalCardUpdate.Applied -> "$action applied."
            DigitalCardUpdate.InvalidTransition ->
                "$action rejected: not a valid transition from the current state."
        }
        _digitalCards.value = UiState.Ready(requireClient().digitalCards.list(cardId))
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun requireClient(): D1Client = client ?: error("D1 client unavailable")

    private fun guard(block: suspend () -> Unit) {
        viewModelScope.launch {
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                WalletViewModel(D1Locator.client(app).getOrNull())
            }
        }
    }
}
