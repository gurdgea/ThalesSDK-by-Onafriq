---
title: Deployment
layout: default
nav_order: 11
---

# Deployment
{: .no_toc }

Obfuscation, hardening, and store submission.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Obfuscation

The SDK ships obfuscated with DexGuard, with its public API preserved. Your
application must still be obfuscated.

Apply the rules supplied with your binary package. They include:

```
-flattenpackagehierachy util
```

Use the package name `util` for consistency with the SDK's own rules. DexGuard is
recommended; ProGuard and R8 are supported with the additional rule below.

### Subclassing DisplayTextView under ProGuard or R8

Provide an obfuscated class name in a `util` package:

```java
package util;

import com.thalesgroup.gemalto.d1.DisplayTextView;

public class x extends DisplayTextView {
    public x(@NonNull Context context) { super(context); }
    public x(@NonNull Context context, @Nullable AttributeSet a) { super(context, a); }
    public x(@NonNull Context context, @Nullable AttributeSet a, int i) { super(context, a, i); }
}
```

### Samsung Pay

ProGuard:

```
-keep class com.samsung.android.sdk.** { *; }
-keep interface com.samsung.android.sdk.** { *; }
```

DexGuard:

```
-keepresourcexmlelements manifest/application/meta-data@name=debug_mode
-keepresourcexmlelements manifest/application/meta-data@name=spay_debug_api_key
```

## Application hardening

### Application configuration

- Verify the installer package is `com.android.vending` to block side-loaded
  distribution
- Force update when a security release is available
- Do not set `android:installLocation` to allow external storage
- Set `android:allowBackup="false"`
- Set `android:debuggable="false"` and `debuggable false` in the release build
  type
- Restrict supported OS versions to those the SDK supports
- Verify a secure lock screen exists using `KeyguardManager`
- Prevent screenshots and accessibility capture on sensitive screens
- Guard against overlay attacks
- Minimise permissions and audit `exported` attributes on components
- Review app links and deep links

### Data handling

Clear sensitive data explicitly. Hold it in `byte[]` rather than `String` or
`StringBuilder`, and clear it in a `finally` block:

```kotlin
val token = tokenProvider.issuerToken(payload)
try {
    gateway.login(token)
} finally {
    token.fill(0)
}
```

Apply the same pattern to `CardDetails`. A scoped accessor makes this structural
rather than a convention. See
[Secure Card Display](services/secure-card-display.html).

### Runtime integrity

Detect rooted devices, hooking frameworks, attached debuggers, emulators, and
application tampering. Verify the signing certificate hash at runtime and share
it with the D1 backend, which validates it before honouring requests. This is
effective only if the stored hash is itself strongly obfuscated.

### Authentication and cryptography

Enable biometric authentication and enforce multi-factor authentication. Use
vetted security providers and a secure random number generator.

## OS compatibility

Thales tests each OS release against the current SDK version. As of July 2026,
Android 17 is supported.

Platform betas are published ahead of public releases specifically to allow
compatibility testing. Include that testing in the release calendar; a payment
application cannot absorb an OS incompatibility discovered at general
availability.

## Store submission

- Complete the Google Play Data Safety form. Thales publishes per-product
  guidance for the financial data declarations.
- Meet Google Play target API level requirements.
- Support 16 KB page sizes: D1 SDK 4.1.0 or later, JNA 5.17.0 or later, AGP
  8.5.1 or later.
- Push provisioning requires passing Google's launch review before publication.
- Remove `spay_debug_api_key` and set `debug_mode` correctly for release builds.

## Release verification

```bash
# Both variants build
./gradlew clean assembleStagingDebug assembleProdRelease

# The release build resolves the release AAR
./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath | grep d1-

# Tests and lint
./gradlew build
```

### Checklist

- [ ] Release build resolves `d1-release-*` and never `d1-debug-*`
- [ ] `allowBackup="false"` and `debuggable false`
- [ ] Samsung debug API key removed; `debug_mode` correct for release
- [ ] Production configuration contains real onboarding values
- [ ] Configuration files and SDK binaries excluded from version control
- [ ] `APP_PK` registered with Thales for the release signing key
- [ ] Google Push Provisioning allowlist covers the release fingerprint
- [ ] No card data in logs, analytics, or crash reporting
- [ ] `onActivityResult` forwards to `handleCardResult`
- [ ] Session renewal verified on a physical device
