---
title: Messaging and push
layout: default
parent: Services
nav_order: 5
---

# Messaging, push notifications, and Click to Pay
{: .no_toc }

1. TOC
{:toc}

---

## Push notifications

| Feature | Transport |
|:---|:---|
| NFC payment | FCM, or HMS Push Kit on Huawei devices without Google services |
| 3DS | FCM only |
| Cards issued by D1 | FCM |

Provide your FCM API key, and HMS Push Kit configuration where applicable, to
your Thales contact during onboarding.

### Registering the token

```kotlin
class D1FirebaseService : FirebaseMessagingService() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { client.push.updatePushToken(token) }
        }
    }
}
```

```kotlin
suspend fun updatePushToken(token: String) = awaitVoid { task.updatePushToken(token, it) }
```

Three requirements:

1. Call `login()` before `updatePushToken()` when using Click to Pay or the
   messaging service. NFC Wallet and 3DS require only a `Context`. When
   combining them, log in first.
2. Prefix HMS tokens with `HMS:` so D1 routes them to the correct push service.
3. Handle token renewal, not only first issuance.

`onNewToken` is deprecated from firebase-messaging 25.x but remains the token
renewal callback. Suppress with `OVERRIDE_DEPRECATION`.

### Processing notifications

Filter incoming messages so only D1 messages reach the SDK:

| Key | Values |
|:---|:---|
| `sender` | `CPS`, `MG`, `TNS` |
| `topic` | `D1_NOTIFICATION` |

```kotlin
companion object {
    private val SENDERS = setOf("CPS", "MG", "TNS")
    private const val TOPIC = "D1_NOTIFICATION"

    fun isD1Notification(data: Map<String, String>): Boolean =
        data["sender"] in SENDERS || data["topic"] == TOPIC
}
```

```kotlin
suspend fun processNotification(
    data: Map<String, String>,
): Map<PushResponseKey, String> = awaitCallback { task.processNotification(data, it) }
```

The response is keyed by `PushResponseKey`. `MESSAGE_TYPE` is always present;
`CARD_ID` and `LAST_CALL_TIMESTAMP` depend on the message type.

| Message type | `CARD_ID` | `LAST_CALL_TIMESTAMP` | Notes |
|:---|:--|:--|:---|
| `TYPE_CARD_STATE` | Yes | — | |
| `TYPE_REPLENISHMENT` | Yes | — | |
| `TYPE_TRANSACTION_NOTIFICATION` | Yes | Yes | |
| `TYPE_AUTHN` | — | — | |
| `TYPE_CARD_RENEWAL` | — | — | Re-synchronise the card identifier afterwards |
| `TYPE_MESSAGE_NOTIFICATION` | Yes | — | |
| `TYPE_UNKNOWN` | — | — | Not an SDK message; handle in the app |

When 3DS is enabled, create the `D1Authn` instance from the same `D1Task` before
calling `processNotification`, because authentication messages require an
`Activity` to prompt the user.

### Manifest and permissions

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service android:name=".push.D1FirebaseService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

Request `POST_NOTIFICATIONS` at runtime on API 33 and later.

The service and SDK integration compile without `google-services.json`. Firebase
initialises only once that file and the `google-services` plugin are added, so
push handling can be written before FCM provisioning is complete.

## Messaging service

Delivers issuer messages to the app. Requires an authenticated session.

```kotlin
suspend fun register()
suspend fun unregister()
suspend fun messages(): List<Message>
suspend fun markRead(messageIds: List<String>)
```

| Property | Description |
|:---|:---|
| `messageID` | Identifier |
| `title`, `message` | Content |
| `type` | `MessageType` |
| `format` | `TEXT` or `HTML` |
| `action` | Associated action |
| `isRead` | Read state |
| `timeStamp` | Delivery time |
| `metadata` | Additional data |

## Click to Pay

```kotlin
suspend fun enrol(
    cardId: String,
    consumer: ConsumerInfo,
    cardHolderName: String,
    billingAddress: BillingAddress,
): Status

suspend fun profiles(): ProfileResult
suspend fun updateCard(cardId: String, cardHolderName: String, billingAddress: BillingAddress): Status
suspend fun updateConsumer(consumer: ConsumerInfo, billingAddress: BillingAddress): Status
suspend fun optOutCard(cardId: String): Status
suspend fun optOutConsumer(): Status
```

`ConsumerInfo` carries first, middle, and last name, language, phone country
code, phone number, and email.

`ProfileResult` exposes `profileList` and `errorMessage`. Check `errorMessage`
even when the call succeeds; it reports partial failures.

{: .important }
> Click to Pay uses `BillingAddress` and `ConsumerInfo` from the
> `com.thalesgroup.gemalto.d1.clicktopay` package. Types with the same names
> exist in `com.thalesgroup.gemalto.d1.card` and are not interchangeable.

To push a card to the Click to Pay token requestor, see
[Push Provisioning](push-provisioning.html).
