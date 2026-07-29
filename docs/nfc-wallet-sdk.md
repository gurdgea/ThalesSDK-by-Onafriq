---
title: NFC Wallet SDK
layout: default
nav_order: 13
---

# NFC Wallet SDK — setup and enrollment

For building your **own-brand HCE wallet** where cards live in *your* app and
the user taps *your* app to pay. Package `com.gemalto.mfs.mwsdk`.

> **Naming:** this SDK was previously **TSH Pay SDK**. Delivered AAR filenames
> still start with `TSHPaySDK-`. Same product.

Integration order:

```
Gradle → Onboarding + properties files → Manifest → SDK init → Mobile Gateway init
      → Wallet secure enrollment → Push notifications → Tokenization → CDCVM → HCE payment
```

---

## 1. Deliverables and Gradle

Package contains AAR libraries plus API documentation. Naming:
`TSHPaySDK-<build type>-<version>.<qualifier>.aar`.

| Build type | Use |
|---|---|
| `release` | Production and certification builds |
| `dev` | Development/testing **only** — enables SDK logging, HTTP inspection, debugging |

**Never ship `dev` to production.** A production build that initializes with the
`dev` library fails with `DEBUG_SDK_USED`. For production also set
`debuggable false` in `build.gradle` and `android:debuggable="false"` in the
manifest.

Supported: **Android 8.1 – 16.0**, ABIs `armeabi-v7a` and `arm64-v8a`.

```
./dependencies
 ├── TSHPaySDK-dev-[version].[qualifier].aar
 └── TSHPaySDK-release-[version].[qualifier].aar
```

`settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://developer.huawei.com/repo/' }
        flatDir { dirs "$rootDir/dependencies" }
    }
}
```

`app/build.gradle`:

```groovy
def nfcWalletSdkVersion = "[version].[qualifier]"

dependencies {
    debugImplementation(name: "TSHPaySDK-dev-${nfcWalletSdkVersion}", ext: "aar")
    releaseImplementation(name: "TSHPaySDK-release-${nfcWalletSdkVersion}", ext: "aar")

    // JNA — 5.17.0+ required for 16 KB memory page support
    implementation(libs.jna) { artifact { type = 'aar' } }

    // FCM - Google push notifications
    implementation platform(libs.firebase.bom)
    implementation libs.firebase.messaging
    implementation libs.firebase.analytics

    // Multi-dex application to prevent any size issue.
    implementation libs.multidex
}
```

Enable **ABI splits** to keep APK size down — you are shipping native code for
two architectures.

---

## 2. Onboarding parameters

**You send Thales:**

| Parameter | Format | Purpose |
|---|---|---|
| PreProd / Production FCM service account | JSON file | Push notifications |
| PreProd / Production HMS Push Kit config | see Huawei docs | Optional — Huawei devices |
| Application binding key | hex string | Binds wallet enrollment requests to your app |

**Thales sends you:**

| Parameter | Format |
|---|---|
| PreProd / Production source IP addresses (to allowlist in FCM) | comma-separated IPv4/IPv6 |
| PreProd / Production URLs (Thales backend endpoints) | string |
| PreProd / Production card information encryption key | hex string or PEM certificate |

> **Signing key rotation is a trap.** The application binding key is computed
> from your app signing certificate. If you rotate keys or use multiple signers,
> the **oldest signing key must be the first signer** in the proof-of-rotation
> struct — otherwise wallet enrollment fails outright, and so does downgrading
> to an earlier SDK version.

### Properties files (`assets/`)

Three files are mandatory:

**`mobilegateway.properties`**

| Key | Recommended |
|---|---|
| `MG_CONNECTION_URL` | from Thales |
| `MG_TRANSACTION_HISTORY_CONNECTION_URL` | from Thales |
| `WALLET_PROVIDER_ID` | from Thales |
| `WALLET_APPLICATION_ID` | optional |
| `MG_CONNECTION_TIMEOUT` | `30000` |
| `MG_CONNECTION_READ_TIMEOUT` | `30000` |
| `MG_RETRY_COUNTER` | `3` |
| `MG_RETRY_INTERVAL` | `10000` |

**`rages.properties`**

| Key | Recommended |
|---|---|
| `REALM` | `CBP` (fixed) |
| `OAUTH_CONSUMER_KEY` | from Thales |
| `RAGES_GATEWAY_URL` | from Thales |
| `RAGES_CONNECTION_TIMEOUT` | `30000` |
| `CSR_DOMAIN` / `CSR_EMAIL` | from Thales |

**`gemcbp.properties`**

| Key | Recommended |
|---|---|
| `CPS_URL` | from Thales |
| `CPS_CONNECTION_TIMEOUT` | `30000` |
| `CPS_READ_TIMEOUT` | `30000` |

---

## 3. Manifest

| Permission | Requirement |
|---|---|
| `android.permission.INTERNET` | Required — enrollment, key replenishment |
| `android.permission.NFC` | Required — contactless payment |
| `android.permission.USE_BIOMETRIC` | Conditional — biometric CDCVM |
| `android.permission.USE_FINGERPRINT` | Conditional — Android 9 and earlier |

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.NFC" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.USE_FINGERPRINT" />

    <uses-feature android:name="android.hardware.nfc" android:required="true" />
    <uses-feature android:name="android.hardware.nfc.hce" android:required="true" />

    <application android:allowBackup="false">

        <!-- CPS communication service -->
        <service
            android:name="com.gemalto.mfs.mwsdk.provisioning.push.CPSCommService"
            android:enabled="true"
            android:exported="false" />

        <service
            android:name=".payment.contactless.YourHostApduService"
            android:exported="true"
            android:permission="android.permission.BIND_NFC_SERVICE">
            <intent-filter>
                <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data
                android:name="android.nfc.cardemulation.host_apdu_service"
                android:resource="@xml/apduservice" />
        </service>

    </application>
</manifest>
```

`android:allowBackup="false"` is **required** — never let payment credentials
into Android backup.

`required="true"` on the NFC features filters non-NFC devices out of Play. If
you'd rather ship to everyone and degrade gracefully, set `required="false"`
and check at runtime:

```java
public static boolean doesDeviceSupportHCE(@NonNull final Context context) {
    final PackageManager pm = context.getPackageManager();
    final boolean hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    final boolean supportsHce = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);

    if (!hasNfc || !supportsHce) {
        return false;
    }
    return true;
}
```

---

## 4. SDK initialization

Two prerequisite levels govern every API in this SDK:

| Level | Achieved by | Grants |
|---|---|---|
| **SDK configuration** | `SDKInitializer.INSTANCE.configure(...)`, or the moment `initialize(...)` starts | APIs that only read local SDK storage |
| **SDK initialization** | `SDKInitializer.INSTANCE.initialize(...)` **completed** | Everything else |

Calling an API below its prerequisite doesn't always throw — several return
`NULL_CONTEXT` / `SDK_NOT_INITIALIZED` error codes with empty results, and a few
throw `NullPointerException`. See the prerequisite tables in
[Deployment](deployment.html).

### `CustomConfiguration`

```java
CustomConfiguration customConfig = new CustomConfiguration.Builder()
                .domesticCurrencyCode(978)
                .keyValidityPeriod(60)
                .build();
```

| Field | Meaning | Range / default |
|---|---|---|
| `keyValidityPeriod` | Seconds between end-user authentication and the POS tap | 0–300, default 45 |
| `domesticCurrencyCode` | ISO 4217 numeric code for CDCVM during low-value transactions | default 978 (EUR) |

> **Do not change `keyValidityPeriod` or `domesticCurrencyCode` after the first
> initialization.**

### Application startup

```java
public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        CustomConfiguration customConfig = new CustomConfiguration.Builder()
                .domesticCurrencyCode(978)
                .keyValidityPeriod(60)
                .build();

        // 1 - Quick configuration: make sure the SDK has an Android Context.
        try {
            SDKInitializer.INSTANCE.configure(this, customConfig);
        } catch (InternalComponentException e) {
            // Log and continue to initialize().
        } catch (Exception e) {
            // Safeguard against crashes during Application startup. Log and continue.
        } catch (Throwable e) {
            // Unlikely. If it happens, do NOT call initialize().
            return;
        }

        // 2 - Set the payment experience.
        PaymentExperienceSettings.setPaymentExperience(this, PaymentExperience.ONE_TAP_ENABLED);

        // 3 - Initialize the SDK on a background thread (initialize() is synchronous).
        Thread sdkInit = new Thread(new Runnable() {
            @Override
            public void run() {
                SDKInitializer.INSTANCE.initialize(getApplicationContext(), customConfig);
            }
        });
        sdkInit.start();

        // 4 - Activate pre-entry (optional).
        activatePreEntry();
    }
}
```

The three-level `catch` is deliberate: this runs in `Application.onCreate()`, so
an uncaught `Throwable` takes down the whole app. Note the asymmetry — on
`Throwable` you must **not** proceed to `initialize()`.

Guard against redundant initialization:

```java
if (SDKController.getInstance().getSDKServiceState() != SDKServiceState.STATE_INITIALIZED) {
    // ... start the init thread
}
```

`initialize()` is idempotent, but it is also **synchronous** — never call it on
the UI thread. `configure()` does **not** run SDK migration; migration happens
during `initialize()`.

### Cold-start budget

If the device kills your app in the background, a tap has to cold-start it:
~100–200 ms for service binding, plus however long `Application.onCreate()`
takes. **Minimise work in `onCreate()`**, and avoid scheduling other work in the
first 0.5 s after it starts. Every millisecond here is a failed tap.

### Mobile Gateway

Required for tokenization, digital card LCM, and transaction history:

```java
protected void initMgSdk(@NonNull final Context context) {
    final MobileGatewayManager mgManager = MobileGatewayManager.INSTANCE;

    try {
        // Avoid multiple initialization.
        if (mgManager.getConfigurationState() == MGSDKConfigurationState.NOT_CONFIGURED) {
            mgManager.configure(context);
        }
    } catch (final MGConfigurationException exception) {
        // Log error.
    }
}
```

### Wallet ID

```java
MGCardEnrollmentService enrollService = MobileGatewayManager.INSTANCE.getCardEnrollmentService();
String walletId = enrollService.getWalletId();
```

Generated on first init, stable across restarts, **regenerated on SDK reset**.
Throws `MGSDKException` on error.

---

## 5. Wallet secure enrollment (WSE)

Run **once per wallet instance**, after SDK init, before any tokenization.

```java
public void performWseIfNeeded() {
    final WalletSecureEnrollmentBusinessService wseService
                = ProvisioningServiceManager.getWalletSecureEnrollmentBusinessService();
    final WalletSecureEnrollmentState state = wseService.getState();

    switch (state) {
        case WSE_COMPLETED:
        case WSE_NOT_REQUIRED:
            // Already done in this or a previous instance.
            break;
        case WSE_STARTED:
            // Triggered during this instance. Wait for the first one to finish.
            return;
        case WSE_REQUIRED:
            wseService.startWalletSecureEnrollment(new WalletSecureEnrollmentListener() {
                @Override
                public void onProgressUpdate(final WalletSecureEnrollmentState wseState) {
                    if (wseState == WalletSecureEnrollmentState.WSE_COMPLETED) {
                        // Success
                    } else if (wseState == WalletSecureEnrollmentState.WSE_STARTED) {
                        // Started
                    }
                }

                @Override
                public void onError(final WalletSecureEnrollmentError error) {
                    // Log error
                }
            });
            break;
        default:
            break;
    }
}
```

`WSE_STARTED` means *another call is already in flight* — return, don't retry.

### Parsing `WalletSecureEnrollmentError`

`getSdkErrorCode()` → `WalletSecureEnrollmentErrorCodes`. Then, depending on the
code: `getHttpStatusCode()` for `COMM_ERROR`, `getCpsErrorCode()` for
`SERVER_ERROR`, `getErrorMessage()` always, and `getStatusAdditionalInfo()` for
`DEVICE_SUSPICIOUS`.

| Error code | When | Action |
|---|---|---|
| `WSE_INTERNAL_ERROR` | Internal SDK error | Retry; reset SDK if persistent |
| `COMMON_NO_INTERNET` | No network | Reconnect, retry |
| `COMMON_COMM_ERROR` | Comms error fetching security assets | Retry |
| `COMMON_SERVER_ERROR` | Server-side error | Retry; escalate to Thales if persistent |
| `RE_ENROLLMENT_REQUIRED` | Security re-enrollment needed | **Reset SDK**, enroll again |
| `WSE_STORAGE_ACCESS_ERROR` | Secure storage retry limit exceeded | Reset SDK, retry |
| `JSON_PARSING_ERROR` | Unparseable response | Retry; reset if persistent |
| `WSE_REQUEST_ERROR` | Enrollment request failed | Retry |
| `WSE_DOWNLOAD_ERROR` | Asset download failed | Check network, retry |
| `WSE_ERROR_INIT_SESSION` | Session init failed (auth) | Retry |
| `WSE_ERROR_COMPUTE_AUTH_VALUE_FAILED_PACKAGE_NOT_FOUND` | Package name unresolvable | **Check onboarding package name** |
| `WSE_ERROR_COMPUTE_AUTH_VALUE_FAILED_CERT_EXCEPTION` | Signing/public key mismatch | **Check onboarding signing cert** |
| `WSE_CPS_COMPONENT_NOT_INITIALIZED` | Called before CPS init | Initialize SDK, retry |
| `WSE_MG_COMPONENT_NOT_INITIALIZED` | Called before MG init | Initialize SDK, retry |
| `DEVICE_SUSPICIOUS` | Device threat detected | **Stop.** Inform user; capture `getStatusAdditionalInfo()` for support |
| `WSE_KCV_ERROR` | Asset KCV validation failed | Retry |

"Reset SDK" = `SDKDataController.INSTANCE.wipeAll(appContext)`.

The two `COMPUTE_AUTH_VALUE_FAILED_*` codes and `DEVICE_SUSPICIOUS` are the ones
worth wiring to distinct support paths — the rest are retry-or-reset.

---

## 6. Push notifications

Implement `FirebaseMessagingService` (and/or Huawei's `HmsMessageService`), and
route everything through one handler.

```java
public class FcmService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull final String token) {
        super.onNewToken(token);
        updateToken(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull final RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        processIncomingMessage(this, remoteMessage.getData());
    }
}
```

Huawei — note the **`HMS:` prefix** and `getDataOfMap()`:

```java
public class HmsService extends HmsMessageService {

    private static final String HMS_TOKEN_PREFIX = "HMS:";

    @Override
    public void onNewToken(final @NonNull String token) {
        super.onNewToken(token);
        updateToken(this, HMS_TOKEN_PREFIX + token);
    }

    @Override
    public void onMessageReceived(@NonNull final RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        processIncomingMessage(this, remoteMessage.getDataOfMap());
    }
}
```

### Update the push token

```java
void updateToken(@NonNull final Context context, @Nullable final String token) {
    final ProvisioningBusinessService provisioningService =
                 ProvisioningServiceManager.getProvisioningBusinessService();
    provisioningService.updatePushToken(token, new PushServiceListener() {
        @Override public void onComplete() { /* Success */ }
        @Override public void onError(final ProvisioningServiceError error) { }
        @Override public void onUnsupportedPushContent(final Bundle bundle) { }
        @Override public void onServerMessage(final String message,
                                              final ProvisioningServiceMessage psm) { }
     });
}
```

### Route by `sender`

| `sender` | Meaning | Handler |
|---|---|---|
| `CPS` | Digital card operations (LCM) | `ProvisioningBusinessService.processIncomingMessage` |
| `TNS` | Transaction notifications | `MGTransactionHistoryService.refreshHistory` |
| `MG` | Payment key replenishment triggered by the TSP | `ProvisioningBusinessService.sendRequestForReplenishment` |

```java
private static final String KEY_SENDER = "sender";

public void processIncomingMessage(@NonNull final Context context,
                                   @NonNull final Map<String, String> data) {
    String sender = "";
    if (!data.isEmpty()) {
        for (String key : data.keySet()) {
            if (KEY_SENDER.equalsIgnoreCase(key)) {
                sender = data.get(key);
            }
        }
    }

    switch (sender) {
        case "CPS": /* Digital card operations (LCM). */ break;
        case "TNS": /* Transaction notifications. */ break;
        case "MG":  /* Key replenishment triggered by the TSP. */ break;
        default:    /* Non-SDK notifications */ break;
    }
}
```

### CPS — forward to the SDK

```java
// 1 - Build bundle from push payload data
final Bundle bundle = new Bundle();
for (String key : data.keySet()) {
    if (null != data.get(key)) {
        bundle.putString(key, data.get(key));
    }
}

// 2 - Process CPS sender push
My_PushServiceListener pushListener = new My_PushServiceListener();
final ProvisioningBusinessService provService
            = ProvisioningServiceManager.getProvisioningBusinessService();
provService.processIncomingMessage(bundle, pushListener);
```

`ProvisioningServiceMessage.getMsgCode()` tells you which operation arrived:

```java
public class My_PushServiceListener implements PushServiceListener {

  @Override
  public void onServerMessage(String tokenizedCardId, ProvisioningServiceMessage message) {
      String messageCode = message.getMsgCode();

      switch (messageCode) {
          case KnownMessageCode.REQUEST_INSTALL_CARD:
              // 1st push notification for installing card
          case KnownMessageCode.REQUEST_REPLENISH_KEYS:
              // 2nd push notification for installing payment keys and subsequent replenishments
          case KnownMessageCode.REQUEST_RESUME_CARD:
          case KnownMessageCode.REQUEST_SUSPEND_CARD:
          case KnownMessageCode.REQUEST_RENEW_CARD:
              // token to be renewed (profile update)
          case KnownMessageCode.REQUEST_DELETE_CARD:
              LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_RELOAD_CARDS));
              break;
          default:
      }
  }

  @Override
  public void onUnsupportedPushContent(Bundle pushMessageBundle) {
      // Message not understood / not supported
  }

  @Override
  public void onComplete() {
      // Provisioning session completed successfully. The card is ready for payment.
  }

  @Override
  public void onError(ProvisioningServiceError error) {
      // Parse the error and take appropriate action.
  }
}
```

`onComplete()` here is the real end of tokenization — that is when you can tell
the user the card is ready.

### TNS — refresh transaction history

Payload keys: `sender`=`TNS`, `action`=`TNS:PaymentTransactionNotification`,
`digitalCardId`, and `transactionRecordType` (co-badged cards only).

You need an **access token first**:

```java
final MGTransactionHistoryService tnsService
       = MobileGatewayManager.INSTANCE.getTransactionHistoryService();

final ProvisioningBusinessService provService
       = ProvisioningServiceManager.getProvisioningBusinessService();

provService.getAccessToken(digitalCardID, GetAccessTokenMode.REFRESH, new AccessTokenListener() {
    @Override
    public void onSuccess(String digitalCardId, String accessToken) {
        tnsService.refreshHistory(accessToken, digitalCardID, null, transactionRecordType,
            new TransactionHistoryListener() {
                @Override
                public void onSuccess(List<MGTransactionRecord> list, String digitalCardId, String timeStamp) {
                    // Parse the list of transaction records
                }

                @Override
                public void onError(String s, MobileGatewayError mobileGatewayError) { }
            });
    }

    @Override
    public void onError(String digitalCardId, ProvisioningServiceError error) { }
});
```

### MG — replenishment

Payload: `sender`=`MG`, `action`=`MG:ReplenishmentNeededNotification`,
`digitalCardId`.

```java
final ProvisioningBusinessService provService
       = ProvisioningServiceManager.getProvisioningBusinessService();
provService.sendRequestForReplenishment(digitalCardID, new ReplenishmentListener(), true);
```

Pass `forced = true` for TSP-triggered replenishment. Reuse
`PushServiceListener`; only `onComplete()` and `onError()` fire in this path.

> **Scheme difference in `onComplete()`:** for **Visa** (LUK) this means done —
> the card has a new key. For **Mastercard and PURE** (SUK) it only means the
> request was sent; you must wait for a further push before the keys exist.

---

## 7. Tokenization

```
checkEligibility → display + accept T&C → digitizeCard → [green | yellow] → trigger provisioning
```

### Step 1 — Check eligibility

```java
MGCardEnrollmentService enrollmentService =
        MobileGatewayManager.INSTANCE.getCardEnrollmentService();

InstrumentData instrumentData =
        new InstrumentData.EncryptedCardDataBuilder(encryptedCardInfo)
                .publicKeyIdentifier(pubKey)
                .build();

EligibilityData eligibilityData =
        new EligibilityData.Builder(InputMethod.BANK_APP, "en").build();

enrollmentService.checkEligibility(eligibilityData, instrumentData, new CardEligibilityListener() {
    @Override
    public void onSuccess(TermsAndConditions termsAndConditions, IssuerData issuerData) {
        String tncContent = termsAndConditions.getContent();
        ContentType tncContentType = termsAndConditions.getContentType();
        // Persist and display the T&C. Collect acceptance before calling digitizeCard(...).
    }

    @Override
    public void onError(MobileGatewayError error) {
        // Card is not eligible, or the request failed.
    }
});
```

Three input variants for `InstrumentData`:

| Source | Builder |
|---|---|
| Encrypted card credentials | `new InstrumentData.EncryptedCardDataBuilder(encryptedCardInfo).publicKeyIdentifier(pubKey).build()` |
| Issuer push receipt (**Mastercard/MDES Token Connect only**) | `new InstrumentData.IssuerPushReceiptBuilder("MASTERCARD", "pushAccountReceipt", payload).build()` |
| Push card enrollment session | pass `pushSessionId` in place of `instrumentData` |

### Step 2 — Accept T&C

```java
TermsAndConditionSession tncSession = termsAndConditions.accept();
```

Render `getContent()` according to `getContentType()`. If your programme does
not require T&C, call `accept()` without user interaction.

### Step 3 — Digitize

Your **issuer backend** returns one of three decisions:

| Flow | Meaning |
|---|---|
| **Green** | Approved, no step-up. Typical when the SDK is inside the issuer's own app and the user is already authenticated. |
| **Yellow** | Approved **with** step-up authentication (ID&V). Typical for multi-issuer wallets. |
| **Red** | Declined — the SDK returns an error callback. |

```java
public class MyDigitizationListener implements MGDigitizationListener {

    @Override
    public void onCPSActivationCodeAcquired(String digitalCardId, byte[] activationCode) {
        // Start provisioning for this digitalCardId (see Step 4).
    }

    @Override
    public void onSelectIDVMethod(IDVMethodSelector idvMethodSelector) {
        // Yellow flow only — list of ID&V methods
    }

    @Override
    public void onActivationRequired(PendingCardActivation pendingCardActivation) {
        // Yellow flow only — selected ID&V method requires activation
    }

    @Override
    public void onComplete(String digitalCardId) {
        // Digitization completed successfully.
    }

    @Override
    public void onError(String digitalCardId, MobileGatewayError error) {
        // Parse error.getCode() and apply retry / recovery logic.
    }
}
```

```java
MyDigitizationListener digitizationListener = new MyDigitizationListener();

MGCardEnrollmentService enrollmentService =
        MobileGatewayManager.INSTANCE.getCardEnrollmentService();

TermsAndConditionSession tncSession = termsAndConditions.accept();

// Provide the authentication in case of green flow
byte[] authenticationToken = null;

enrollmentService.digitizeCard(tncSession, authenticationToken, digitizationListener);
```

### Yellow flow — ID&V

`IDVMethodSelector.getIdvMethodList()` returns `IDVMethod[]` with `id`, `type`,
`value`, `isOTPRequired`:

| `type` | `value` | Completion |
|---|---|---|
| `cell_phone` | masked phone number | OTP → completes **directly** |
| `email` | masked email | OTP → completes **directly** |
| `app_to_app` | issuer app name | **with** cryptogram → completes directly; **without** → waits for CPS push |
| `customer_service` | phone number | completes only after CPS push |
| `website` | website URL | completes only after CPS push |

That last column drives your UX: for `website`, `customer_service`, and
cryptogram-less `app_to_app`, the user leaves your flow and the card activates
later via push. Show a pending state; don't block on a callback that isn't
coming.

```java
@Override
public void onSelectIDVMethod(final IDVMethodSelector idvMethodSelector) {
    if (idvMethodSelector.getIdvMethodList().length == 0) {
        // Log error
    }
    this.idvMethodSelector = idvMethodSelector;
    displayIdvMethods();
}

public void displayIdvMethods() {
    for (int i = 0; i < idvMethodSelector.getIdvMethodList().length; i++) {
        IDVMethod idvMethod = idvMethodSelector.getIdvMethodList()[i];
        String id = idvMethod.getId();
        String type = idvMethod.getType();
        String value = idvMethod.getValue();
        boolean isOtpRequired = idvMethod.isOtpRequired();
    }
}

// Called from your UI when the end user picks a method
public void selectIdvMethod(int idvSelectedMethodId) {
    idvMethodSelector.select(idvSelectedMethodId);
}
```

**OTP** — the SDK provides no OTP entry UI; build your own:

```java
@Override
public void onActivationRequired(PendingCardActivation pendingCardActivation) {
    this.pendingCardActivation = pendingCardActivation;

    PendingCardActivation activationState = pendingCardActivation.getState();
    switch (activationState) {
        case OTP_NEEDED:
            // display a UI to enter an OTP
    }
}

public void idvActivationWithOtp(String otp) {
    pendingCardActivation.activate(otp.getBytes(), digitizationListener);
}
```

**App-to-app**:

```java
MGCardEnrollmentService enrollmentService = MobileGatewayManager.INSTANCE.getCardEnrollmentService();
PendingCardActivation pendingCardActivation = enrollmentService.getPendingCardActivation(digitalCardId);
PendingCardActivationState state = pendingCardActivation.getState();
if (PendingCardActivationState.APP2APP_NEEDED == state) {
  AppToAppData appToAppData = pendingCardActivation.getAppToAppData();
  if (appToAppData != null) {
    String scheme = appToAppData.getScheme();
    String source = appToAppData.getSource();
    String payload = appToAppData.getPayload();
    // use source to get packageId and intent action to launch issuer application
  }
}
```

Resume with `PendingCardActivation.resumeAppToAppActivation(...)` — passing the
issuer cryptogram where the variant provides one.

### Step 4 — Trigger provisioning

Uses the `activationCode` from `onCPSActivationCodeAcquired`. Branch on
enrollment status — this is not optional boilerplate; each branch calls a
different API:

```java
byte[] activationCode = ...; // from MGDigitizationListener.onCPSActivationCodeAcquired(...)
String pushToken = "...";    // from FCM or HMS Push Kit (prefix "HMS:" for HMS)
String language = "en";

String walletId = MobileGatewayManager.INSTANCE.getCardEnrollmentService().getWalletId();

EnrollingServiceListener enrollingListener = new MyEnrollingServiceListener(activationCode);

final EnrollingBusinessService enrollingService = ProvisioningServiceManager.getEnrollingBusinessService();
final ProvisioningBusinessService provisioningBusinessService = ProvisioningServiceManager.getProvisioningBusinessService();

final EnrollmentStatus status = enrollingService.isEnrolled();
switch (status) {
    case ENROLLMENT_NEEDED:
        enrollingService.enroll(walletId, pushToken, language, enrollingListener);
        break;
    case ENROLLMENT_IN_PROGRESS:
        enrollingService.continueEnrollment(language, enrollingListener);
        break;
    case ENROLLMENT_COMPLETE:
        // The SDK requests the activation code via enrollingListener.onCodeRequired(...).
        provisioningBusinessService.sendActivationCode(enrollingListener);
        break;
    default:
        break;
}
```

The activation code is fed in **byte by byte** through a `SecureCodeInputer` —
never as a `String`:

```java
public class MyEnrollingServiceListener implements EnrollingServiceListener {

    private final byte[] activationCode;

    public MyEnrollingServiceListener(final byte[] activationCode) {
        this.activationCode = activationCode;
    }

    @Override
    public void onStarted() { }

    @Override
    public void onCodeRequired(final CHCodeVerifier chCodeVerifier) {
        final SecureCodeInputer inputer = chCodeVerifier.getSecureCodeInputer();
        for (final byte b : activationCode) {
            inputer.input(b);
        }
        inputer.finish();
    }

    @Override
    public void onComplete() {
        // In a green flow, this usually means provisioning is complete.
    }

    @Override
    public void onError(final ProvisioningServiceError error) { }
}
```

Store the returned `digitalCardId` — you need it for access tokens, transaction
history, and LCM.

---

## 8. CDCVM

Consumer Device Cardholder Verification Method, backed by the Android secure
lock screen and Keystore user authentication. Two methods: **biometrics**
(strong credentials) and **device keyguard** (PIN/pattern/password).

> **The first provisioned card that supports CDCVM defines the method for the
> whole wallet instance.** The SDK does not allow mixed CDCVM methods. Setting
> it is **mandatory** — skip it and `getAllCards()` returns
> `DigitalizedCardErrorCodes.CD_CVM_REQUIRED` and NFC payments fail.

Detect the requirement:

```java
DigitalizedCardManager.getAllCards(new AbstractAsyncHandler<String[]>() {
    @Override
    public void onComplete(final AsyncResult<String[]> asyncResult) {
        if (asyncResult.isSuccessful()) {
            // Card list retrieved successfully
        } else {
            final int errorCode = asyncResult.getErrorCode();
            if (errorCode == DigitalizedCardErrorCodes.CD_CVM_REQUIRED) {
                // Set the CDCVM method here
            }
        }
    }
});
```

Check eligibility and set it:

```java
DeviceCVMEligibilityResult result =
        DeviceCVMEligibilityChecker.checkDeviceEligibility(getApplicationContext());

if (result.getBiometricsSupport() == BiometricsSupport.SUPPORTED) {
    try {
        DeviceCVMManager.INSTANCE.initialize(CHVerificationMethod.BIOMETRICS);
    } catch (DeviceCVMException e) { }
}
else if (result.getDeviceKeyguardSupport() == DeviceKeyguardSupport.SUPPORTED) {
    try {
        DeviceCVMManager.INSTANCE.initialize(CHVerificationMethod.DEVICE_KEYGUARD);
    } catch (DeviceCVMException e) { }
}
else {
    // Device does not support CDCVM, or no secure lock screen.
    // Prompt the end user to enable biometrics or device credentials.
}
```

Non-supported results are diagnostic — use them to write a useful message:

- `getBiometricsSupport()`: `ANDROID_VERSION_NOT_SUPPORTED`,
  `NO_FINGERPRINT_SENSOR`, `NO_FINGERPRINT_ENROLLED`, `PERMISSION_NOT_GRANTED`,
  `SECURE_LOCK_NOT_PRESENTED`
- `getDeviceKeyguardSupport()`: `ANDROID_VERSION_NOT_SUPPORTED`,
  `SECURE_LOCK_NOT_PRESENTED`

"No fingerprint enrolled" and "no secure lock screen" are user-fixable; say so
rather than showing a generic failure.

---

Continued in [NFC Wallet payments](nfc-wallet-payment.html) — managing
cards, HCE contactless payment, QR, DSRP, and replenishment.
