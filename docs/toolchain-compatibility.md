# Toolchain compatibility

This matrix is the source of truth for Kenvy's verified Kotlin, Gradle,
Android Gradle Plugin (AGP), Java, Android, and iOS toolchains. Version claims
are intentionally exact: if CI validates one patch version, this document names
that patch version instead of claiming the whole major or minor range.

## Current matrix

| Stack | Kotlin | Gradle | AGP | Gradle runtime JDK | Java toolchain target | Android fixture shape | iOS fixture shape | CI job | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Baseline Android KMP | `2.1.20` | `9.4.1` | `8.5.2` | `17` | `17` | `com.android.library` plus `org.jetbrains.kotlin.multiplatform` with `androidTarget()` | Not exercised | `compatibility-android-baseline` | Validated |
| Modern Android KMP | `2.3.20` | `9.4.1` | `9.2.0` | `17` | `17` | `com.android.kotlin.multiplatform.library` plus `org.jetbrains.kotlin.multiplatform` with `kotlin { android { ... } }` | Not exercised | `compatibility-android-modern` | Validated |
| Modern iOS KMP | `2.3.20` | `9.4.1` | N/A | `17` | Native target | Not exercised | `iosArm64()` and `iosSimulatorArm64()` | `compatibility-ios-modern` | Validated on macOS |
| Modern JVM toolchain | `2.3.20` | `9.4.1` | N/A | `21` | `21` | Not exercised | Not exercised | `compatibility-jvm-modern` | Validated |

The development baseline remains Kotlin `2.1.20`, Gradle wrapper/catalog
`9.4.1`, Java target/runtime `17`, AGP example fixtures `8.5.2`, and Kenvy
development version `0.1.3-SNAPSHOT`.

## Unsupported combinations

- AGP `9.x` Android KMP library modules should not use the legacy
  `com.android.library` plus `androidTarget()` fixture shape. AGP's built-in
  Kotlin mode uses `com.android.kotlin.multiplatform.library` for KMP library
  Android targets.
- AGP `9.2.0` requires JDK `17` as its Android build runtime. Kenvy validates
  Java `21` separately through a JVM toolchain scenario instead of running AGP
  on Java `21`.
- The Android-KMP library plugin is single-variant. Kenvy still supports
  Android variant-scoped values for legacy Android library fixtures; modern
  Android-KMP validation covers the canonical Android platform value without
  build-type or flavor claims.

## Verification commands

Run these commands when changing Kotlin, Gradle, AGP, Java, Android target, iOS
target, generated visibility, or generated source-set wiring:

```sh
./gradlew :kenvy-plugin:test :kenvy-plugin:functionalTest
KOTLIN_VERSION=2.1.20 AGP_VERSION=8.5.2 JAVA_VERSION=17 ./scripts/compatibility-matrix.sh android-baseline
KOTLIN_VERSION=2.3.20 AGP_VERSION=9.2.0 JAVA_VERSION=17 JAVA_TOOLCHAIN_VERSION=17 ./scripts/compatibility-matrix.sh android-modern
KOTLIN_VERSION=2.3.20 JAVA_VERSION=17 ./scripts/compatibility-matrix.sh ios-modern
KOTLIN_VERSION=2.3.20 JAVA_VERSION=21 JAVA_TOOLCHAIN_VERSION=21 ./scripts/compatibility-matrix.sh jvm-modern
```

The compatibility fixtures assert that generated `internal expect object` and
`internal actual object` declarations compile from same-module consumer source.
The existing functional suite also preserves the default public output shape,
including the no-explicit-`public` behavior.

## Source notes

- AGP `9.2.0` documents Gradle `9.4.1` and JDK `17` compatibility in the
  Android release notes.
- Android's Android-KMP library plugin guide identifies
  `com.android.kotlin.multiplatform.library` as the supported KMP Android
  library plugin shape and documents the `kotlin { android { ... } }`
  configuration block.
- Kotlin `2.3.20` documents full compatibility through Gradle `9.3.0` and
  allows later Gradle releases with possible deprecation warnings.
- Gradle's current compatibility matrix lists Java `21` toolchain support from
  Gradle `8.4`, Java `21` runtime support from Gradle `8.5`, and tested Kotlin
  `2.0.0` through `2.3.20`.
