---
title: Troubleshooting
layout: default
nav_order: 12
---

# Troubleshooting
{: .no_toc }

Symptoms, causes, and resolutions.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Build

### Could not find name: "d1-debug-4.4.0", ext: "aar"

The dependency is declared as a single string rather than as named arguments.
Kotlin DSL requires:

```kotlin
debugApi(group = "", name = "d1-debug-4.4.0", ext = "aar")
```

Confirm that `flatDir` is declared in `dependencyResolutionManagement` in
`settings.gradle.kts`, not in `pluginManagement`.

### Direct local .aar file dependencies are not supported when building an AAR

An Android library module cannot embed an AAR. Resolve the SDK through a
`flatDir` repository instead of `files("libs/...")`. See
[Installation](installation.html).

### Configuration cache state could not be cached

Reported against a task such as `:d1core:extractDebugAnnotations`. This is a
consequence of an unresolved dependency earlier in the build. Resolve the
dependency failure; the cache error clears with it.

### uses-sdk:minSdkVersion 25 cannot be smaller than version 27

The SDK requires `minSdk 27`.

### Dependency requires compile against version 37 or later

Current AndroidX releases require `compileSdk 37`. Install the platform with
`sdkmanager "platforms;android-37.0"` rather than downgrading AndroidX, which
cascades through the Compose BOM.

### Duplicate R.txt compile error

Caused by `android.enableJetifier=true`. Disable Jetifier, or exclude the D1 SDK
from it.

### 'val' cannot be reassigned on CardControlSettings

Several optional control accessors do not resolve to assignable Kotlin
properties. Call the setter method:

```kotlin
controls.setContactlessEnabled(value)
```

### InvalidFragmentVersionForActivityResult

`appcompat` declares `androidx.fragment:1.1.0`, and lint evaluates the declared
rather than resolved version. Declare a current `androidx.fragment` explicitly in
the application module.

### JVM garbage collector is thrashing

The default Gradle heap is insufficient for a multi-module, multi-flavour build
with lint. Increase it:

```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
```

## Runtime

### Push provisioning never completes

No error, callback, or timeout occurs. `onActivityResult` is not forwarding to
`handleCardResult`. See [Initialization](initialization.html).

### Card reports NOT_DIGITIZED after successful digitization

Usually TSP portal configuration. Verify the registered package name and issuer
name. For Samsung Pay, `ISSUER NAME` must match the TSP value exactly.

### CheckEligibility error, code 14

The device's wallet environment does not match the D1 environment. Confirm the
build's environment and the wallet's mode.

### Eligibility fails with an unclear vendor error

Mastercard limits `cardHolderName` to 27 characters.

### Digitization state queries return nothing on Samsung

`ISSUER NAME` in the Samsung portal must match the TSP issuer name exactly. Only
exact matches return results.

### Google Pay unavailable

Google Pay does not run on emulators or on devices without Google Play services.
Test provisioning on physical hardware.

### configure() fails without identifying the cause

An unregistered package name and signing fingerprint pair fails during
`configure()`. Re-supply `APP_PK` to Thales after rotating signing keys or adding
a build flavour.

### One wallet failure prevents initialization

`ConfigCallback.onError` delivers a `List<D1Exception>`, one entry per configured
target. Handle entries individually. See
[Initialization](initialization.html).

### An update reports success but nothing changes

`updateDigitalCard` reports invalid state transitions through
`onSuccess(false)`. See [Card management](services/card-management.html).

### Card details render blank

`CardDetailsUI.getInstance` requires
`com.thalesgroup.gemalto.d1.securecarddisplay.DisplayTextView`. Confirm the
views are attached and have not been recreated by recomposition; see
[UI integration](ui-integration.html).

### Session expires unexpectedly during PIN change

Change PIN enforces a shorter session window. Refresh the session immediately
before submitting rather than relying on renewal after failure.

## Testing

### ExceptionInInitializerError in unit tests

SDK classes cannot be loaded in a local JVM test. Structure the integration so
logic under test does not require SDK types. See [Testing](testing.html).

## Support

Include with any support request:

- Full stack trace
- `D1Exception.getMessage()` and `getErrorCode()`
- `D1Task.getSDKVersions()`
- `D1Task.getAppInstanceID(context)`
- Environment, device model, and OS version
