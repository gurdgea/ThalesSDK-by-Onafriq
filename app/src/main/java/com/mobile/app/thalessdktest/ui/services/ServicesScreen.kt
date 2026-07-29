package com.mobile.app.thalessdktest.ui.services

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
import com.mobile.app.thalessdktest.ui.common.ErrorCard
import com.mobile.app.thalessdktest.ui.common.LabelledValue
import com.thalesgroup.gemalto.d1.clicktopay.Profile
import com.thalesgroup.gemalto.d1.messaging.Message
import kotlinx.coroutines.launch

@Composable
fun ServicesScreen(
    client: D1Client?,
    cardId: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<Throwable?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            error = null
            runCatching { block() }.onSuccess { status = label }.onFailure { error = it }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Messaging & Click to Pay", style = MaterialTheme.typography.headlineSmall)

        error?.let { ErrorCard(it) }
        status?.let { Card { Text(it, Modifier.padding(16.dp)) } }

        if (client == null) {
            Text("D1 is not configured.")
            return@Column
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Messaging", style = MaterialTheme.typography.titleSmall)
                Text(
                    "login() must succeed before registering for message notifications.",
                    style = MaterialTheme.typography.bodySmall,
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        run("Registered for message notifications.") {
                            client.messaging.register()
                        }
                    }) { Text("Register") }

                    OutlinedButton(onClick = {
                        run("Unregistered.") { client.messaging.unregister() }
                    }) { Text("Unregister") }

                    OutlinedButton(onClick = {
                        scope.launch {
                            error = null
                            runCatching { client.messaging.messages() }
                                .onSuccess { messages = it; status = "${it.size} message(s)." }
                                .onFailure { error = it }
                        }
                    }) { Text("List") }

                    OutlinedButton(
                        onClick = {
                            run("Marked as read.") {
                                client.messaging.markRead(messages.map(Message::getMessageID))
                            }
                        },
                        enabled = messages.isNotEmpty(),
                    ) { Text("Mark all read") }
                }

                messages.forEach { message ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            message.title ?: "(untitled)",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            listOfNotNull(
                                message.type?.name,
                                message.timeStamp,
                                if (message.isRead) "read" else "unread",
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Click to Pay", style = MaterialTheme.typography.titleSmall)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            error = null
                            runCatching { client.clickToPay.profiles() }
                                .onSuccess { result ->
                                    profiles = result.profileList.orEmpty()
                                    status = result.errorMessage
                                        ?: "${profiles.size} profile(s)."
                                }
                                .onFailure { error = it }
                        }
                    }) { Text("Get profiles") }

                    OutlinedButton(
                        onClick = {
                            run("Card opted out of Click to Pay.") {
                                client.clickToPay.optOutCard(cardId)
                            }
                        },
                        enabled = cardId.isNotBlank(),
                    ) { Text("Opt out card") }

                    OutlinedButton(onClick = {
                        run("Consumer opted out of Click to Pay.") {
                            client.clickToPay.optOutConsumer()
                        }
                    }) { Text("Opt out consumer") }
                }

                profiles.forEach { profile ->
                    LabelledValue(
                        "Profile",
                        runCatching { profile.toString() }.getOrDefault("-"),
                    )
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Push token", style = MaterialTheme.typography.titleSmall)
                Text(
                    "FCM is wired but inert until google-services.json is added. Prefix HMS " +
                        "tokens with \"HMS:\" so D1 routes them correctly.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
