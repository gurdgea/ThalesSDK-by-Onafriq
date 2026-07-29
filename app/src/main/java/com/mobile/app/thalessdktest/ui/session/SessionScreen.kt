package com.mobile.app.thalessdktest.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.app.d1core.session.D1SessionState
import com.mobile.app.thalessdktest.ui.common.ErrorCard
import com.mobile.app.thalessdktest.ui.common.LabelledValue
import com.mobile.app.thalessdktest.ui.common.presentable

@Composable
fun SessionScreen(
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val warnings by viewModel.warnings.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val config = viewModel.config

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("D1 session", style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(Modifier.padding(16.dp)) {
                LabelledValue("Environment", config?.environment ?: "unavailable")
                LabelledValue("Issuer", config?.issuerId ?: "-")
                LabelledValue("Service URL", config?.serviceUrl ?: "-")
                LabelledValue("Client binding", (config?.clientBindingEnabled ?: false).toString())
                LabelledValue("State", state.describe())
            }
        }

        viewModel.configError?.let { ErrorCard(it) }
        error?.let { ErrorCard(it) }

        if (warnings.isNotEmpty()) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Configured with warnings",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Core init succeeded; these targets failed independently.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    warnings.forEach { Text("• ${it.presentable()}") }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::configure, enabled = viewModel.isUsable) {
                Text("Configure")
            }
            Button(onClick = viewModel::login, enabled = viewModel.isUsable) {
                Text("Login")
            }
            OutlinedButton(onClick = viewModel::logout, enabled = viewModel.isUsable) {
                Text("Logout")
            }
            OutlinedButton(onClick = viewModel::logoutAll, enabled = viewModel.isUsable) {
                Text("Logout all")
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("SDK versions", style = MaterialTheme.typography.titleSmall)
                viewModel.sdkVersions.forEach { (name, version) ->
                    LabelledValue(name, version)
                }
                LabelledValue(
                    "D1Pay component",
                    if (viewModel.d1PayAvailable) "present" else "not in this AAR",
                )
            }
        }
    }
}

private fun D1SessionState.describe(): String = when (this) {
    D1SessionState.Idle -> "Idle"
    D1SessionState.Configuring -> "Configuring…"
    is D1SessionState.Configured -> "Configured (${warnings.size} warning(s))"
    D1SessionState.LoggingIn -> "Logging in…"
    D1SessionState.LoggedIn -> "Logged in"
    is D1SessionState.Blocked -> "Blocked: ${reason.presentable()}"
    is D1SessionState.ConfigureFailed -> "Configure failed: ${reason.presentable()}"
}
