package com.mobile.app.thalessdktest.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.app.d1core.D1Client
import com.mobile.app.d1ui.SecureCardDetails
import com.mobile.app.d1ui.SecureCardField
import com.mobile.app.d1ui.mask
import com.mobile.app.d1ui.reveal
import com.mobile.app.thalessdktest.ui.common.ErrorCard
import com.mobile.app.thalessdktest.ui.common.LabelledValue
import com.mobile.app.thalessdktest.ui.common.StateSection
import kotlinx.coroutines.launch

@Composable
fun CardsScreen(
    client: D1Client?,
    cardId: String,
    onCardIdChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardsViewModel = viewModel(factory = CardsViewModel.Factory),
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val masked by viewModel.maskedSummary.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cards", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = cardId,
            onValueChange = onCardIdChange,
            label = { Text("Card ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::loadCards) { Text("List cards") }
            OutlinedButton(
                onClick = { viewModel.loadMetadata(cardId) },
                enabled = cardId.isNotBlank(),
            ) { Text("Metadata") }
        }

        StateSection(cards) { list ->
            if (list.isEmpty()) {
                Text("No cards returned for this consumer.")
            }
            list.forEach { card ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCardIdChange(card.cardId) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(card.cardId, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(card.scheme, card.last4?.let { "•••• $it" }, card.state)
                                .joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        StateSection(metadata) { data ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Metadata", style = MaterialTheme.typography.titleSmall)
                    LabelledValue("Last 4 PAN", data.last4Pan ?: "-")
                    LabelledValue("Scheme", runCatching { data.scheme?.name }.getOrNull() ?: "-")
                    LabelledValue("State", runCatching { data.state?.name }.getOrNull() ?: "-")
                    LabelledValue(
                        "State reason",
                        runCatching { data.stateReason?.name }.getOrNull() ?: "-",
                    )
                    LabelledValue(
                        "Ongoing operation",
                        runCatching { data.ongoingOperation?.name }.getOrNull() ?: "-",
                    )
                }
            }
        }

        StateSection(assets) { list ->
            Text(
                "${list.size} card asset(s) cached locally",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (client != null && cardId.isNotBlank()) {
            SdkRenderedCardDetails(client, cardId)
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Option 1 — app renders",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Card data arrives as byte[] and is wiped before the call returns. " +
                        "Only a masked summary is kept.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { viewModel.loadMaskedSummary(cardId) },
                    enabled = cardId.isNotBlank(),
                ) { Text("Load masked summary") }
                StateSection(masked) { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun SdkRenderedCardDetails(client: D1Client, cardId: String) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<Throwable?>(null) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Option 2 — SDK renders", style = MaterialTheme.typography.titleSmall)
            Text(
                "The SDK writes into its own views. No card data enters this process. " +
                    "Auto-masks after 60s. Visa and Mastercard only.",
                style = MaterialTheme.typography.bodySmall,
            )

            SecureCardDetails(
                display = client.secureCardDisplay,
                cardId = cardId,
                onError = { error = it },
            ) { state ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabelledSecureField("PAN") { SecureCardField(state.pan) }
                    LabelledSecureField("Expiry") { SecureCardField(state.expiry) }
                    LabelledSecureField("CVV") { SecureCardField(state.cvv) }
                    LabelledSecureField("Cardholder") { SecureCardField(state.cardHolderName) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                runCatching { state.reveal(client.secureCardDisplay) }
                                    .onFailure { error = it }
                            }
                        }) { Text(if (state.isRevealed) "Refresh" else "Reveal") }

                        OutlinedButton(onClick = { state.mask(client.secureCardDisplay) }) {
                            Text("Mask")
                        }
                    }
                }
            }

            error?.let { ErrorCard(it) }
        }
    }
}

@Composable
private fun LabelledSecureField(label: String, field: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        field()
    }
}
