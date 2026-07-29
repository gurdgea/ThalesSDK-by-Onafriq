package com.mobile.app.thalessdktest.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.app.d1core.error.D1Failure

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
    data class Failed(val error: Throwable) : UiState<Nothing>
}

fun Throwable.presentable(): String = when (this) {
    is D1Failure.NotLoggedIn -> "Session expired. Log in again."
    is D1Failure.DeviceUnsafe -> "This device was judged unsafe; D1 has disabled all APIs."
    is D1Failure.DebugSdkInRelease -> "The debug D1 AAR is in a release build."
    is D1Failure.ConfigInvalid -> message ?: "Invalid D1 configuration"
    else -> message ?: this::class.simpleName ?: "Unknown error"
}

@Composable
fun <T> StateSection(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Idle -> Unit
        UiState.Loading -> Row(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }

        is UiState.Failed -> ErrorCard(state.error, modifier)
        is UiState.Ready -> content(state.value)
    }
}

@Composable
fun ErrorCard(error: Throwable, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = error.presentable(),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
fun LabelledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Renders nothing when [checked] is null: the card product does not offer it. */
@Composable
fun OptionalSwitchRow(
    label: String,
    checked: Boolean?,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (checked == null) return
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
