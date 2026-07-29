# ThalesSDKTest

Sample Android application integrating the **Thales D1 SDK 4.4.0**. Cards are
pushed into Google Pay and other token requestors; the app manages card display,
provisioning, controls, and PIN operations.

📖 **[Integration guide](https://gurdgea.github.io/ThalesSDK-by-Onafriq/)** —
full documentation, published from [`docs/`](docs/).

## Modules

| Module | Responsibility |
|---|---|
| `:d1core` | Configuration, session management, coroutine bridge, error mapping, service wrappers. No UI dependencies. |
| `:d1ui` | Compose wrappers for the SDK's secure views. |
| `:d1pay` | D1Pay configuration contributor. |
| `:app` | Screens and navigation. |

## Building

Two items are not in version control and must be supplied locally.

**1. SDK binaries.** The D1 SDK is distributed privately under licence. Obtain
the AARs from your Thales contact and place them in `d1core/libs/` — see
[`d1core/libs/README.md`](d1core/libs/README.md).

**2. Environment configuration.** Copy a template and fill in your onboarding
values:

```bash
cp config/staging.properties.example config/staging.properties
```

Gradle falls back to the template when the real file is absent, so the project
configures without it. Placeholder values do not reach a live backend.

```bash
./gradlew assembleStagingDebug
./gradlew assembleProdRelease
./gradlew test
```

## Environments

Parameters come from `config/<env>.properties`, selected by a product flavour on
the `env` dimension:

| Flavour | File | Environment |
|---|---|---|
| `staging` | `config/staging.properties` | Sandbox |
| `preprod` | `config/preprod.properties` | PreProd |
| `prod` | `config/prod.properties` | Production |

`:d1core` exposes each key as a `BuildConfig` field. `D1Config` reads and
validates them, reporting all missing values together.

The debug and release AARs are bound to their respective build types through
`debugApi` and `releaseApi`. Verify the separation before release:

```bash
./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath | grep d1-
```

## Current limitations

- **Samsung Pay** requires `samsungpay_<version>.jar`, which is not present in
  this delivery package. No Samsung Pay code or manifest entries are included.
- **D1Pay** is not present in this delivery package. `:d1pay` resolves the
  configuration entry point reflectively, so it compiles now and activates when a
  D1Pay-enabled AAR is supplied.
- **Push notifications** are implemented but inert. Add `google-services.json`
  and apply the `google-services` plugin to activate Firebase.
- **PIN and activation** additionally require the issuer backend endpoints listed
  in the guide's [prerequisites](docs/prerequisites.md).

## Documentation

The site in [`docs/`](docs/) is built by
[`.github/workflows/docs.yml`](.github/workflows/docs.yml) using pinned Jekyll and
[just-the-docs](https://just-the-docs.com) versions.

To publish, once:

1. **Settings → Pages → Build and deployment → Source: _GitHub Actions_**
2. Push to `main`, or run **Actions → Publish docs → Run workflow**
3. The URL appears in the workflow's `deploy` job

Set `sample_app_url` in [`docs/_config.yml`](docs/_config.yml) to this
repository's URL so the guide links back correctly.

Preview locally:

```bash
cd docs && bundle install && bundle exec jekyll serve
```

`docs/superpowers/` holds design and planning notes; it is excluded from the
published site.
