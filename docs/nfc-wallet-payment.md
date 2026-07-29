---
title: NFC Wallet payments
layout: default
nav_order: 14
---

# NFC Wallet SDK — payments

Continues [NFC Wallet SDK](nfc-wallet-sdk.html).

---

## 9. Managing digital cards

### Two IDs for the same card

| ID | Created during | Used by |
|---|---|---|
| **Tokenized card ID** | Secure provisioning (CPS token ID) | `DigitalizedCardManager`, replenishment |
| **Digital card ID** | Digitization (MG card ID) | Access tokens, transaction history, LCM, card art |

Mixing them up is the single most common bug in this SDK. Convert explicitly
with `DigitalizedCardManager.getDigitalCardId()` and
`DigitalizedCardManager.getTokenizedCardId()`.

### List cards

`DigitalizedCardManager` offers a callback style (`AsyncHandler`) and a blocking
style (`AsyncToken`). **Never use the blocking form on the UI thread.**

```kotlin
val cardDisplayThread = HandlerThread("getAllCards")
cardDisplayThread.start()

val handler = object : AbstractAsyncHandler<Array<String>>(cardDisplayThread.looper) {
    override fun onComplete(result: AsyncResult<Array<String>>) {
        if (result.isSuccessful) {
            val tokenizedCardIds = result.result
        } else {
            // TODO: handle error
        }
    }
}

DigitalizedCardManager.getAllCards(handler)
```

Blocking equivalent:

```kotlin
val token = DigitalizedCardManager.getAllCards(null)
val result = token.waitToComplete()
```

### Card state and details

```kotlin
val digitalizedCard = DigitalizedCardManager.getDigitalizedCard(tokenizedCardId)

val statusResult = digitalizedCard.getCardState(null).waitToComplete()
if (statusResult.isSuccessful) {
    val status = statusResult.result
    val state = status.state                          // ACTIVE | SUSPENDED
    val numberOfPaymentsLeft = status.numberOfPaymentsLeft
    val needsReplenishment = status.needsReplenishment()
    val expiryDate = status.expiryDate                // LUK expiry date (null for SUK)
}

val detailsResult = digitalizedCard.getCardDetails(null).waitToComplete()
if (detailsResult.isSuccessful) {
    val details = detailsResult.result
    val lastFourDigits = details.lastFourDigits           // FPAN
    val lastFourDigitsOfDPAN = details.lastFourDigitsOfDPAN
    val panExpiry = details.panExpiry
    val scheme = details.scheme                            // Visa, Mastercard, PURE
}

try {
    val paymentAccountReference = digitalizedCard.paymentAccountReference
    // null if contactless data unavailable or PAR not present
} catch (e: InternalComponentException) { }
```

`SUSPENDED` means the card needs activation before it can pay — surface that in
the card list, not just on tap failure.

### Co-badged (auxiliary scheme) cards

```kotlin
if (digitalizedCard.hasAuxiliaryScheme()) {
    val details = digitalizedCard.getCardDetails(null).waitToComplete().result
    val auxiliaryScheme = details.auxiliaryScheme
    val auxiliaryLastFourDigitsOfDPAN = details.auxiliaryLastFourDigitsOfDPAN

    val status = digitalizedCard.getCardState(null).waitToComplete().result
    val auxiliaryNumberOfPaymentsLeft = status.auxiliaryNumberOfPaymentsLeft

    try {
        val auxiliaryPaymentAccountReference = digitalizedCard.auxiliaryPaymentAccountReference
    } catch (e: InternalComponentException) { }
}
```

Auxiliary keys deplete **independently** — check both counters when deciding
whether to replenish.

### Card art

```kotlin
fun getCardArt(context: Context, digitalCardId: String) {
    // First check if we already have the image locally
    val imageBytes = readFromFile(context, digitalCardId)
    if (imageBytes.isNotEmpty()) {
        val image = BitmapDrawable(context.resources,
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size))
        return
    }

    val gatewayManager = MobileGatewayManager.INSTANCE
    try {
        val cardArt = gatewayManager.getCardArt(digitalCardId)
        cardArt.getBitmap(CardArtType.CARD_BACKGROUND_COMBINED,
            object : MGAbstractAsyncHandler<CardBitmap>() {
                override fun onComplete(result: MGAsyncResult<CardBitmap>) {
                    if (result.isSuccessful) {
                        val value = result.result
                        writeToFile(context, digitalCardId, value.resource)   // cache it
                        val image = BitmapDrawable(context.resources,
                            BitmapFactory.decodeByteArray(value.resource, 0, value.resource.size))
                    }
                }
            })
    } catch (exception: NoSuchCardException) { }
}
```

Types: `BANK_LOGO`, `CARD_BACKGROUND`, `CARD_BACKGROUND_COMBINED`, `CARD_ICON`.
**Every retrieval triggers a network request** — cache to disk, as above.

Card metadata (TSP issuer name, TSP ID, PAR, TSP digital card ID) comes from
`MGCardEnrollmentService.getCardMetaData()`. The `tokenId` maps to
`tokenUniqueReference` (Mastercard/MDES) or `tokenReferenceID` (Visa/VTS).

### Default payment card

```java
public void setDefault(final DigitalizedCard digitalizedCard) {
    digitalizedCard.setDefault(
        PaymentType.CONTACTLESS,
        new AsyncHandlerVoid(new AsyncHandlerVoid.Delegate() {
            @Override
            public void onSuccess() { }

            @Override
            public void onError(final String error) { }
        })
    );
}
```

Verify the card is `ACTIVE` **and** has `getNumberOfPaymentsLeft() > 0` before
setting it default — otherwise you make an unusable card the tap target.

Also: `DigitalizedCardManager.getDefault()`, `unsetDefaultCard()`.

### Access tokens

```java
public void getAccessToken(final String digitalCardId) {
    final ProvisioningBusinessService provisioningService =
            ProvisioningServiceManager.getProvisioningBusinessService();

    provisioningService.getAccessToken(
            digitalCardId,
            GetAccessTokenMode.REFRESH,
            new AccessTokenListener() {
                @Override
                public void onSuccess(final String digitalCardId, final String accessToken) {
                    // Use for LCM services and transaction notifications.
                }

                @Override
                public void onError(final String digitalCardId, final ProvisioningServiceError error) { }
            }
    );
}
```

Tokens expire and must be refreshed. **Never log or persist them to disk.**

### Digital card lifecycle

```java
String accessToken = "....";

MGCardLifeCycleManager cardLifeCycleManager = MGClient.getCardLifeCycleManager();

cardLifeCycleManager.deleteCard(
    digitalCardId,
    new MGCardLifecycleEventListener() {
        @Override
        public void onSuccess(String digitalCardId) {
            // Request ACCEPTED — not yet applied.
            // The backend sends a push notification to complete the operation.
        }

        @Override
        public void onError(String digitalCardId, MobileGatewayError error) { }
    },
    null,
    null,
    accessToken);
```

Also `suspendCard(...)` and `resumeCard(...)`.

> **`onSuccess` only means the SDK accepted the request.** The actual state
> change arrives later by push. Don't update your UI to "deleted" here — mark it
> pending and let the CPS notification confirm.

Deleted cards may briefly report `RETIRED`, and `getAllCards()` can still return
them until local storage is cleaned.

---

## 10. Contactless payment (HCE)

### Implement the HCE service

Extend `AsyncHCEService`. **No methods are required** — both overrides below are
optional.

```java
public class MyHCEService extends AsyncHCEService {

    // Optional: capture APDU processing time.
    @Override
    public byte[] processCommandApdu(byte[] inputApdu, Bundle bundle) {
        // Returns the SDK value (always 'null'). APDU processing is asynchronous;
        // the SDK sends the response APDU to the POS terminal itself.
        return super.processCommandApdu(inputApdu, bundle);
    }

    // Optional: inspect or override the response APDU.
    @Override
    public boolean onApduResponse(final byte[] inputApdu, final Bundle extras, final byte[] responseApdu) {
        // To override: modify responseApdu, call sendResponseApdu(...), return true.
        // Otherwise return false and let the SDK send it.
        return false;
    }
}
```

Manifest:

```xml
<service android:name="com.mycompany.myapplication.myservices.MyHCEService"
  android:exported="true"
  android:label="@string/app_name"
  android:permission="android.permission.BIND_NFC_SERVICE">
  <intent-filter>
    <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" />
  </intent-filter>
  <meta-data
    android:name="android.nfc.cardemulation.host_apdu_service"
    android:resource="@xml/apduservice"/>
</service>
```

`res/xml/apduservice.xml` — the PPSE AID is **required**; scheme AIDs depend on
your programme, so **confirm the list with your payment network representative**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<host-apdu-service xmlns:android="http://schemas.android.com/apk/res/android"
  android:description="@string/hce_service_description"
  android:requireDeviceUnlock="false"
  android:apduServiceBanner="@drawable/hce_banner">
  <aid-group
    android:description="@string/aid_description"
    android:category="payment">
    <!-- required PPSE AID -->
    <aid-filter android:name="325041592E5359532E4444463031"/>
    <!-- Mastercard AIDs -->
    <aid-filter android:name="A0000000041010"/>
    <aid-filter android:name="A0000000043060"/>
    <aid-filter android:name="A0000000042010"/>
    <!-- Visa AIDs -->
    <aid-filter android:name="A0000000031010"/>
    <aid-filter android:name="A0000000980840"/>
    <aid-filter android:name="A0000000032020"/>
    <aid-filter android:name="A0000000032010"/>
  </aid-group>
</host-apdu-service>
```

`android:requireDeviceUnlock="false"` is what allows pay-from-lock-screen.
Verify registration in device **Settings → Tap and Pay**.

> **Danger:** the SDK suspends APDU processing during authentication. Some
> devices block background `Activity` launches, so your step-up prompt never
> appears and the transaction hangs. **Implement a timeout that calls
> `deactivate()`** to unblock.

### Payment callbacks

```java
PaymentServiceListener myPaymentListener = new ContactlessPaymentServiceListener() {

    @Override
    public void onTransactionStarted() {
        // First APDU exchanged with the POS terminal.
    }

    @Override
    public void onAuthenticationRequired(PaymentService activatedPaymentService,
                                          CHVerificationMethod chVerificationMethod,
                                          long cvmResetTimeout) {
        // Use chVerificationMethod to pick the auth UI.
        // cvmResetTimeout tells the user how long verification stays valid.
    }

    @Override
    public void onReadyToTap(PaymentService paymentService) {
        // Authentication succeeded. Prompt to tap again before cvmResetTimeout expires.
    }

    @Override
    public void onTransactionCompleted(TransactionContext transactionContext) { }

    @Override
    public void onTransactionInterrupted() {
        // NFC link lost. Prompt the end user to tap again. Optional callback.
    }

    @Override
    public void onError(TransactionContext transactionContext,
                        PaymentServiceErrorCode paymentServiceErrorCode,
                        String message) { }

    @Override
    public void onFirstTapCompleted() {
        // PFP-based transactions only.
    }

    @Override
    public void onNextTransactionReady(DeactivationStatus deactivationStatus,
                                       DigitalizedCardStatus digitalizedCardStatus,
                                       DigitalizedCard digitalizedCard) {
        // Verify card state and prepare for the next payment.
    }
};
```

Return it from the HCE service:

```java
public class MyHCEService extends AsyncHCEService {
    @Override
    public PaymentServiceListener setupListener() {
        return myPaymentListener;
    }
}
```

Typical order:

```
onTransactionStarted → onAuthenticationRequired → onReadyToTap
    → onTransactionCompleted → onNextTransactionReady
```

`onTransactionInterrupted()` can fire any time after start; `onError()` replaces
the success path.

**POS disconnect tuning:** `PaymentSetting.setRetryLimit(int)` defaults to `0`,
meaning `onError()` fires on the very first disconnect.
`PaymentSetting.setTransactionRetryTimeout(long)` accepts 500–10000 ms
(default 2000). If field testing shows spurious failures on flaky terminals,
raise the retry limit before blaming the SDK.

### One-tap, two-tap, and pre-entry

Set the experience at startup:

```java
PaymentExperienceSettings.setPaymentExperience(this, PaymentExperience.ONE_TAP_ENABLED);
```

Options: `ONE_TAP_ENABLED`, `TWO_TAP_ALWAYS`. One-tap **falls back to two-tap**
when the user doesn't tap within `keyValidityPeriod`, or when a low-value
transaction crosses a risk threshold requiring authentication. Your UI must
handle both without looking broken.

**Pre-entry** lets the user authenticate at unlock time (one payment per
unlock):

```java
private void activatePreEntry() {
    DeviceCVMPreEntryReceiver receiver = new DeviceCVMPreEntryReceiver();
    receiver.init();
    IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
    registerReceiver(receiver, filter);
}
```

> Do **not** add any intent filter to `DeviceCVMPreEntryReceiver` other than
> `ACTION_USER_PRESENT`.

### Manual mode (user picks a non-default card)

```java
// 01 - Temporarily change the default card to the selected card, if necessary.
DigitalizedCard originalDefault = null;
DigitalizedCard selectedCard = getSelectedCard();

if (!isDefault(selectedCard)) {
    originalDefault = getDefaultCard();
    setDefaultCard(selectedCard);
}

// 02 - Listener
private PaymentServiceListener paymentServiceListener = new ContactlessPaymentServiceListener() {
    @Override
    public void onAuthenticationRequired(PaymentService paymentService,
                                          CHVerificationMethod cvm, long cvmResetTimer) {
        startInputCvmActivity(cvm);
    }

    @Override
    public void onTransactionCompleted(TransactionContext ctx) {
        setDefaultCard(originalDefault);   // restore
    }

    @Override
    public void onError(TransactionContext transactionContext,
                        PaymentServiceErrorCode errorCode, String msg) {
        setDefaultCard(originalDefault);   // restore
    }

    @Override public void onTransactionStarted() { }
    @Override public void onReadyToTap(PaymentService service) { }
};

// 03 - Trigger authentication prior to payment.
final PaymentBusinessService paymentBusinessService = PaymentBusinessManager.getPaymentBusinessService();
paymentBusinessService.startAuthentication(paymentServiceListener, PaymentType.CONTACTLESS);
```

Restore the original default in **both** `onTransactionCompleted` and `onError`,
plus on timeout — otherwise the user's chosen default silently changes.

### CDCVM verification

Handle in `onAuthenticationRequired`, launching a dedicated activity:

```java
@Override
public void onAuthenticationRequired(PaymentService activatedPaymentService,
                                     CHVerificationMethod cvm,
                                     long cvmResetTimeout) {
    if (cvm == CHVerificationMethod.DEVICE_KEYGUARD) {
        Intent intent = new Intent(getApplicationContext(), KeyguardActivity.class);
        intent.putExtra(Tags.CVM, cvm);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
```

The activity **must extend `DeviceCVMKeyguardActivity`** (this is also what
enables keyguard fallback from biometrics):

```java
public class KeyguardActivity extends DeviceCVMKeyguardActivity {

     private PaymentBusinessService paymentBusinessService;
     private DeviceCVMVerifier chDeviceCVMVerifier;

     @Override
     protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_device_keyguard);
      unlockAndWake();

      CharSequence title = getString(R.string.keyguard_title);
      CharSequence message1 = getString(R.string.keyguard_message);

      Bundle extras = getIntent().getExtras();
      CHVerificationMethod cvm = (CHVerificationMethod) extras.getSerializable(Tags.CVM);

      paymentBusinessService = PaymentBusinessManager.getPaymentBusinessService();
      PaymentService paymentService = paymentBusinessService.getActivatedPaymentService();

      chDeviceCVMVerifier = (DeviceCVMVerifier) paymentService.getCHVerifier(cvm);

      chDeviceCVMVerifier.setDeviceCVMVerifyListener(new DeviceCVMVerifyListener() {
          @Override
          public void onVerifySuccess() {
             KeyguardActivity.this.finish();
          }

          @Override
          public void onVerifyError(int errorCode, CharSequence charSequence) {
              // Not expected for keyguard
          }

          @Override
          public void onVerifyFailed() {
              // Ask the user to retry
          }

          @Override
          public void onVerifyHelp(int i, CharSequence charSequence) { }
      });

      chDeviceCVMVerifier.setKeyguardActivity(this);

      // Start authentication only when the screen is ON and unlocked
      if (DeviceUtil.isDeviceScreenOn(getApplicationContext())) {
             DeviceCVMVerifierInput input = new DeviceCVMVerifierInput(title, message1);
             chDeviceCVMVerifier.startAuthentication(input);
      }
     }
}
```

For **biometrics**, the same structure applies with a `CancellationSignal`, plus
two error cases worth special handling:

- `FingerprintManager.FINGERPRINT_ERROR_CANCELED` — fires in lock-screen mode;
  cancel the old signal, create a new one, restart authentication.
- `FingerprintManager.FINGERPRINT_ERROR_LOCKOUT` — too many attempts; fall back
  to keyguard via `confirmCredential(...)`.

On cancel or back-press, **deactivate the payment service**:

```java
private void cancelTransaction(String message) {
    if (cancellationSignal != null) {
        cancellationSignal.cancel();
    }
    PaymentBusinessManager.getPaymentBusinessService().deactivate();
}
```

Stop listening when backgrounded:

```java
@Override
public void onResume() {
    super.onResume();
    cancellationSignal = new CancellationSignal();
    deviceCVMVerifier.startAuthentication(cancellationSignal);
}

@Override
public void onPause() {
    if (cancellationSignal != null) cancellationSignal.cancel();
}
```

**Lock-screen support:**

```xml
<activity android:name=".BioFingerprintActivity"
          android:showOnLockScreen="true"
          android:screenOrientation="portrait" />
```

```java
private void unlockAndWake() {
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    if (!pm.isScreenOn()) {
        mWl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.FULL_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "");
        mWl.acquire();
    }

    getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON   |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
}
```

Release the wake lock in `onDestroy()`.

**Delegated authentication** — if your app authenticated the user itself:

```java
@Override
public void onAuthenticationRequired(final PaymentService service,
                                     CHVerificationMethod cvm, long cvmResetTimeout) {
   // Only applicable for Biometric or KeyGuard method
   if (cvm == CHVerificationMethod.BIOMETRICS || cvm == CHVerificationMethod.DEVICE_KEYGUARD) {
      final DeviceCVMVerifier verifier = (DeviceCVMVerifier) service.getCHVerifier(cvm);

      verifier.setCVMType(CVMType.FINGERPRINT);
      verifier.onDelegatedAuthPerformed(timeOfAuth);
      // or, if the user declines:
      verifier.onDelegatedAuthCancelled();
   }
}
```

The SDK compares `timeOfAuth` against the key validity duration — check there is
enough time left for the second tap before delegating.

### Display the transaction

`TransactionContext` holds **sensitive data**. Always `wipe()` it in a `finally`:

```java
@Override
public void onTransactionCompleted(TransactionContext ctx) {
    try {
        TransactionData data = TransactionData.from(ctx);
        // Display data in your UI.
    } finally {
        ctx.wipe();
    }
}

@Override
public void onError(TransactionContext ctx, PaymentServiceErrorCode errorCode, String message) {
    if (ctx == null) {
        // Handle the error when no transaction context is available.
        return;
    }
    try {
        TransactionData data = TransactionData.from(ctx);
    } finally {
        ctx.wipe();
    }
}
```

`ctx` **can be null** in `onError` — null-check before wiping.

Fields are BCD-encoded, so decode before display:

```java
public static TransactionData from(TransactionContext ctx) {
    String currencyCode = TransactionDisplayUtils.bcdToString(ctx.getCurrencyCode());
    String transactionDate = TransactionDisplayUtils.bcdToString(ctx.getTrxDate());
    String transactionType = TransactionDisplayUtils.getTransactionType(ctx.getTrxType());

    return new TransactionData(currencyCode, ctx.getAmount(),
            transactionDate, transactionType, ctx.getTrxId());
}

public static String bcdToString(byte bcd) {
    int high = (bcd & 0xF0) >>> 4;
    int low = (bcd & 0x0F);
    return String.valueOf(high) + low;
}

public static String getTransactionType(byte trxType) {
    switch (trxType) {
        case 0:  return "PAY";
        case 32: return "REFUND";
        default: return "TRANSACTION";
    }
}
```

---

## 11. QR code payment

**Thales white-label EMV PURE cards only.** Requires tokenization first.

```java
public boolean isQRCodeSupported(DigitalizedCardDetails card) {
    final PaymentType[] supported = card.paymentTypeSupported();
    for (PaymentType p : supported) {
        if (p == PaymentType.QR) return true;
    }
    return false;
}
```

```java
PaymentInputData paymentInputData = new PaymentInputData.PaymentInputBuilder(PaymentType.QR)
                .withQRCodePaymentParameters(amount, currencyCode, countryCode)
                .withPureQRCodePaymentParameters(idd.getBytes(), aid.getBytes())
                .build();

final PaymentBusinessService pbs = PaymentBusinessManager.getPaymentBusinessService();
pbs.generateApplicationCryptogram(PaymentType.QR, paymentInputData, qrCodePaymentServiceListener);
```

> The published sample for this call contains curly quotes and mismatched
> variable names. Retype it rather than pasting — and validate field formats
> against the table below.

| Field | Format |
|---|---|
| `aid` | hex, 5–16 bytes; `"0000000000"` = use primary AID |
| `amount` | BCD hex, 6 bytes |
| `currencyCode` | numeric 3, ISO 4217 |
| `countryCode` | numeric 3, ISO 3166-1 |
| `idd` | hex, 15 bytes, optional |

`QRCodePaymentServiceListener` gives you `onAuthenticationRequired`,
`onDataReadyForPayment`, `onError`, `onNextTransactionReady`. Retrieve output
with `paymentService.getQRCodeData()`, then render the payload (Base64-encoded
cryptogram) with a library such as ZXing.

`QRCodeData` fields: `statusWord`, `cid`, `chipDataField`,
`condensedPaymentData` (n/a), `cardMainAid`, `cardMainAppTemplate`,
`cardAliasAid`, `cardAliasAppTemplate`, `commonDataTemplate`.

| Status word | Meaning |
|---|---|
| `9000` | Success — all fields available if `cid` is `0x8x` format |
| `6989` | Customer verification required, no method in Application Control |
| `6988` | Zero transaction amount not allowed |
| `6987` | Amount exceeds issuer-defined limit |
| `6986` | Amount exceeds end-user-defined limit |
| `6985` | ATC limit reached, or AID not compliant |

| Error code | Action |
|---|---|
| `NO_DEFAULT_CARD` | Set a default card first |
| `QR_CODE_PAYMENT_NOT_SUPPORTED` | Verify `PaymentType.QR` in `paymentTypeSupported()` |
| `QR_CODE_WRONG_STATE` | Payment service already active — call `deactivate()` on CDCVM cancel |
| `QR_CODE_INPUT_INVALID` | Fix input fields |
| `QR_CODE_OUTPUT_INVALID` | Treat as failed |
| `CARD_OUT_OF_PAYMENT_KEYS` | Replenish first |

Null input or a wrong listener type throws `IllegalArgumentException`.

---

## 12. DSRP remote payment

**Mastercard digital cards with MCBP 2.x profile only.** Requires tokenization.

```java
public boolean isDsrpSupported(DigitalizedCardDetails card) {
    final PaymentType[] supported = card.paymentTypeSupported();
    for (PaymentType p : supported) {
        if (p == PaymentType.DSRP) return true;
    }
    return false;
}
```

```java
long amount = 11900;          // minor units. Example: 119.00
char currencyCode = 702;      // SGD (ISO 4217 numeric)
char countryCode = 702;       // Singapore (ISO 3166-1 numeric)
TransactionType transactionType = TransactionType.PURCHASE;
long unpredictableNumber = 12345;

PaymentInputData paymentInputData = new PaymentInputData.PaymentInputBuilder(PaymentType.DSRP)
                .withRemotePaymentParameters(amount, currencyCode)
                .withMCRemotePaymentParameters(countryCode, transactionType,
                                               CryptogramDataType.DE55, unpredictableNumber)
                .build();

final PaymentBusinessService pbs = PaymentBusinessManager.getPaymentBusinessService();
pbs.generateApplicationCryptogram(PaymentType.DSRP, paymentInputData, remotePaymentServiceListener);
```

All six fields are **required**. `cryptogramDataType` is `UCAF` or `DE55`.

```java
@Override
public void onDataReadyForPayment(PaymentService paymentService, TransactionContext transactionContext) {
    RemotePaymentOutputData remotePaymentData = paymentService.getRemotePaymentData();
    // send remotePaymentData.getCryptogramData() to the merchant/backend
    deactivatePaymentService();
}
```

> **`deactivate()` after every generation.** Skip it and the next call fails
> with `REMOTE_PAYMENT_WRONG_STATE`. Deactivate on `onError` too, before retry.

`RemotePaymentOutputData`: `cryptogramData` (UCAF or DE-55 TLV), `dpan`,
`dpanSequenceNumber`, `track2EquivalentData` (ISO/IEC 7813, no sentinels/LRC),
`PAR`, `dPanexpirationDate`, `cryptogramDataType`.

| Error code | Action |
|---|---|
| `REMOTE_PAYMENT_WRONG_STATE` | `deactivate()` after each generation / on CDCVM cancel |
| `REMOTE_PAYMENT_OUTPUT_INVALID` | Retry |
| `REMOTE_PAYMENT_NOT_SUPPORTED` | Check `paymentTypeSupported()` |
| `REMOTE_PAYMENT_INPUT_INVALID` | Rebuild input data |
| `NO_DEFAULT_CARD` | Set a default card |
| `CARD_OUT_OF_PAYMENT_KEYS` | Replenish first |

---

## 13. Transaction history

```java
MobileGatewayManager mgClient = MobileGatewayManager.INSTANCE;
MGTransactionHistoryService transactionHistoryService = mgClient.getTransactionHistoryService();

transactionHistoryService.refreshHistory(
        accessToken,
        digitalCardId,
        null,
        new TransactionHistoryListener() {
            @Override
            public void onSuccess(List<MGTransactionRecord> records,
                                  String digitalCardId, String timeStamp) { }

            @Override
            public void onError(String digitalCardId, MobileGatewayError error) { }
        }
);
```

An overload takes `transactionRecordType` (`PRIMARY` / `AUXILIARY`) for
co-badged cards. Each record carries transaction ID, date, type, status,
currency, amount + display amount, merchant name/type/postal code, terminal ID,
merchant ID, and the primary/auxiliary indicator.

**Limits are set by the payment network** — a time window (e.g. 30 days) and/or
a max count (e.g. 10 transactions). Don't present it as a full statement.

---

## 14. Payment key replenishment

| Key type | Scheme | Behaviour |
|---|---|---|
| **SUK** (Single Use Key) | Mastercard, PURE | One key per transaction |
| **LUK** (Limited Use Key) | Visa | Serves multiple transactions |

Thresholds are agreed with Thales at onboarding: remaining SUK count for SUK;
remaining transaction count **plus LUK expiration** for LUK.

```java
public static boolean needsReplenishment(final DigitalizedCard card) {
    AsyncResult<DigitalizedCardStatus> result = card.getCardState(null).waitToComplete();
    if (!result.isSuccessful()) return false;

    DigitalizedCardStatus status = result.getResult();
    return status != null && status.needsReplenishment();
}
```

```java
public void replenish(final String tokenizedCardId, final boolean forced) {
    ProvisioningBusinessService service = ProvisioningServiceManager.getProvisioningBusinessService();
    service.sendRequestForReplenishment(tokenizedCardId, new ReplenishmentListener(), forced);
}
```

Note this takes the **tokenized card ID**, not the digital card ID.

Check for replenishment:

- at app startup — **but not when the app was launched to pay**; defer it
- after a payment, in `onNextTransactionReady`
- when a card is set as default
- when connectivity returns

Avoid terminating the app mid-replenishment.

---

## 15. Reset the SDK

```java
SDKDataController.INSTANCE.wipeAll(appContext);
```

Deletes **all** local SDK data — digital cards and payment credentials.

> This is **local only**. No server call is made, so server-side tokens are
> orphaned (cleaned up on next tokenization). The wallet ID is regenerated on
> next init, so you **must run wallet secure enrollment again** before any new
> tokenization.

---

Next: [Deployment](deployment.html).
