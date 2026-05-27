# Getting started with Kenvy

This guide shows you how to add Kenvy to a Kotlin Multiplatform build, define
your first contract, generate code, and understand where diagnostics appear.

## Before you begin

This repository currently verifies Kenvy with:

- Kotlin `2.1.20`
- Java 17 as the Gradle runtime target
- AGP `8.5.2` for the Android examples in this repo only

Kenvy generates Kotlin source during the build. You need a valid package name
for the generated code, either from your project `group` or from
`kenvy.packageName`.

- Set `group` to a valid Kotlin package such as `com.example.app`, or
- Configure `kenvy { packageName.set("com.example.app") }`

If you use KMP target-specific bridges, you must also apply the Kotlin
Multiplatform plugin and declare the targets you need.

## Add the plugin

Apply the plugin in the module that owns your `kenvy.toml` file.

Kenvy is published on the Gradle Plugin Portal. Normal consumers should use the
plugins DSL with the documented version:

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    id("io.github.adriandleon.kenvy") version "0.1.2"
}

group = "com.example.app"

kotlin {
    jvm()
}
```

By default, Kenvy reads `kenvy.toml` from the Gradle project directory. If you
need a different path, configure `kenvy.configFile`.

```kotlin
kenvy {
    configFile.set(layout.projectDirectory.file("config/kenvy.toml"))
}
```

## Define your contract

Create `kenvy.toml` in the project root. Each property lives under
`[properties.<name>]`.

```toml
[properties.api_key]
type = "String"
default = "placeholder"
description = "Backend API key"
help_url = "https://example.com/docs/api-key"
sensitive = true

[properties.base_url]
type = "String"
default = "https://api.example.com"

[properties.retry_count]
type = "Int"
default = "3"

[properties.debug_enabled]
type = "Boolean"
default = "false"

[properties.timeout_ms]
type = "Long"
default = "30000"
```

Kenvy currently supports `String`, `Int`, `Boolean`, and `Long`.

Contract property keys stay as written in your contract and override sources.
Generated Kotlin accessors default to lower camel case, so `api_key` becomes
`Kenvy.apiKey`, `retry_count` becomes `Kenvy.retryCount`, and `timeout_ms`
becomes `Kenvy.timeoutMs`.

Supported property metadata fields are:

- `description`
- `help_url`
- `sensitive`

## Generate configuration

Run the main generation task from the project root.

```sh
./gradlew generateKenvy
```

Kenvy writes generated source under `build/generated/kenvy/`. The shared output
path is:

```text
build/generated/kenvy/commonMain/kotlin/<package path>/<interfaceName>.kt
```

With the default extension values, that becomes:

```text
build/generated/kenvy/commonMain/kotlin/<group as package path>/Kenvy.kt
```

`generateKenvy` also runs `generateKenvyExample`, which writes
`local.properties.example` in the project root so you can see what local values
need to be filled in. `generateKenvy` does not mutate `local.properties`.

## Fill in local values

If your contract contains placeholders such as `default = "placeholder"` or
`default = "<YOUR_VALUE>"`, `generateKenvy` fails until you provide a real
value. The fastest path is:

1. Run `./gradlew generateKenvyExample`.
2. Copy `local.properties.example` to `local.properties`.
3. Fill in the blank values.
4. Run `./gradlew generateKenvy` again.

Kenvy does not mutate `local.properties` during `generateKenvy`.

For placeholder-backed properties, `local.properties.example` includes comments
for the property type, description, and setup link when `help_url` is present.
Sensitive values stay masked in the example output.

## Understand resolution order

Kenvy uses the same precedence order everywhere. Later entries win.

1. `properties.<name>.default`
2. `overrides.common.<name>`
3. `overrides.<platform>.<name>`
4. `overrides.<platform>.<variant>.<name>`
5. Root and module `local.properties` files merged in order, using `<name>`
6. Root and module `local.properties` files merged in order, using `<name>.<platform>`
7. Root and module `local.properties` files merged in order, using `<name>.<platform>.<variant>`
8. Matching unprefixed environment variable only when you opt in with
   `kenvy { legacyUnprefixedEnvironmentOverrides.set(true) }`
9. Matching `KENVY_<NORMALIZED_NAME>` environment variable
10. Matching `KENVY_<NORMALIZED_NAME>_<NORMALIZED_PLATFORM>` environment variable
11. Matching `KENVY_<NORMALIZED_NAME>_<NORMALIZED_PLATFORM>_<NORMALIZED_VARIANT>` environment variable

Use `local.properties` for developer-local values and `KENVY_` environment
variables for CI or ephemeral overrides.

Kenvy reads `local.properties` from two locations and merges them: root `local.properties`
(next to `settings.gradle.kts`) first, then module `local.properties` second. Module values
win over root values for the same key. For single-project builds the root and module file are
the same, so only one lookup happens.

When you need different untracked secrets per target, keep the same logical
property name and append platform or platform-plus-variant scopes in
`local.properties`. For example, `api_key.android.debug` is more specific than
`api_key.android`, which is more specific than `api_key`. You can define these
scoped keys in either root or module `local.properties`; scoped keys from both
files are merged the same way as unscoped keys.

Kenvy maps property names to environment variables by uppercasing and replacing
non-alphanumeric separators with underscores, then prefixes the result with
`KENVY_`. For example, `api_key` and `api-key` both map to
`KENVY_API_KEY`. Platform and variant segments use the same normalization.
For platform `android` and variant `debug`, Kenvy looks up `KENVY_API_KEY`,
then `KENVY_API_KEY_ANDROID`, then `KENVY_API_KEY_ANDROID_DEBUG`, and uses
the most specific non-blank value. This lets CI matrix builds set
`KENVY_API_KEY_ANDROID_DEBUG` and `KENVY_API_KEY_IOS_RELEASE` as separate
secrets without changing generated code.

Kenvy ignores unprefixed variables such as `API_KEY` by default. This avoids
accidental collisions with ambient build variables such as `PLATFORM_NAME`.
If you need the legacy behavior during migration, opt in explicitly. `KENVY_`
variables still win when both names are present:

```kotlin
kenvy {
    legacyUnprefixedEnvironmentOverrides.set(true)
}
```

## Configure overrides

Use shared and platform-specific overrides directly in `kenvy.toml`.

```toml
[properties.base_url]
type = "String"
default = "https://api.example.com"

[overrides.common]
base_url = "https://shared.example.com"

[overrides.android]
base_url = "https://android.example.com"

[overrides.android.debug]
base_url = "https://android-debug.example.com"
```

You can also set the platform and variant manually when you generate common
output outside platform-specific tasks.

Android target generation can infer a single variant from common
variant-bearing tasks such as `compileDebugKotlinAndroid`, `testDebugUnitTest`,
`connectedDebugAndroidTest`, `assembleRelease`, and `bundleRelease`. Lifecycle
tasks such as `build` and `check` do not imply one variant, and multi-variant
invocations such as `assembleDebug assembleRelease` require an explicit
`kenvy.variant` value.

```kotlin
kenvy {
    platform.set("android")
    variant.set("debug")
}
```

This same format works for local secrets:

```properties
api_key=shared-local
api_key.android=android-local
api_key.android.debug=android-debug-local
api_key.ios=ios-local
api_key.ios.release=ios-release-local
```

Environment values still win over both local values and TOML overrides during
generation. Use `KENVY_API_KEY`, `KENVY_BASE_URL`, and similar names for those
overrides. `mergeKenvy` does not persist environment variable values back into
`local.properties`.

## Configure generated names

Kenvy defaults to `Kenvy` as the generated object name and uses the project
`group` as the generated package. Override either if you need a different API
surface.

```kotlin
kenvy {
    packageName.set("com.example.config")
    interfaceName.set("AppConfig")
}
```

Defaults:

- `packageName`: `project.group.toString()`
- `interfaceName`: `Kenvy`
- `generatedPropertyNameStyle`: `lower-camel`
- `generatedVisibility`: `public`
- `platform`: empty
- `variant`: empty
- `cacheGeneratedOutput`: `false`

Kenvy applies naming conversion only when it writes Kotlin source. Contract
keys for `kenvy.toml`, `[overrides.*]`, `local.properties`,
`local.properties.example`, and `KENVY_<NORMALIZED_NAME>` environment
variables do not change.

If you need the previous preserved-name API during migration, configure the
compatibility style:

```kotlin
kenvy {
    generatedPropertyNameStyle.set("preserve")
}
```

That keeps names such as `api_key` in generated Kotlin source while the rest of
the resolution model continues to use the same canonical contract keys.

## Configure generated visibility

Generated declarations are public by default. If your shared module has a
constrained public API surface, configure `generatedVisibility` to keep
configuration types internal to the module:

```kotlin
kenvy {
    generatedVisibility.set("internal")
}
```

With `"internal"`, the generated object receives the `internal` modifier:

```kotlin
internal object Kenvy {
    val apiKey: String = "value"
}
```

For KMP projects, the common `expect` object and all platform `actual` objects
receive the same modifier:

```kotlin
internal expect object Kenvy { ... }
internal actual object Kenvy { ... }
```

The two accepted values are `"public"` (default) and `"internal"`. Any other
value fails the build with a clear error.

Kenvy emits a lifecycle diagnostic when generating with the default public
visibility. Set `generatedVisibility.set("internal")` to suppress it for
API-surface-sensitive shared modules.

Only set `platform` and `variant` directly when you need common generation to
resolve as a specific target outside target-specific tasks. When KMP target
tasks are present, Kenvy also registers target-specific generation tasks from
the declared targets.

If you deliberately want build-cache storage for generated source that may
contain resolved secrets, you can opt in:

```kotlin
kenvy {
    cacheGeneratedOutput.set(true)
}
```

Leave this off unless you have reviewed how generated artifacts move between
developers and CI environments.

## Keep secrets out of version control

Kenvy can mark individual properties as sensitive and can verify that local
secret-bearing files are Git-ignored.

```toml
[security]
secret_files = ["local.properties", "secrets/dev.properties"]
```

`secret_files` entries must be quoted project-relative paths. Absolute paths
and `..` traversal are rejected.

Kenvy warns when configured secret files exist in the project and are missing
from `.gitignore`. The warning does not print file contents. `local.properties`
is always treated as a secret-bearing file; add more project-relative paths
when needed.

Add `local.properties` to `.gitignore` before you store any scoped local
secrets in it.

## Read diagnostics

Kenvy emits diagnostics at task execution time. Expect actionable output during
`generateKenvy`, `generateKenvyExample`, or `mergeKenvy`, not as eager
configuration-time warnings.

Common failures include:

- Missing placeholder values
- Type mismatches such as `abc` for an `Int`
- Unknown override keys
- Invalid `packageName` or `interfaceName` values
- Secret files that are not ignored by Git

### Missing kenvy.toml

When `kenvy.toml` cannot be found, Kenvy names the exact expected path, the
Gradle project/module where the plugin is applied, and its project directory:

```text
Kenvy: kenvy.toml not found.
Expected config file: /absolute/path/to/shared/kenvy.toml
Gradle project: :shared
Project directory: /absolute/path/to/shared
By default, Kenvy resolves kenvy.toml relative to the Gradle project/module where io.github.adriandleon.kenvy is applied.
If your contract lives elsewhere, configure kenvy { configFile.set(...) }.
```

### Missing local values

When a required property has no resolved value, the diagnostic lists which
`local.properties` files were checked and their file-system status:

```text
Missing values:
  - api_key (String)
    Resolution source: default
    Add to local.properties:
      + api_key.android.debug=<YOUR_VALUE>
    Checked local properties files:
      - root: /absolute/path/local.properties (missing)
      - module :shared: /absolute/path/shared/local.properties (missing)
    Docs: docs/examples.md#multi-module-build-with-shared-module-and-root-localproperties
```

For single-project builds the list contains only the single root `local.properties`.
When you use `localPropertiesFiles.setFrom(...)` to replace defaults, only your
custom file is listed.

When a property has a `help_url`, Kenvy includes it in the missing-value
report when the URL is present and non-blank.

Sensitive values are masked in diagnostics and in `local.properties.example`.
Generated source can still contain resolved values, which is why
`cacheGeneratedOutput` stays conservative by default.

## Use mergeKenvy

Use `mergeKenvy` when you want to backfill missing contract values into
`local.properties` without overwriting values already there.

```sh
./gradlew mergeKenvy
```

`mergeKenvy` preserves local-only keys, keeps existing values, and uses
contract/default resolution when writing new keys. It does not store
environment variable values into `local.properties`.

## Next steps

Use [examples](examples.md) to match your target layout, then review
[known limitations](known-limitations.md) before adopting Kenvy in a shared
team workflow. Maintainers preparing a release should use the
[release checklist](release-checklist.md) to record GitHub, Portal, workflow,
and public consumer smoke evidence.
