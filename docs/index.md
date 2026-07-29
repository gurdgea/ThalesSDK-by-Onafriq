---
title: Overview
layout: default
nav_order: 1
---

# Thales D1 SDK for Android
{: .no_toc }

Integration guide for D1 SDK {{ site.sdk_version }}.
{: .fs-6 .fw-300 }

[Get started](prerequisites.html){: .btn .btn-primary }
[View the sample app]({{ site.sample_app_url }}){: .btn }

---

1. TOC
{:toc}

---

## What the D1 SDK does

The D1 SDK lets an issuer or banking app manage payment cards and push them into
third-party wallets. Cards live in Google Pay, Samsung Pay, and other token
requestors; your app orchestrates provisioning and card management.

It provides:

- **Secure Card Display** — show PAN, expiry, CVV, and cardholder name without
  the data entering your process
- **Dynamic CVV2** — single-use CVV for card-not-present transactions
- **Push Provisioning** — add cards to Google Pay, Samsung Pay, and token
  requestors
- **View and control** — list and manage every token issued against a card
- **Transaction control** — domain controls, spending limits, transaction history
- **PIN management** — display and change PINs for physical cards
- **Card activation** — activate a physical card with a CVV or PAN challenge
- **Messaging** — issuer messages delivered to the app
- **Click to Pay** — enrolment and profile management

## Choosing between the two Thales SDKs

Thales ships two unrelated Android SDKs. Choose by asking where the card lives
and which app the user taps.

| | **D1 SDK** | **NFC Wallet SDK** |
|:---|:---|:---|
| Package | `com.thalesgroup.gemalto.d1` | `com.gemalto.mfs.mwsdk` |
| Artifact | `d1-release.aar`, `d1-debug.aar` | `TSHPaySDK-release-<ver>.aar` |
| Build type | Issuer / banking app | Own-brand HCE wallet |
| Cards live in | Google Pay, Samsung Pay, token requestors | Your app |
| Entry point | `D1Task` | `SDKInitializer`, `MobileGatewayManager` |

Use the **D1 SDK** when the user taps *Add to Google Pay* and later pays with the
Google Pay app.

Use the **NFC Wallet SDK** when the user pays by tapping their phone while your
app is the default NFC payment app. It is documented separately under
[NFC Wallet SDK](nfc-wallet-sdk.html). Some programmes ship both.

The rest of this guide covers the D1 SDK.

## Integration sequence

The order below is deliberate: each step depends on the one before it.

| Step | Task | Guide |
|:--|:--|:--|
| 1 | Complete onboarding, collect parameters, start wallet approvals | [Prerequisites](prerequisites.html) |
| 2 | Add the binaries and Gradle configuration | [Installation](installation.html) |
| 3 | Wire per-environment parameters | [Configuration](configuration.html) |
| 4 | Build and configure `D1Task` | [Initialization](initialization.html) |
| 5 | Implement login and session renewal | [Authentication](authentication.html) |
| 6 | Implement Secure Card Display | [Services](services/secure-card-display.html) |
| 7 | Register for push notifications | [Messaging and push](services/messaging.html) |
| 8 | Implement push provisioning | [Push Provisioning](services/push-provisioning.html) |
| 9 | Add remaining services | [Services](services.html) |
| 10 | Harden, obfuscate, submit | [Deployment](deployment.html) |

Secure Card Display at step 6 is the fastest end-to-end proof that
configuration, login, and your backend all work. Implement it before anything
more elaborate.

## Reference architecture

The sample app separates SDK access from presentation across four modules:

```
:app  ──►  :d1ui  ──►  :d1core  ◄──  :d1pay
```

| Module | Responsibility |
|:---|:---|
| `:d1core` | Configuration, session management, coroutine bridge, error mapping, service wrappers. No UI dependencies. |
| `:d1ui` | Compose wrappers for the SDK's secure Views. |
| `:d1pay` | Optional D1Pay configuration. |
| `:app` | Screens and navigation. |

Keeping `:d1core` free of UI dependencies matters because several D1 APIs
require `View` instances, and mixing those concerns makes the wrapper untestable.
See [UI integration](ui-integration.html).

## Requirements

| Component | Version |
|:---|:---|
| D1 SDK | 4.4.0 |
| `minSdk` | 27 |
| `compileSdk` | 37 |
| JNA | 5.17.0 or later |
| Google Play services base / basement | 18.5.0 or later |
| AGP | 8.5.1 or later recommended |

## Further reading

- [D1 SDK Android Javadoc](https://thalesgroup.github.io/d1sdk-docs/d1-sdk/latest/android/)
- [D1 SDK iOS reference](https://thalesgroup.github.io/d1sdk-docs/d1-sdk/latest/ios/documentation/d1/)
- [Thales product documentation](https://docs.payments.thalescloud.io/)
- [Thales sample application](https://github.com/ThalesGroup/dp-d1-sample-android)
