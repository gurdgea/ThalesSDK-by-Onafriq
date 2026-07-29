---
title: Installation
layout: default
nav_order: 3
---

# Installation
{: .no_toc }

Adding the SDK binaries, Gradle configuration, and manifest entries.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Package contents

A delivery package has this shape:

```
<d1-release-package>
├── binaries
│   ├── samsungpay_2.17.00.jar
│   ├── d1-debug.aar
│   └── d1-release.aar
└── javadoc
```

Packages vary by programme. Confirm which components yours contains before
planning work that depends on them:

```bash
unzip -o d1-debug-4.4.0.aar -d aar/ && cd aar

# List the packages the SDK exposes
unzip -l classes.jar | awk '{print $4}' \
  | grep '^com/thalesgroup/gemalto/d1' \
  | sed 's|/[^/]*$||' | sort -u

# D1Pay support
unzip -l classes.jar | grep -c d1pay

# Bundled Samsung Pay classes
unzip -l classes.jar | grep -ci samsung

# Supported ABIs
ls jni/
```

To read exact signatures:

```bash
mkdir x && cd x && unzip -q ../classes.jar 'com/thalesgroup/gemalto/d1/*'
javap -p com/thalesgroup/gemalto/d1/D1Task.class
javap -p -constants com/thalesgroup/gemalto/d1/D1Exception\$ErrorCode.class
```

A package without the `d1pay` package does not support D1Pay, and any reference
to `D1PayConfigParams` fails to compile. A package without Samsung classes and
without `samsungpay_<version>.jar` compiles Samsung Pay calls but throws
`NoClassDefFoundError` at runtime.

## Adding the binaries

Copy everything from `binaries/` into the module that owns the SDK:

```
d1core/libs/
├── d1-debug-4.4.0.aar
└── d1-release-4.4.0.aar
```

Declare a `flatDir` repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        flatDir { dirs("d1core/libs") }
    }
}
```

{: .important }
> Declare `flatDir` inside `dependencyResolutionManagement`. A `flatDir` block
> in `pluginManagement` applies only to plugin resolution and has no effect on
> dependencies. `FAIL_ON_PROJECT_REPOS` prevents declaring repositories in
> individual modules.

Reference the AARs by name and extension:

```kotlin
dependencies {
    releaseApi(group = "", name = "d1-release-4.4.0", ext = "aar")
    debugApi(group = "", name = "d1-debug-4.4.0", ext = "aar")
}
```

Use `api` rather than `implementation` so downstream modules can reference SDK
types such as `OEMPayType`, `CardSettings`, and `SecureEditText`.

{: .warning }
> Local `.aar` **file** dependencies — `files("libs/d1-debug-4.4.0.aar")` — are
> rejected in Android library modules, because an AAR cannot embed another AAR.
> Resolve the SDK as a module through `flatDir` instead.

### Build type separation

The debug AAR must never reach a release build. Shipping it throws
`ERROR_DEBUG_SDK_USED` during `configure()`.

Verify the split resolves correctly:

```bash
./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath | grep d1-
# \--- :d1-release-4.4.0

./gradlew :app:dependencies --configuration stagingDebugRuntimeClasspath | grep d1-
# +--- :d1-debug-4.4.0
```

## SDK versions

Compile-time platform requirements:

```kotlin
android {
    compileSdk { version = release(37) }

    defaultConfig {
        minSdk = 27
        targetSdk = 36
    }
}
```

`minSdk 27` is required by the SDK manifest. `compileSdk 37` is required by
current AndroidX releases, including `androidx.core:core-ktx:1.19.0` and
`androidx.lifecycle:lifecycle-runtime-compose:2.11.0`.

Install the platform if needed:

```bash
sdkmanager "platforms;android-37.0"
```

## Dependencies

```kotlin
dependencies {
    releaseApi(group = "", name = "d1-release-4.4.0", ext = "aar")
    debugApi(group = "", name = "d1-debug-4.4.0", ext = "aar")

    implementation(libs.jna)                        // 5.17.0+
    implementation(libs.play.services.base)         // 18.5.0+
    implementation(libs.play.services.basement)     // 18.5.0+

    // Required for the coroutine bridge; not bundled with the SDK.
    api(libs.kotlinx.coroutines.android)

    implementation(libs.firebase.messaging)

    // D1Authn (3DS)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.play.services.fido)
    implementation(libs.androidx.biometric)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
}
```

Add the Samsung Pay JAR only if it is present in your package:

```kotlin
implementation(files("libs/samsungpay_2.17.00.jar"))
```

`androidx.credentials` requires `compileSdk 34` or later. OkHttp is not required
from SDK 4.2.0 onward.

## 16 KB page size support

Required by Google Play for apps targeting Android 15 and later:

- D1 SDK 4.1.0 or later
- JNA 5.17.0 or later, which requires AGP 8.0.2 or later
- AGP 8.5.1 or later aligns automatically; earlier versions need explicit
  packaging options

## Manifest

The SDK manifest contributes `INTERNET`, `ACCESS_NETWORK_STATE`, and
`HIDE_OVERLAY_WINDOWS`. Add notification support:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Request it at runtime on API 33 and later.

If you support push to token requestors, register the callback scheme so the
requestor can return control to your app:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:host="com.example.app" android:scheme="myapp" />
</intent-filter>
```

### Samsung Pay entries

Add these only when Samsung Pay is in scope:

```xml
<queries>
    <package android:name="com.samsung.android.spay" />
    <package android:name="com.samsung.android.samsungpay.gear" />
</queries>

<application>
    <meta-data android:name="debug_mode" android:value="${DEBUG_MODE}" />
    <meta-data android:name="spay_sdk_api_level" android:value="${SPAY_API_LEVEL}" />

    <!-- Debug builds only. Remove before release. -->
    <meta-data android:name="spay_debug_api_key" android:value="..." />
</application>
```

The `<queries>` block is mandatory from Android 11 (`targetSdk 30`). The Samsung
debug API key is valid for three months, covers ten Samsung accounts, must never
be logged, and must be removed from release builds.

## Optional components

To integrate a component that is not in your current package but is expected
later, resolve its entry point reflectively. The code compiles now and activates
when the component arrives, with no source change:

```kotlin
class D1PayParamsContributor : D1ParamsContributor {

    override fun params(context: Context): D1Params? = configParams()

    val isAvailable: Boolean get() = configParamsClass != null

    private fun configParams(): D1Params? {
        val type = configParamsClass ?: return null
        return runCatching {
            type.getMethod("getInstance").invoke(null) as? D1Params
        }.getOrNull()
    }

    private companion object {
        private const val CLASS_NAME =
            "com.thalesgroup.gemalto.d1.d1pay.D1PayConfigParams"

        val configParamsClass: Class<*>? by lazy {
            runCatching { Class.forName(CLASS_NAME) }.getOrNull()
        }
    }
}
```

Contribute the result alongside the core parameters during
[initialization](initialization.html).

## Verify the build

```bash
./gradlew clean assembleStagingDebug assembleProdRelease
```

Both variants must build before you continue.

## Next

Continue to [Configuration](configuration.html).
