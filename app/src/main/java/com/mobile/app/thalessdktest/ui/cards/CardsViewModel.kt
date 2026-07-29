package com.mobile.app.thalessdktest.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobile.app.d1core.D1Client
import com.mobile.app.thalessdktest.di.D1Locator
import com.mobile.app.thalessdktest.ui.common.UiState
import com.thalesgroup.gemalto.d1.card.CardAsset
import com.thalesgroup.gemalto.d1.card.CardMetadata
import com.thalesgroup.gemalto.d1.card.cardservice.Card
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CardSummary(
    val cardId: String,
    val last4: String?,
    val scheme: String?,
    val state: String?,
)

class CardsViewModel(private val client: D1Client?) : ViewModel() {

    private val _cards = MutableStateFlow<UiState<List<CardSummary>>>(UiState.Idle)
    val cards: StateFlow<UiState<List<CardSummary>>> = _cards.asStateFlow()

    private val _metadata = MutableStateFlow<UiState<CardMetadata>>(UiState.Idle)
    val metadata: StateFlow<UiState<CardMetadata>> = _metadata.asStateFlow()

    private val _assets = MutableStateFlow<UiState<List<CardAsset>>>(UiState.Idle)
    val assets: StateFlow<UiState<List<CardAsset>>> = _assets.asStateFlow()

    /** Option 1 output: rendered by us, so it is never retained beyond this string. */
    private val _maskedSummary = MutableStateFlow<UiState<String>>(UiState.Idle)
    val maskedSummary: StateFlow<UiState<String>> = _maskedSummary.asStateFlow()

    fun loadCards() {
        val d1 = client ?: return
        viewModelScope.launch {
            _cards.value = UiState.Loading
            _cards.value = runCatching { d1.cardControl.cards().map(Card::toSummary) }
                .fold({ UiState.Ready(it) }, { UiState.Failed(it) })
        }
    }

    fun loadMetadata(cardId: String) {
        val d1 = client ?: return
        viewModelScope.launch {
            _metadata.value = UiState.Loading
            runCatching { d1.secureCardDisplay.metadata(cardId) }
                .onSuccess { data ->
                    _metadata.value = UiState.Ready(data)
                    _assets.value = UiState.Loading
                    _assets.value = runCatching { d1.secureCardDisplay.assets(data) }
                        .fold({ UiState.Ready(it) }, { UiState.Failed(it) })
                }
                .onFailure { _metadata.value = UiState.Failed(it) }
        }
    }

    /**
     * Demonstrates the byte[] path. The PAN is masked to its last four digits
     * before it becomes a String, and the SDK buffers are wiped by the wrapper
     * before this coroutine resumes.
     */
    fun loadMaskedSummary(cardId: String) {
        val d1 = client ?: return
        viewModelScope.launch {
            _maskedSummary.value = UiState.Loading
            _maskedSummary.value = runCatching {
                d1.secureCardDisplay.withCardDetails(cardId) { details ->
                    val last4 = details.pan.takeLast(4).toByteArray().toString(Charsets.UTF_8)
                    val expiry = details.expiryDate.toString(Charsets.UTF_8)
                    "•••• $last4  exp $expiry"
                }
            }.fold({ UiState.Ready(it) }, { UiState.Failed(it) })
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                CardsViewModel(D1Locator.client(app).getOrNull())
            }
        }
    }
}

private fun Card.toSummary() = CardSummary(
    cardId = cardID,
    last4 = runCatching { cardMetadata?.last4Pan }.getOrNull(),
    scheme = runCatching { cardMetadata?.scheme?.name }.getOrNull(),
    state = runCatching { cardMetadata?.state?.name }.getOrNull(),
)
