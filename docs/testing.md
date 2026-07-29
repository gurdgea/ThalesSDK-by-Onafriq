---
title: Testing
layout: default
nav_order: 10
---

# Testing
{: .no_toc }

Structuring an integration so it can be tested.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Constraints

The SDK is distributed obfuscated with DexGuard. Its static initialisers require
an Android runtime, so loading SDK classes in a local JVM unit test fails:

```
java.lang.ExceptionInInitializerError
    Caused by: java.lang.RuntimeException
```

Once initialisation fails, the result is cached, and subsequent tests in the same
class fail with `NoClassDefFoundError`.

This means SDK types cannot be instantiated in `src/test`. Instrumented tests can
load them, but meaningful D1 calls also require a provisioned device, a live
backend, a valid issuer token, and — for provisioning — an allowlisted build on
physical hardware.

Design the integration so the logic worth testing does not require SDK types.

## Isolating the SDK boundary

Define an interface for SDK access. Return application types rather than SDK
types so a fake never needs to construct one:

```kotlin
internal interface D1Gateway {
    suspend fun configure(): List<D1Failure>
    fun bindingHash(): String?
    suspend fun login(token: ByteArray)
    suspend fun logout()
    suspend fun logoutAll()
}
```

The production implementation wraps `D1Task` and maps `D1Exception` to
`D1Failure`. Tests use a fake:

```kotlin
private class FakeGateway : D1Gateway {
    var loginCount = 0
    var lastToken: ByteArray? = null

    override suspend fun configure() = emptyList<D1Failure>()
    override fun bindingHash(): String? = "binding-payload"
    override suspend fun login(token: ByteArray) { loginCount++; lastToken = token }
    override suspend fun logout() = Unit
    override suspend fun logoutAll() = Unit
}
```

Session behaviour is then directly testable:

```kotlin
@Test
fun `retries exactly once when the session has expired`() = runTest {
    val gateway = FakeGateway()
    var attempts = 0

    val result = session(gateway).withSession {
        attempts++
        if (attempts == 1) throw D1Failure.NotLoggedIn(null, null)
        "ok"
    }

    assertEquals("ok", result)
    assertEquals(2, attempts)
    assertEquals(1, gateway.loginCount)
}

@Test
fun `token is wiped after login`() = runTest {
    val gateway = FakeGateway()
    session(gateway, tokenProvider = { "token".toByteArray() }).login()
    assertTrue(gateway.lastToken!!.all { it == 0.toByte() })
}
```

## Testing error classification

Key the mapping on error code names so it is a pure function:

```kotlin
@Test
fun `d1pay codes route by prefix`() {
    assertTrue(
        failureFor("ERROR_D1PAY_SOMETHING_NEW", "boom", null, null)
            is D1Failure.D1PayUnavailable
    )
}
```

Referencing `ErrorCode` as a parameter or field type is safe; only reading a
constant triggers class initialisation.

Verify the mapping table against the SDK enum by reading its constant names
reflectively with initialisation disabled:

```kotlin
@Test
fun `every handled code name exists in the SDK enum`() {
    val enumClass = Class.forName(
        "com.thalesgroup.gemalto.d1.D1Exception\$ErrorCode",
        false,                        // do not initialise
        javaClass.classLoader,
    )
    val actual = enumClass.declaredFields
        .filter { it.isEnumConstant }
        .map { it.name }
        .toSet()

    assertTrue("enum constants not readable", actual.isNotEmpty())
    assertEquals(emptySet<String>(), HANDLED_CODE_NAMES - actual)
}
```

Field names are available from the class file without executing the static
initialiser. This catches typos in the table and fails if a future SDK version
renames a code.

## Coverage

Prioritise logic that is easy to get subtly wrong and expensive to debug at
runtime:

| Area | Rationale |
|:---|:---|
| Hex decoding | An incorrect nibble shift corrupts the RSA key without raising an error |
| Configuration validation | Catches placeholder and malformed values before `configure()` |
| Error classification | Wide enum, mechanical mapping, easy to mis-key |
| Session renewal | Central to every feature |
| Optional component detection | Verifies assumptions about the delivered package |

```kotlin
@Test
fun `shifts the high nibble instead of adding it`() {
    assertArrayEquals(byteArrayOf(0xB1.toByte()), "B1".hexToBytes())
}

@Test
fun `reports every missing key at once`() {
    val missing = config(serviceUrl = "", issuerId = "REPLACE_ME", consumerId = "").validate()
    assertEquals(listOf("D1_SERVICE_URL", "ISSUER_ID", "CONSUMER_ID"), missing)
}
```

## Manual verification

Verify on a physical device with a provisioned backend:

- `configure()` succeeds, and per-target results are as expected
- `login()` succeeds and renews after expiry
- Card metadata and card details display
- Digitization state reflects wallet contents
- Provisioning completes and `handleCardResult` is reached
- PIN display and change complete within the session window
- Push notifications arrive and are processed

## Running

```bash
./gradlew test
./gradlew :d1core:testStagingDebugUnitTest
```

With build flavours, each test executes once per flavour, so reported totals are
a multiple of the number of distinct tests.
