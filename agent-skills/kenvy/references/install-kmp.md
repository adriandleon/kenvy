# Installing Kenvy in KMP and Compose projects

Kenvy belongs in the Gradle module that owns `kenvy.toml`. Preserve the
consumer project's existing KMP, Android, iOS, desktop, and Compose layout.

## Release state

The plugin ID is `io.github.adriandleon.kenvy`. Kenvy is published on the
Gradle Plugin Portal, and normal consumers should install the documented Portal
version with the plugins DSL:

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    id("io.github.adriandleon.kenvy") version "0.1.2"
}
```

Before changing the documented version, read `README.md`,
`docs/known-limitations.md`, and `docs/release-checklist.md`, then verify the
GitHub release, Gradle Plugin Portal page, Release workflow run, and public
consumer smoke evidence. Maintainers may use local Maven staging for plugin
development fallback, but it is not the normal install path for external
consumers.

## Module ownership

Find the module that should own the contract:

- Single-module KMP: the root project usually owns `kenvy.toml`.
- Multi-module app: the shared KMP module often owns `shared/kenvy.toml`.
- Compose Multiplatform: the module that exposes shared code to Compose should
  usually own the generated Kenvy API.
- Android app plus shared module: don't apply Kenvy to the Android app module
  unless app code, not shared KMP code, owns the generated API.

If ownership is ambiguous, stop and ask. Don't create a new module or move
source sets only to fit Kenvy.

## Common-only KMP

Use this flow when the project only needs shared generated values:

1. Apply the Kotlin Multiplatform plugin and Kenvy in the owning module.
2. Set `group` to a valid Kotlin package or configure `kenvy.packageName`.
3. Create `kenvy.toml` in that module unless `kenvy.configFile` points
   elsewhere.
4. Run `./gradlew generateKenvy`.
5. Compile the shared source set, such as `./gradlew compileKotlinMetadata` or
   the module's existing compile task.

Kenvy writes shared generated source under
`build/generated/kenvy/commonMain/kotlin`.

## Android plus iOS KMP

Use this flow when Kenvy must generate `expect`/`actual` objects:

1. Inspect declared KMP targets before editing.
2. Keep existing Android and iOS target declarations.
3. Use `[overrides.android]` and `[overrides.android.<variant>]` for Android
   values.
4. Use the canonical `[overrides.ios]` key for every iOS architecture target.
5. Let Android variant-bearing tasks infer a single variant when possible, or
   set `kenvy.variant` explicitly for lifecycle or multi-variant tasks.
6. Set iOS variants explicitly with `kenvy.variant`; Kenvy doesn't infer iOS
   variants from task names.

Kenvy registers target tasks from declared production targets, such as
`generateKenvyAndroid`, `generateKenvyIos`, `generateKenvyIosArm64`, and
`generateKenvyIosSimulatorArm64`.

## JVM or desktop KMP

For JVM and desktop targets, keep the existing target names and source sets.
Kenvy can register target-specific generation for production targets, including
`generateKenvyJvm` when a JVM target exists. Use `[overrides.jvm]` when the
source or tests confirm that the generated task uses `jvm` as the platform key.

## Compose Multiplatform

For Compose Multiplatform projects, install Kenvy where shared UI or shared
business code needs the generated config. Don't change Compose entry points,
resources, or navigation to introduce Kenvy. Update call sites to the generated
object, such as `Kenvy.apiKey`, only after generation succeeds.

## Validation

Run the smallest generation and compile checks that cover affected source sets.
Examples:

```sh
./gradlew generateKenvy
./gradlew compileKotlinMetadata
./gradlew compileDebugKotlinAndroid
./gradlew compileKotlinIosArm64
```

If the generated API feeds app code, compile that app or shared module before
reporting completion.
