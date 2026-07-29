package com.mobile.app.thalessdktest.ui.wallet

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.app.thalessdktest.ui.common.ErrorCard
import com.mobile.app.thalessdktest.ui.common.LabelledValue
import com.mobile.app.thalessdktest.ui.common.StateSection
import com.thalesgroup.gemalto.d1.card.CardAction
import com.thalesgroup.gemalto.d1.pushprovisioning.CardDigitizationState

private const val PUSH_TO_SCHEME_CALLBACK =
    "thalessdktest://com.mobile.app.thalessdktest/PushToSchemeResult"

@Composable
fun WalletScreen(
    cardId: String,
    last4: String,
    onLast4Change: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WalletViewModel = viewModel(factory = WalletViewModel.Factory),
) {
    val context = LocalContext.current
    val digitization by viewModel.digitization.collectAsStateWithLifecycle()
    val requestors by viewModel.tokenRequestors.collectAsStateWithLifecycle()
    val digitalCards by viewModel.digitalCards.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wallet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Samsung Pay is omitted: the delivery has no samsungpay JAR.",
            style = MaterialTheme.typography.bodySmall,
        )

        error?.let { ErrorCard(it) }
        message?.let {
            Card { Text(it, Modifier.padding(16.dp)) }
        }

        androidx.compose.material3.OutlinedTextField(
            value = last4,
            onValueChange = onLast4Change,
            label = { Text("Last 4 PAN digits") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.checkDigitizationByLast4(last4) },
                enabled = last4.length == 4,
            ) { Text("State by last 4") }
            OutlinedButton(
                onClick = { viewModel.checkDigitizationByCardId(cardId) },
                enabled = cardId.isNotBlank(),
            ) { Text("State by card ID") }
        }

        StateSection(digitization) { state ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelledValue("Digitization state", state.name)
                    when (state) {
                        CardDigitizationState.NOT_DIGITIZED -> Button(
                            onClick = {
                                (context as? Activity)?.let {
                                    viewModel.addToGooglePay(cardId, it)
                                }
                            },
                            enabled = cardId.isNotBlank(),
                        ) { Text("Add to Google Pay") }

                        CardDigitizationState.PENDING_IDV -> Text(
                            "Pending ID&V — authenticate the user, then activate the digital card.",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        CardDigitizationState.DIGITIZED -> Text(
                            "Already in the wallet.",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        else -> Unit
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Token Connect", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { viewModel.loadTokenRequestors(cardId) },
                    enabled = cardId.isNotBlank(),
                ) { Text("Eligible token requestors") }

                StateSection(requestors) { list ->
                    if (list.isEmpty()) Text("None eligible for this card.")
                    list.forEach { requestor ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(requestor.name ?: requestor.id ?: "-")
                                Text(
                                    requestor.id ?: "-",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(onClick = {
                                viewModel.pushToScheme(
                                    cardId = cardId,
                                    requestor = requestor,
                                    callbackUrl = PUSH_TO_SCHEME_CALLBACK,
                                ) { deepLink ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, deepLink.toUri())
                                    )
                                }
                            }) { Text("Push") }
                        }
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Digital cards (view & control)", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { viewModel.loadDigitalCards(cardId) },
                    enabled = cardId.isNotBlank(),
                ) { Text("List digital cards") }

                StateSection(digitalCards) { list ->
                    if (list.isEmpty()) Text("No tokens for this card.")
                    list.forEach { digitalCard ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                digitalCard.tokenRequestorName
                                    ?: digitalCard.tokenRequestorID
                                    ?: digitalCard.cardID,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                listOfNotNull(
                                    digitalCard.state?.name,
                                    digitalCard.last4?.let { "•••• $it" },
                                    digitalCard.deviceName,
                                    if (digitalCard.isOnCurrentDevice) "this device" else null,
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                            )

                            digitalCard.deviceBindingList?.forEach { binding ->
                                Text(
                                    "binding ${binding.bindingReference}: " +
                                        "${binding.bindingStatus?.name} (${binding.deviceName})",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CardAction.entries.forEach { action ->
                                    OutlinedButton(onClick = {
                                        viewModel.updateDigitalCard(cardId, digitalCard, action)
                                    }) { Text(action.name) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
