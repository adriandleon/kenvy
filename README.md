# Kenvy

Kenvy is a Gradle plugin for build-time Kotlin Multiplatform configuration. It
turns a `kenvy.toml` contract into typed Kotlin source, example local
properties, and target-specific generated code for the KMP targets you declare.

This repository contains the plugin implementation and its publication wiring
for the Gradle Plugin Portal. A local staged artifact is verified and an
external consumer smoke test passes. The plugin is not yet on the Gradle Plugin
Portal; public release requires Portal credentials and final publication.

## What you get

Kenvy focuses on build-time configuration rather than runtime lookup. The MVP
lets you:

- Declare typed properties in `kenvy.toml`
- Resolve values from defaults, shared overrides, platform overrides, variant
  overrides, scoped `local.properties` entries, and `KENVY_` environment
  variables
- Generate a typed `Kenvy` object for shared code
- Generate target-specific `actual` objects for declared Android, iOS, and JVM
  targets
- Generate `local.properties.example` for onboarding
- Merge missing contract defaults into `local.properties`
- Mask sensitive values in diagnostics and example output
- Warn when local secret files are not ignored by Git

## Requirements

You need a Gradle build that can apply the plugin and a Kotlin Multiplatform
project if you want automatic source-set wiring for `commonMain` and platform
targets.

- Kotlin: `2.1.20`
- Gradle runtime target: Java 17
- Android examples in this repo use AGP `8.5.2`

Kenvy uses your project `group` as the default package name for generated code.
Set `group` to a valid Kotlin package, or configure `kenvy.packageName`
explicitly.

## Quick start

Use this minimal setup to generate shared configuration for `commonMain`.

The plugin ID `io.github.adriandleon.kenvy` is not yet on the Gradle Plugin
Portal. For local staged evaluation, publish to Maven Local first and configure
`mavenLocal()` in `pluginManagement.repositories`. See
[release checklist](docs/release-checklist.md) for the remaining Portal release
steps.

1. Apply the plugin in your Gradle build.
2. Use the plugin ID `io.github.adriandleon.kenvy`.
3. Set `group` or `kenvy.packageName` to a valid Kotlin package.
4. Create `kenvy.toml` in the project root.
5. Run `./gradlew generateKenvy`.

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    id("io.github.adriandleon.kenvy")
}

group = "com.example.app"

kotlin {
    jvm()
}
```

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
```

When you run `generateKenvy`, Kenvy also generates
`local.properties.example`. If `api_key` is still unresolved, the task fails
with an actionable missing-value report instead of generating incomplete code.

## Core tasks

Kenvy registers these user-facing tasks in the `kenvy` task group.

- `generateKenvy`: generates shared Kotlin configuration and also runs
  `generateKenvyExample`
- `generateKenvyExample`: generates `local.properties.example` from the
  contract
- `mergeKenvy`: merges missing contract values into `local.properties` without
  overwriting existing local values

`generateKenvy` does not modify `local.properties`. Use `mergeKenvy` only when
you want to backfill missing contract values into that file.

When the Kotlin Multiplatform plugin is present, Kenvy also registers
target-specific generation tasks from the production targets declared in your
build. Common examples are `generateKenvyAndroid`, `generateKenvyJvm`,
`generateKenvyIos`, `generateKenvyIosArm64`, and
`generateKenvyIosSimulatorArm64`.

## Resolution order

Kenvy resolves each property using a fixed precedence chain. Later entries win
over earlier entries.

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

Local properties files are loaded in order and merged: root `local.properties` first, then
module `local.properties`. Module values win when both files define the same key. When the
plugin is applied to the root project, only the root file is used to avoid duplication.

Use one logical contract property name for every local override. For example,
`api_key` can resolve from `api_key`, `api_key.android`, or
`api_key.android.debug` without changing the generated Kotlin API name.

By default, generated Kotlin properties use lower camel case. For example,
`api_key` becomes `apiKey`, `retry_count` becomes `retryCount`, and
`timeout_ms` becomes `timeoutMs`. If you need the previous preserved-name
behavior during migration, opt in explicitly:

```kotlin
kenvy {
    generatedPropertyNameStyle.set("preserve")
}
```

To add a custom local properties file or replace the defaults, configure the extension:

```kotlin
kenvy {
    // Add a custom file after the defaults (later files override earlier files):
    localPropertiesFiles.from(layout.projectDirectory.file("config/extra.properties"))

    // Replace the entire default file list with a custom file:
    localPropertiesFiles.setFrom(layout.projectDirectory.file("config/custom.properties"))
}
```

Kenvy maps property names to environment variables by uppercasing and replacing
non-alphanumeric separators with underscores, then prefixes the result with
`KENVY_`. For example, `api_key` and `api-key` both map to
`KENVY_API_KEY`. Platform and variant segments use the same normalization:
`KENVY_API_KEY_ANDROID_DEBUG` for platform `android` and variant `debug`.

Kenvy ignores unprefixed variables such as `API_KEY` by default. This avoids
accidental collisions with build-system variables such as `PLATFORM_NAME`
inside Xcode, CI, or Gradle-driven builds. If you need legacy behavior during
migration, opt in explicitly. `KENVY_` variables still win when both names are
present:

```kotlin
kenvy {
    legacyUnprefixedEnvironmentOverrides.set(true)
}
```

## Platform support

Kenvy can generate an `expect object` in `commonMain` and matching `actual`
objects for platform source sets when you apply it to a KMP project.

- Android uses the canonical platform key `android`
- iOS uses the canonical platform key `ios` for all architectures
- JVM uses the canonical platform key `jvm`

Android variant-specific overrides use the form
`[overrides.android.debug]`. Kenvy can infer the Android variant from tasks
such as `compileDebugKotlinAndroid`, or you can set `kenvy.variant`
explicitly.

For local secrets, use the same platform and variant segments in
`local.properties`. For example, `api_key.android.release` overrides
`api_key.android`, which overrides the generic `api_key`. iOS always uses the
canonical platform key `ios`, and iOS variant selection is manual through
`kenvy { variant.set("debug") }` or `kenvy { variant.set("release") }`.

## Documentation

Use the docs in `docs/` for onboarding, examples, release hardening, and known
limits.

- [Getting started](docs/getting-started.md)
- [Examples](docs/examples.md)
- [Known limitations](docs/known-limitations.md)
- [Release checklist](docs/release-checklist.md)

## Current release status

The core plugin implementation is in place, all tests pass, publication wiring
is configured via `com.gradle.plugin-publish` 2.1.1, and a fresh external
consumer smoke test passes against the locally staged artifact. Public release
to the Gradle Plugin Portal requires Portal credentials and final publication.
See [release checklist](docs/release-checklist.md) for the remaining
pre-launch steps.

## Next steps

Start with [Getting started](docs/getting-started.md), then use
[Examples](docs/examples.md) to match your target layout. If you are preparing
the first public release, work through
[Release checklist](docs/release-checklist.md) before publishing anything.
