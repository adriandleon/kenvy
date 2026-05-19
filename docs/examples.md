# Kenvy examples

These examples mirror behaviors that already have functional test coverage in
this repository. Use them as copy-paste starting points for consumer projects.
The plugin ID `io.github.adriandleon.kenvy` is not yet on the Gradle Plugin
Portal. For local staged evaluation, publish to Maven Local first
(`./gradlew :kenvy-plugin:publishToMavenLocal`) and add `mavenLocal()` to
`pluginManagement.repositories` in your consuming build.

## Common-only shared configuration

Use this layout when you only need shared generated config and do not need
platform-specific `actual` objects yet.

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    id("io.github.adriandleon.kenvy")
}

group = "com.example.shared"

kotlin {
    jvm()
}
```

```toml
[properties.api_key]
type = "String"
default = "placeholder"

[properties.retry_count]
type = "Int"
default = "3"
```

```kotlin
package com.example.shared

fun useKenvy(): String = Kenvy.apiKey
```

Generate the code with:

```sh
./gradlew generateKenvy
```

`generateKenvy` writes the shared object under
`build/generated/kenvy/commonMain/kotlin/<package path>/Kenvy.kt` and also runs
`generateKenvyExample`. Because `api_key` is still a placeholder in this
example, you must provide a real value through `local.properties` or an
environment variable before `generateKenvy` can finish successfully. Use
`KENVY_API_KEY` for the supported environment override name. The contract key
stays `api_key`; only the generated Kotlin accessor becomes `apiKey`.

## Common plus Android

Use this layout when you want Kenvy to generate an `expect` object in
`commonMain` and an Android `actual` object in `androidMain`.

```kotlin
plugins {
    id("com.android.library") version "8.5.2"
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    id("io.github.adriandleon.kenvy")
}

group = "com.example.mobile"

kotlin {
    androidTarget()
}

android {
    namespace = "com.example.mobile"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}
```

```toml
[properties.api_key]
type = "String"
default = "common-default"

[properties.timeout]
type = "Long"
default = "5000"

[overrides.common]
api_key = "common-fallback"

[overrides.android]
api_key = "android-default"

[overrides.android.debug]
api_key = "android-debug"
```

```kotlin
package com.example.mobile

fun androidValue(): String = Kenvy.apiKey
```

Run `./gradlew compileDebugKotlinAndroid` to let Kenvy infer the Android
variant from the tested Android compile task. In that flow Kenvy runs
`generateKenvy`, generates the tested Android bridge task
`generateKenvyAndroid`, and resolves `api_key` from
`[overrides.android.debug]`.

If you keep untracked Android secrets locally, use the same property name with
scoped keys in `local.properties`:

```properties
api_key=shared-local
api_key.android=android-local
api_key.android.debug=android-debug-local
api_key.android.release=android-release-local
```

In that setup, `Kenvy.apiKey` stays the same in application code while the
generated Android value changes with the requested build variant.

For CI or shell-based overrides, use the prefixed environment name. For
example, `api_key` resolves from `KENVY_API_KEY`.

## Common plus iOS

Use this layout when you want Kenvy to generate iOS `actual` objects using the
canonical `ios` override key.

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    id("io.github.adriandleon.kenvy")
}

group = "com.example.apple"

kotlin {
    iosArm64()
    iosSimulatorArm64()
}
```

```toml
[properties.api_key]
type = "String"
default = "common-default"

[properties.timeout]
type = "Long"
default = "5000"

[overrides.ios]
api_key = "ios-value"
```

```kotlin
package com.example.apple

fun iosValue(): String = Kenvy.apiKey
```

Run `./gradlew compileKotlinIosArm64 compileKotlinIosSimulatorArm64` to
generate the iOS outputs and compile against them. Kenvy registers the tested
tasks `generateKenvyIosArm64` and `generateKenvyIosSimulatorArm64`, and both
resolve through the canonical `[overrides.ios]` table. If you rename an iOS
target, keep using `[overrides.ios]`; custom iOS target names do not introduce
a different override key.

For different local iOS values, keep using the canonical `ios` platform key in
`local.properties`:

```properties
api_key=shared-local
api_key.ios=ios-local
api_key.ios.release=ios-release-local
```

If you want a release-specific iOS local value, set the variant explicitly in
your Gradle build before running the iOS generation task:

```kotlin
kenvy {
    variant.set("release")
}
```

Kenvy still generates `Kenvy.apiKey`; only the resolved value changes.

## Multi-module build with shared module and root local.properties

Use this layout when the plugin is applied to a `:shared` module and your
secrets are stored in the root `local.properties` next to `settings.gradle.kts`.
You no longer need to duplicate secrets into `shared/local.properties`.

```
my-app/
├── settings.gradle.kts          ← includes(":shared")
├── local.properties             ← root secrets file (gitignored)
└── shared/
    ├── build.gradle.kts         ← applies io.github.adriandleon.kenvy
    └── kenvy.toml
```

```toml
# shared/kenvy.toml
[properties.api_key]
type = "String"
default = "placeholder"
description = "Backend API key"
```

```kotlin
// shared/build.gradle.kts
plugins { id("io.github.adriandleon.kenvy") }
group = "com.example.shared"
```

```properties
# local.properties (root — shared with all modules)
api_key=my-root-secret
```

Kenvy automatically looks in the root `local.properties` first, then in
`shared/local.properties`. Module values override root values for the same key:

```properties
# shared/local.properties (optional — overrides root values for this module only)
api_key=shared-module-override
```

To add a custom local properties file or replace the lookup list entirely:

```kotlin
// shared/build.gradle.kts
kenvy {
    // Append to the defaults (root → module → custom, last wins):
    localPropertiesFiles.from(layout.projectDirectory.file("config/ci-overrides.properties"))

    // Replace defaults entirely (only this file is consulted):
    // localPropertiesFiles.setFrom(layout.projectDirectory.file("config/custom.properties"))
}
```

Do not commit any local properties file that contains real secret values.
Keep all local override files in `.gitignore` before storing credentials.

### What diagnostic file paths mean

When `api_key` is unresolved in a `:shared` module build, Kenvy reports which
files it checked:

```text
Checked local properties files:
  - root: /absolute/path/local.properties (missing)
  - module :shared: /absolute/path/shared/local.properties (missing)
```

- **root** means the file next to `settings.gradle.kts` (the root project directory).
- **module :shared** means the file inside the module where the plugin is applied.
- **(missing)** means the file does not exist yet — create it and add your value.

If you replaced the default list with `localPropertiesFiles.setFrom(...)`, only
your custom file is listed:

```text
Checked local properties files:
  - custom: /absolute/path/config/ci-overrides.properties
```

## Custom object name and package

Use this configuration when your project `group` is not the package you want in
generated source or when `Kenvy` is not the object name you want to expose.

```kotlin
kenvy {
    packageName.set("com.example.config")
    interfaceName.set("AppConfig")
}
```

With that configuration, `packageName` controls the generated package and
`interfaceName` changes both the generated file name and the generated object
name. Kenvy writes `AppConfig.kt` and exposes `AppConfig.apiKey` instead of
`Kenvy.apiKey`.

## Migrating from preserved-name generated properties

By default, Kenvy generates lower camel case Kotlin property names. For example,
`api_key` becomes `apiKey`, `retry_count` becomes `retryCount`, and `timeout_ms`
becomes `timeoutMs`. Contract keys in `kenvy.toml`, `[overrides.*]`,
`local.properties`, and `KENVY_` environment variables are not affected.

If you have existing code that references the old preserved names (such as
`Kenvy.api_key`), opt in to the compatibility mode while you migrate:

```kotlin
kenvy {
    generatedPropertyNameStyle.set("preserve")
}
```

That keeps accessors such as `Kenvy.api_key` in generated Kotlin source. Once
your code is updated to use `Kenvy.apiKey`, remove the setting to return to the
default lower camel case behavior.

## Example local properties generation

Use `generateKenvyExample` to scaffold the local values a developer needs to
provide in the project root.

```toml
[properties.api_key]
type = "String"
default = "placeholder"
description = "Backend API key"
help_url = "https://example.com/docs/api-key"

[properties.base_url]
type = "String"
default = "https://api.example.com"
```

Run:

```sh
./gradlew generateKenvyExample
```

Kenvy writes `local.properties.example` in the project root. `generateKenvy`
also runs this task before shared code generation. Placeholder-backed entries
stay as blank assignments, while comments can include the property type,
description, setup link, and non-sensitive effective contract values. Plain
`generateKenvyExample` uses shared context, so platform or variant override
comments appear only when `kenvy.platform` or `kenvy.variant` configures that
task context. Environment variable values are never written into
`local.properties.example`.

## Merge workflow

Use `mergeKenvy` when you want to add missing contract values into
`local.properties` without overwriting values a developer already set.

```sh
./gradlew mergeKenvy
```

`mergeKenvy` preserves local-only keys, keeps existing values, and does not
persist environment variable values into `local.properties`. It adds only
missing contract-backed entries, creates `local.properties` if needed, and
leaves existing local values in place even when the contract has defaults or
overrides. Kenvy ignores unprefixed environment variables such as `API_KEY` by
default. If you need to preserve that legacy behavior temporarily, opt in with
`kenvy { legacyUnprefixedEnvironmentOverrides.set(true) }`.

## Next steps

Use [getting started](getting-started.md) for setup details and
[release checklist](release-checklist.md) when you want to validate the project
for external launch.

## CI usage with scoped environment variables

Kenvy is CI-agnostic. It reads only environment variables that are visible to
the Gradle process. CI systems can expose secrets as environment variables
using the platform/variant naming convention to supply different values for
different build targets.

Kenvy does not integrate directly with CI providers. The examples below show
the environment variable mapping pattern; adapt the secret names and step
syntax to your CI system.

### GitHub Actions

```yaml
- name: Build Android debug
  env:
    KENVY_API_KEY_ANDROID_DEBUG: ${{ secrets.KENVY_API_KEY_ANDROID_DEBUG }}
  run: ./gradlew compileDebugKotlinAndroid

- name: Build iOS release
  env:
    KENVY_API_KEY_IOS_RELEASE: ${{ secrets.KENVY_API_KEY_IOS_RELEASE }}
  run: ./gradlew shared:generateKenvyIos -PkenvyVariant=release
```

Store secrets under the same name in GitHub → Repository → Settings →
Secrets. GitHub Actions maps `secrets.*` into step environment variables via
the `env:` block.

### Bitrise

```bash
envman add --key KENVY_API_KEY_ANDROID_RELEASE \
           --value "$KENVY_API_KEY_ANDROID_RELEASE"
./gradlew assembleRelease
```

Use Bitrise Secrets (not regular Env Vars) so values are protected in pull
request builds.

### Jenkins

```groovy
withCredentials([
  string(credentialsId: 'kenvy-api-key-ios-release',
         variable: 'KENVY_API_KEY_IOS_RELEASE')
]) {
  sh './gradlew shared:generateKenvyIos -PkenvyVariant=release'
}
```

`withCredentials` scopes each binding to the enclosed block and never logs
the value.

### Resolution fallback

Kenvy checks candidates from least to most specific and uses the last
non-blank match. If `KENVY_API_KEY_ANDROID_DEBUG` is blank or absent,
Kenvy falls back to `KENVY_API_KEY_ANDROID`, then to `KENVY_API_KEY`. You
can set a generic fallback secret in every CI job and override it per
platform/variant only where needed.

Do not generate or write `local.properties` in CI as the primary secret
delivery path. File-based delivery is a compatibility escape hatch; the
`KENVY_` environment variable pattern is the recommended CI path.
