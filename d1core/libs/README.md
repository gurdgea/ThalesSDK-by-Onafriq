# Thales D1 SDK binaries

The D1 SDK is delivered privately under licence, obtain them from your Thales delivery contact and drop them here:

```
d1core/libs/
├── d1-debug-4.4.0.aar
└── d1-release-4.4.0.aar
```

The filenames matter. `settings.gradle.kts` declares a `flatDir` repository over
this folder, and `d1core/build.gradle.kts` resolves them by name:

```kotlin
releaseApi(group = "", name = "d1-release-4.4.0", ext = "aar")
debugApi(group = "", name = "d1-debug-4.4.0", ext = "aar")
```

If your delivery ships a different version, update both lines to match.

Delivery packages supporting Samsung Pay also contain `samsungpay_<version>.jar`,
which goes in this folder too. See
[Installation](https://gurdgea.github.io/ThalesSDK-by-Onafriq/installation.html)
for how to verify which components your package contains.
