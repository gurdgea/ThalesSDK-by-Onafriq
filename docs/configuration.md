---
title: Configuration
layout: default
nav_order: 4
---

# Configuration
{: .no_toc }

Managing onboarding parameters across environments.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Structure

Thales issues a distinct parameter set per environment. Bind each set to a build
flavour so the correct values are selected at build time.

```
config/
├── staging.properties.example    # Sandbox
├── preprod.properties.example    # PreProd
└── prod.properties.example       # Production
```

Commit only the templates. Real files carry onboarding values and stay out of
version control:

```gitignore
/config/*.properties
!/config/*.properties.example
```

## Parameters

```properties
# Onboarding parameters (D1Task.Builder)
D1_SERVICE_URL=https://sandbox.client-api.d1.thalescloud.io
ISSUER_ID=REPLACE_ME_ISSUER_ID
DIGITAL_CARD_URL=https://sandbox.digitalcard.d1.thalescloud.io
D1_SERVICE_RSA_MODULUS=00B1D8E4...
D1_SERVICE_RSA_EXPONENT=010001

# Optional, SDK 4.4.0 and later
APPLICATION_PROFILE_ID=

# Runtime identifiers
CONSUMER_ID=REPLACE_ME_CONSUMER_ID
CARD_ID=REPLACE_ME_CARD_ID

# Visa only. Defaults to ISSUER_ID when blank.
VISA_CLIENT_APP_ID=

# SDK behaviour
CLIENT_BINDING_ENABLED=false
SECURE_LOG_ENABLED=true

# Authentication
ISSUER_ACCESS_TOKEN=
```

| Key | Required | Notes |
|:---|:--|:---|
| `D1_SERVICE_URL` | Yes | D1 Service Server |
| `ISSUER_ID` | Yes | Issued by Thales |
| `DIGITAL_CARD_URL` | Yes | Digital card operations |
| `D1_SERVICE_RSA_MODULUS` | Yes | Hex string |
| `D1_SERVICE_RSA_EXPONENT` | Yes | Hex string |
| `APPLICATION_PROFILE_ID` | No | SDK 4.4.0 and later |
| `CONSUMER_ID` | Yes | End user identifier |
| `CARD_ID` | No | Convenience for development |
| `VISA_CLIENT_APP_ID` | No | Visa programmes only |
| `CLIENT_BINDING_ENABLED` | Yes | See [Authentication](authentication.html) |
| `SECURE_LOG_ENABLED` | Yes | Controls the SDK log service |
| `ISSUER_ACCESS_TOKEN` | No | Development only |

## Build flavours

Declare a flavour per environment and expose the values through `BuildConfig`:

```kotlin
val environments = listOf("staging", "preprod", "prod")

fun loadEnvConfig(env: String): Properties {
    val local = rootProject.file("config/$env.properties")
    val template = rootProject.file("config/$env.properties.example")
    val file = if (local.exists()) local else template
    require(file.exists()) {
        "Missing config/$env.properties. Copy config/$env.properties.example and " +
            "fill in the onboarding values supplied by Thales."
    }
    return Properties().apply { file.inputStream().use { load(it) } }
}

android {
    flavorDimensions += "env"
    productFlavors {
        environments.forEach { env ->
            create(env) {
                dimension = "env"
                buildConfigField("String", "ENV_NAME", "\"$env\"")
                loadEnvConfig(env).forEach { (rawKey, rawValue) ->
                    val key = rawKey.toString().trim()
                    val value = rawValue.toString().trim()
                    when (key) {
                        "CLIENT_BINDING_ENABLED", "SECURE_LOG_ENABLED" ->
                            buildConfigField("boolean", key, value.ifEmpty { "false" })
                        else ->
                            buildConfigField("String", key, "\"$value\"")
                    }
                }
            }
        }
    }
    buildFeatures { buildConfig = true }
}
```

Falling back to the template keeps a fresh checkout buildable. Without it Gradle
fails during configuration, before any task runs.

{: .important }
> Declare the same flavour dimension in every Android module, including modules
> that never read `BuildConfig`. A module without the dimension that depends on
> one with it requires `missingDimensionStrategy`, which pins it to a single
> environment's variant.

Build a specific environment:

```bash
./gradlew assembleStagingDebug
./gradlew assembleProdRelease
```

## Typed configuration

Read `BuildConfig` once into a typed object so the rest of the codebase does not
depend on generated fields:

```kotlin
data class D1Config(
    val environment: String,
    val serviceUrl: String,
    val issuerId: String,
    val digitalCardUrl: String,
    val rsaModulusHex: String,
    val rsaExponentHex: String,
    val applicationProfileId: String?,
    val consumerId: String,
    val cardId: String?,
    val visaClientAppId: String?,
    val clientBindingEnabled: Boolean,
    val secureLogEnabled: Boolean,
    val issuerAccessToken: String?,
) {
    val rsaModulus: ByteArray get() = rsaModulusHex.hexToBytes()
    val rsaExponent: ByteArray get() = rsaExponentHex.hexToBytes()

    companion object {
        fun fromBuildConfig(): D1Config = D1Config(
            environment = BuildConfig.ENV_NAME,
            serviceUrl = BuildConfig.D1_SERVICE_URL,
            issuerId = BuildConfig.ISSUER_ID,
            digitalCardUrl = BuildConfig.DIGITAL_CARD_URL,
            rsaModulusHex = BuildConfig.D1_SERVICE_RSA_MODULUS,
            rsaExponentHex = BuildConfig.D1_SERVICE_RSA_EXPONENT,
            applicationProfileId = BuildConfig.APPLICATION_PROFILE_ID.nullIfBlank(),
            consumerId = BuildConfig.CONSUMER_ID,
            cardId = BuildConfig.CARD_ID.nullIfBlank(),
            visaClientAppId = BuildConfig.VISA_CLIENT_APP_ID.nullIfBlank(),
            clientBindingEnabled = BuildConfig.CLIENT_BINDING_ENABLED,
            secureLogEnabled = BuildConfig.SECURE_LOG_ENABLED,
            issuerAccessToken = BuildConfig.ISSUER_ACCESS_TOKEN.nullIfBlank(),
        )
    }
}
```

## Validation

`D1Task.Builder` accepts any values and surfaces problems later, during
`configure()`, without identifying the offending parameter. Validate first and
report every problem together:

```kotlin
fun validate(): List<String> = buildList {
    if (serviceUrl.isBlank() || serviceUrl.isPlaceholder()) add("D1_SERVICE_URL")
    if (issuerId.isBlank() || issuerId.isPlaceholder()) add("ISSUER_ID")
    if (digitalCardUrl.isBlank() || digitalCardUrl.isPlaceholder()) add("DIGITAL_CARD_URL")
    if (consumerId.isBlank() || consumerId.isPlaceholder()) add("CONSUMER_ID")
    if (!rsaModulusHex.isValidHex()) add("D1_SERVICE_RSA_MODULUS")
    if (!rsaExponentHex.isValidHex()) add("D1_SERVICE_RSA_EXPONENT")
}

private fun String.isPlaceholder() = startsWith("REPLACE_ME")

private fun String.isValidHex() =
    isNotBlank() && length % 2 == 0 && all { it.digitToIntOrNull(16) != null }
```

Name the environment in the failure so the correct file is obvious:

```kotlin
class D1ConfigException(
    val environment: String,
    val missingKeys: List<String>,
) : IllegalStateException(
    "Incomplete D1 configuration in config/$environment.properties. " +
        "Supply real onboarding values for: ${missingKeys.joinToString()}"
)
```

Surface the failure on a screen rather than throwing during
`Application.onCreate`, so a configuration mistake does not present as a crash.

## Hex decoding

The RSA modulus and exponent are hex strings that must be converted to
`ByteArray`:

```kotlin
fun String.hexToBytes(): ByteArray {
    val cleaned = trim()
    require(cleaned.length % 2 == 0) {
        "Hex string must have an even length, was ${cleaned.length}"
    }
    return ByteArray(cleaned.length / 2) { index ->
        val high = cleaned[index * 2].hexValue()
        val low = cleaned[index * 2 + 1].hexValue()
        ((high shl 4) or low).toByte()
    }
}

private fun Char.hexValue(): Int =
    digitToIntOrNull(16) ?: throw IllegalArgumentException("Not a hex digit: '$this'")
```

{: .warning }
> Each byte is `(high shl 4) or low`. An implementation that adds the nibbles
> produces correct output whenever the high nibble is zero, so it passes casual
> testing and then corrupts the key. A malformed modulus does not raise a decode
> error — it produces a valid `ByteArray` that D1 rejects during `configure()`
> with an unrelated-looking message. Cover this with a test:
>
> ```kotlin
> assertArrayEquals(byteArrayOf(0xB1.toByte()), "B1".hexToBytes())
> ```

## Next

Continue to [Initialization](initialization.html).
