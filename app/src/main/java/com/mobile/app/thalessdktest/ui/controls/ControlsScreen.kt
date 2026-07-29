package com.mobile.app.thalessdktest.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.app.thalessdktest.ui.common.ErrorCard
import com.mobile.app.thalessdktest.ui.common.LabelledValue
import com.mobile.app.thalessdktest.ui.common.OptionalSwitchRow
import com.mobile.app.thalessdktest.ui.common.StateSection

@Composable
fun ControlsScreen(
    cardId: String,
    modifier: Modifier = Modifier,
    viewModel: ControlsViewModel = viewModel(factory = ControlsViewModel.Factory),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Transaction control", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Controls that come back null are not offered by the card product, " +
                "so their rows are hidden rather than disabled.",
            style = MaterialTheme.typography.bodySmall,
        )

        error?.let { ErrorCard(it) }
        message?.let { Card { Text(it, Modifier.padding(16.dp)) } }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.load(cardId) },
                enabled = cardId.isNotBlank(),
            ) { Text("Get card settings") }
            OutlinedButton(
                onClick = { viewModel.suspendCard(cardId) },
                enabled = cardId.isNotBlank(),
            ) { Text("Suspend") }
            OutlinedButton(
                onClick = { viewModel.resumeCard(cardId) },
                enabled = cardId.isNotBlank(),
            ) { Text("Resume") }
        }

        StateSection(snapshot) { data ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Domain controls", style = MaterialTheme.typography.titleSmall)

                    OptionalSwitchRow("Online payment", data.onlinePayment) { value ->
                        viewModel.setControl(cardId) { it.control.setOnlinePaymentEnabled(value) }
                    }
                    OptionalSwitchRow("Abroad payment", data.abroadPayment) { value ->
                        viewModel.setControl(cardId) { it.control.setAbroadPaymentEnabled(value) }
                    }
                    OptionalSwitchRow("Contactless", data.contactless) { value ->
                        viewModel.setControl(cardId) { it.control.setContactlessEnabled(value) }
                    }
                    OptionalSwitchRow("Magnetic stripe", data.magneticStripe) { value ->
                        viewModel.setControl(cardId) { it.control.setMagneticStripeEnabled(value) }
                    }
                    OptionalSwitchRow("ATM withdrawal", data.atmWithdrawal) { value ->
                        viewModel.setControl(cardId) { it.control.setATMWithdrawalEnabled(value) }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Merchant categories", style = MaterialTheme.typography.titleSmall)

                    OptionalSwitchRow("Gambling", data.gambling) { value ->
                        viewModel.setControl(cardId) {
                            it.control.merchant.setGamblingMerchantEnabled(value)
                        }
                    }
                    OptionalSwitchRow("Adult", data.adult) { value ->
                        viewModel.setControl(cardId) {
                            it.control.merchant.setAdultMerchantEnabled(value)
                        }
                    }
                    OptionalSwitchRow("Risky", data.risky) { value ->
                        viewModel.setControl(cardId) {
                            it.control.merchant.setRiskyMerchantEnabled(value)
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LabelledValue(
                        "Denied currencies (ISO 4217)",
                        data.deniedCurrencies.joinToString().ifEmpty { "none" },
                    )
                    LabelledValue(
                        "Countries (ISO 3166-1 alpha-2)",
                        data.countries.joinToString().ifEmpty { "none" },
                    )
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction history", style = MaterialTheme.typography.titleSmall)
                Text(
                    "D1 caps this at 50 records over the last 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { viewModel.loadTransactions(cardId) },
                    enabled = cardId.isNotBlank(),
                ) { Text("Load transactions") }

                StateSection(transactions) { records ->
                    if (records.isEmpty()) Text("No transactions returned.")
                    records.forEach { record ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                runCatching { record.merchant?.name }.getOrNull()
                                    ?: record.id
                                    ?: "-",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                listOfNotNull(
                                    record.transactionDate,
                                    record.transactionStatus?.name,
                                    record.declinedReason,
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
