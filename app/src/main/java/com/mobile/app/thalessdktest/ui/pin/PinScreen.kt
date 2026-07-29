package com.mobile.app.thalessdktest.ui.pin

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.app.d1core.D1Client
import com.mobile.app.d1ui.CardActivationField
import com.mobile.app.d1ui.CardPinDisplay
import com.mobile.app.d1ui.SecurePinEntry
import com.mobile.app.d1ui.SecurePinField
import com.mobile.app.d1ui.rememberCardActivationEntry
import com.mobile.app.d1ui.rememberCardPinDisplay
import com.mobile.app.thalessdktest.ui.common.ErrorCard
import com.mobile.app.thalessdktest.ui.common.LabelledValue
import com.thalesgroup.gemalto.d1.PINEntryUI
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

@Composable
fun PinScreen(
    client: D1Client?,
    cardId: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<Throwable?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var activationMethod by remember { mutableStateOf<String?>(null) }

    val pinDisplay = rememberCardPinDisplay()
    val activationEntry = rememberCardActivationEntry()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PIN & activation", style = MaterialTheme.typography.headlineSmall)

        error?.let { ErrorCard(it) }
        status?.let { Card { Text(it, Modifier.padding(16.dp)) } }

        if (client == null) {
            Text("D1 is not configured.")
            return@Column
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display physical card PIN", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Rendered inside an SDK-owned view; the PIN never reaches this app.",
                    style = MaterialTheme.typography.bodySmall,
                )
                CardPinDisplay(pinDisplay, Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        scope.launch {
                            error = null
                            runCatching { client.pin.displayPin(cardId, pinDisplay.ui) }
                                .onSuccess { status = "PIN displayed." }
                                .onFailure { error = it }
                        }
                    },
                    enabled = cardId.isNotBlank(),
                ) { Text("Show PIN") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Change PIN", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Change PIN has a stricter session window than any other API, so the " +
                        "session is refreshed immediately before submit.",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (cardId.isBlank()) {
                    Text("Enter a card ID first.")
                } else {
                    SecurePinEntry(pin = client.pin, cardId = cardId, pinLength = PIN_LENGTH) { state ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("New PIN", style = MaterialTheme.typography.labelMedium)
                            SecurePinField(state.entry, Modifier.fillMaxWidth())
                            Text("Confirm PIN", style = MaterialTheme.typography.labelMedium)
                            SecurePinField(state.confirm, Modifier.fillMaxWidth())

                            Text(
                                when (state.event) {
                                    PINEntryUI.PINEvent.FIRST_ENTRY_FINISH -> "Now confirm the PIN"
                                    PINEntryUI.PINEvent.PIN_MATCH -> "PINs match"
                                    PINEntryUI.PINEvent.PIN_MISMATCH -> "PINs do not match"
                                    null -> "Enter a $PIN_LENGTH digit PIN"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )

                            Button(
                                onClick = {
                                    scope.launch {
                                        error = null
                                        runCatching { state.submit(client.pin) }
                                            .onSuccess { status = "PIN changed." }
                                            .onFailure { error = it }
                                    }
                                },
                                enabled = state.canSubmit,
                            ) { Text("Submit") }
                        }
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Activate physical card", style = MaterialTheme.typography.titleSmall)
                activationMethod?.let { LabelledValue("Challenge", it) }
                Text(
                    "The issuer picks the challenge per card product: CVV or last 4 PAN digits.",
                    style = MaterialTheme.typography.bodySmall,
                )
                CardActivationField(activationEntry, Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                error = null
                                runCatching { client.pin.activationMethod(cardId) }
                                    .onSuccess { activationMethod = it.name }
                                    .onFailure { error = it }
                            }
                        },
                        enabled = cardId.isNotBlank(),
                    ) { Text("Get method") }

                    Button(
                        onClick = {
                            scope.launch {
                                error = null
                                runCatching {
                                    client.pin.activatePhysicalCard(
                                        cardId,
                                        activationEntry.entryUI,
                                    )
                                }
                                    .onSuccess { status = "Card activated." }
                                    .onFailure { error = it }
                            }
                        },
                        enabled = cardId.isNotBlank(),
                    ) { Text("Activate") }
                }
            }
        }
    }
}
