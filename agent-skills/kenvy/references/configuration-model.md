# Kenvy configuration model

Use this reference when creating or changing `kenvy.toml` or the `kenvy { ... }`
Gradle extension.

## Contract file

By default, Kenvy reads `kenvy.toml` from the Gradle project directory where
the plugin is applied. Configure `kenvy.configFile` only when the contract must
live elsewhere.

Each property lives under `[properties.<name>]`. Supported types are:

- `String`
- `Int`
- `Boolean`
- `Long`

Supported property metadata fields are:

- `description`
- `help_url`
- `sensitive`

Use public defaults in TOML. Use placeholders for required local or CI-provided
values. Don't store literal real secrets in `kenvy.toml`.

## Overrides

Kenvy supports these override tables:

```toml
[overrides.common]
[overrides.<platform>]
[overrides.<platform>.<variant>]
```

Use one logical property name across defaults, overrides, local files, CI env
vars, and generated Kotlin accessors. For example, one `api_key` property can
resolve differently for common, Android, Android debug, iOS, and iOS release
without introducing separate generated APIs.

## Generated names

Kenvy uses the Gradle project `group` as the default generated package and
`Kenvy` as the default generated object name. Override them only when the
project already has a different public API expectation:

```kotlin
kenvy {
    packageName.set("com.example.config")
    interfaceName.set("AppConfig")
}
```

Generated Kotlin property names default to lower camel case:

- `api_key` becomes `apiKey`
- `retry_count` becomes `retryCount`
- `timeout_ms` becomes `timeoutMs`

Contract keys in `kenvy.toml`, overrides, `local.properties`, examples, and
`KENVY_` environment variables stay canonical. Use
`generatedPropertyNameStyle.set("preserve")` only as a migration bridge for
existing call sites that still use preserved names.

## Extension properties

The public extension includes:

- `configFile`
- `packageName`
- `interfaceName`
- `platform`
- `variant`
- `cacheGeneratedOutput`
- `generatedPropertyNameStyle`
- `generatedVisibility`
- `legacyUnprefixedEnvironmentOverrides`
- `localPropertiesFiles`

### generatedVisibility

Controls the visibility modifier on the generated top-level object and its
members. Accepted values are `"public"` (default) and `"internal"`. Any other
value fails the build.

```kotlin
kenvy {
    generatedVisibility.set("internal")
}
```

With `"internal"`, the generated classifier becomes `internal object`,
`internal expect object`, or `internal actual object` depending on the KMP
target. The default `"public"` preserves the current generated source shape
with no explicit visibility modifier (Kotlin's default is public).

When visibility is not configured or is `"public"`, Kenvy emits a lifecycle
diagnostic at task execution time that points API-surface-sensitive consumers
to this setting.

Set `platform` and `variant` only when generation must resolve as a specific
target or variant. Android target tasks can infer a single variant from
variant-bearing tasks such as `compileDebugKotlinAndroid`, but lifecycle tasks
such as `build` or `check` and multi-variant invocations such as
`assembleDebug assembleRelease` need an explicit `kenvy.variant`. iOS variant
selection is always manual through `kenvy.variant`.

`localPropertiesFiles.from(...)` appends files after the defaults.
`localPropertiesFiles.setFrom(...)` replaces the default root/module lookup.

Leave `cacheGeneratedOutput` disabled unless the team has reviewed whether
generated source containing local or environment-specific values can enter a
shared build cache.

## Security table

Use `[security] secret_files` to list project-relative secret-bearing files
that Kenvy should verify against `.gitignore`.

```toml
[security]
secret_files = ["local.properties", "secrets/dev.properties"]
```

Kenvy rejects absolute paths and `..` traversal. The gitignore verifier is
intentionally narrow; prefer exact entries such as `local.properties` over
complex glob-only rules.
