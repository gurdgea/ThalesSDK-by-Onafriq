---
title: Prerequisites
layout: default
nav_order: 2
---

# Prerequisites
{: .no_toc }

Work to complete before writing code.
{: .fs-6 .fw-300 }

1. TOC
{:toc}

---

## Onboarding

The D1 SDK is distributed privately. It is not published to Maven Central or any
public repository, so integration begins with a Thales delivery engagement.

**Thales provides:**

- The SDK package (AARs, and a Samsung Pay JAR where applicable)
- Service URLs and RSA public key material
- Your `issuerID` and card encryption keys
- A parameter set for each environment

**You provide:**

- Application package name
- Signing certificate fingerprint (`APP_PK`)
- FCM service account JSON, or HMS Push Kit configuration

Supply `APP_PK` for every signing key you use, including debug and internal
builds, and re-supply it whenever you rotate keys or add a build flavour. D1
rejects unregistered package-name and fingerprint pairs during `configure()`.

## Environment parameters

Request a separate parameter set for each environment: Sandbox, PreProd, and
Production.

| Parameter | Type | Description |
|:---|:---|:---|
| `d1ServiceURL` | String | D1 Service Server URL |
| `issuerID` | String | Unique customer identifier |
| `d1ServiceRSAExponent` | byte[] | RSA exponent of the D1 service public key |
| `d1ServiceRSAModulus` | byte[] | RSA modulus of the D1 service public key |
| `digitalCardURL` | String | Digital card operations URL |

If the RSA key arrives as a PEM file, extract the modulus and exponent with:

```bash
openssl rsa -pubin -inform PEM -text -noout < pubkey.pem
```

A **Mobile SDK Sandbox** is available for early development before PreProd is
provisioned. It supports login, NFC payment, Secure Card Display, and physical
issuance. Request its parameters and test card data from your Thales contact.

## Wallet approvals

Wallet-side approvals run on their own timeline and gate testing as well as
launch. Start them on day one.

### Google Pay

1. Request access to the Push Provisioning API using a **corporate** Google
   account. Personal accounts cannot be bound to the required NDA.
2. Submit an allowlist request. Google allowlists by **package name and signing
   fingerprint**, so include debug and internal builds.
3. Configure Terms of Service and card metadata, including card art, in the TSP.
4. Apply Google's branding guidelines to the *Add to Google Pay* button.
5. Pass Google's launch review before publishing to the Play Store.

### Samsung Pay

1. Register at [Samsung Pay Developers](https://pay.samsung.com/developers/signup)
   and upload a **release** build.
2. Create a Service ID. Pass it as the `serviceId` argument to
   `ConfigParams.buildConfigCard`. Visa and Mastercard tokenization does not
   require a CSR.
3. Set `ISSUER NAME` in the Samsung portal to exactly match the TSP issuer name.
   Digitization state queries return results only for exact matches.

Samsung Pay support additionally requires `samsungpay_<version>.jar` from your
delivery package.

## Backend services

PIN management and card activation require endpoints on your side. D1 calls
these outbound; the mobile work cannot complete without them.

| Feature | Endpoint |
|:---|:---|
| Display a PIN | `GET /cms-api/v1/issuers/{issuerId}/cards/{cardId}/pin` |
| Change a PIN | `PUT /cms-api/v1/issuers/{issuerId}/cards/{cardId}/pin` |
| PIN change counter (Seccos only) | `GET /cms-api/v1/issuers/{issuerId}/cards/{cardId}/pin/changecounter` |
| Activate a card | `POST /cms-api/v1/issuers/{issuerId}/physicalcards/{cardId}/activate` |
| Authentication for the above | `POST /oauth2/token` |

Supported PIN block formats are ISO 0 and PIN 3DES Seccos.

You also need a service that mints issuer access tokens. See
[Authentication](authentication.html) for the required JWT structure.

## Core concepts

| Term | Meaning |
|:---|:---|
| **End user** | Identified by `consumerId`, defined by the issuer and unique within D1. D1 does not create end users; you register them. |
| **Card** | Identified by `cardId`, defined by the issuer. Has no direct link to the PAN, so it is not cardholder data for PCI DSS scoping. May be physical or virtual. |
| **Digital card** | Identified by `digitalCardId`. An EMV token created by tokenization and defined by the payment network. |
| **Card product** | Identified by `cardProductId`. Defines card type, PAN range, defaults, and which controls and limits are available. Configured at onboarding. |
| **Account** | An issuer-defined identifier, such as an account number, used at authorization to select which account to check funds against. |

Cards reach D1 by one of two routes. **Card creation** has D1 allocate the PAN
and expiry, optionally syncing to your card management system. **Card
registration** has your system create the card, which you then register in D1.

Card products determine which transaction controls and limits exist for a card.
Limits are inherited at creation and can afterwards be updated but never added
or removed, so define card products carefully during onboarding.

## Next

Continue to [Installation](installation.html).
