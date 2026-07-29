package com.mobile.app.thalessdktest.ui.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobile.app.d1core.D1Client
import com.mobile.app.thalessdktest.di.D1Locator
import com.mobile.app.thalessdktest.ui.common.UiState
import com.thalesgroup.gemalto.d1.card.cardservice.CardSettings
import com.thalesgroup.gemalto.d1.card.cardservice.CardTransactionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ControlsSnapshot(
    val onlinePayment: Boolean?,
    val abroadPayment: Boolean?,
    val contactless: Boolean?,
    val magneticStripe: Boolean?,
    val atmWithdrawal: Boolean?,
    val gambling: Boolean?,
    val adult: Boolean?,
    val risky: Boolean?,
    val deniedCurrencies: List<String>,
    val countries: List<String>,
)

class ControlsViewModel(private val client: D1Client?) : ViewModel() {

    /**
     * The SDK object is cached verbatim and mutated in place. Constructing a
     * CardSettings would overwrite backend state and the SDK rejects it.
     */
    private var cached: CardSettings? = null

    private val _snapshot = MutableStateFlow<UiState<ControlsSnapshot>>(UiState.Idle)
    val snapshot: StateFlow<UiState<ControlsSnapshot>> = _snapshot.asStateFlow()

    private val _transactions =
        MutableStateFlow<UiState<List<CardTransactionRecord>>>(UiState.Idle)
    val transactions: StateFlow<UiState<List<CardTransactionRecord>>> = _transactions.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    fun load(cardId: String) = guard {
        _snapshot.value = UiState.Loading
        val settings = requireClient().cardControl.settings(cardId)
        cached = settings
        _snapshot.value = UiState.Ready(settings.toSnapshot())
    }

    fun setControl(cardId: String, mutate: (CardSettings) -> Unit) = guard {
        val settings = cached ?: error("Call getCardSettings() before updating")
        mutate(settings)
        requireClient().cardControl.updateControls(cardId, settings.control)
        _message.value = "Card controls updated."
        _snapshot.value = UiState.Ready(settings.toSnapshot())
    }

    fun loadTransactions(cardId: String) = guard {
        _transactions.value = UiState.Loading
        _transactions.value = UiState.Ready(requireClient().cardControl.transactions(cardId))
    }

    fun suspendCard(cardId: String) = guard {
        requireClient().cardControl.suspendCard(cardId)
        _message.value = "Card suspended."
    }

    fun resumeCard(cardId: String) = guard {
        requireClient().cardControl.resumeCard(cardId)
        _message.value = "Card resumed."
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
                ControlsViewModel(D1Locator.client(app).getOrNull())
            }
        }
    }
}

/** Nulls are preserved: they mean the card product does not offer that control. */
private fun CardSettings.toSnapshot(): ControlsSnapshot {
    val controls = control
    val merchant = runCatching { controls.merchant }.getOrNull()
    val geography = runCatching { controls.geography }.getOrNull()
    return ControlsSnapshot(
        onlinePayment = controls.isOnlinePaymentEnabled,
        abroadPayment = controls.isAbroadPaymentEnabled,
        contactless = controls.isContactlessEnabled,
        magneticStripe = controls.isMagneticStripeEnabled,
        atmWithdrawal = controls.isATMWithdrawalEnabled,
        gambling = runCatching { merchant?.isGamblingMerchantEnabled }.getOrNull(),
        adult = runCatching { merchant?.isAdultMerchantEnabled }.getOrNull(),
        risky = runCatching { merchant?.isRiskyMerchantEnabled }.getOrNull(),
        deniedCurrencies = controls.deniedCurrencyList.orEmpty(),
        countries = runCatching { geography?.countryList }.getOrNull().orEmpty(),
    )
}
