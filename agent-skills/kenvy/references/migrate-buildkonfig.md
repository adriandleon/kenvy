# Migrating from BuildKonfig

Use this reference when a project uses the `com.codingfeline.buildkonfig`
Gradle plugin and wants to adopt Kenvy. Keep the migration incremental and
prove parity before removing BuildKonfig.

## Concept mapping

| BuildKonfig | Kenvy |
| --- | --- |
| `com.codingfeline.buildkonfig` | `io.github.adriandleon.kenvy` |
| `packageName = "com.example.app"` | `kenvy { packageName.set("com.example.app") }` |
| `objectName = "BuildKonfig"` | `kenvy { interfaceName.set("BuildKonfig") }` |
| Default generated internal object | `kenvy { generatedVisibility.set("internal") }` |
| `exposeObjectWithName = "PublicConfig"` | `kenvy { interfaceName.set("PublicConfig"); generatedVisibility.set("public") }` |
| `defaultConfigs` fields | `[properties.<name>]` and public defaults |
| `targetConfigs { create("android") { ... } }` | `[overrides.android]`, scoped local keys, or scoped CI env vars |
| Flavor target values | `[overrides.<platform>.<variant>]` or scoped local/CI names |
| Generated object call sites | Generated Kenvy object call sites |

Match `packageName` and `objectName` only when it reduces call-site churn. If
the project can tolerate a new API, prefer the Kenvy default object `Kenvy`.

## Visibility mapping

BuildKonfig generates `internal object BuildKonfig` by default and
`internal expect object BuildKonfig` / `internal actual object BuildKonfig`
when target configs are present.

Kenvy generates `public` (no-modifier) declarations by default. To match
BuildKonfig's internal default:

```kotlin
kenvy {
    interfaceName.set("BuildKonfig")
    generatedVisibility.set("internal")
}
```

To match BuildKonfig's `exposeObjectWithName`:

```kotlin
kenvy {
    interfaceName.set("PublicConfig")
    generatedVisibility.set("public")
}
```

## Supported types

Kenvy supports `String`, `Int`, `Boolean`, and `Long`. BuildKonfig also
supports behavior that is not equivalent in Kenvy, including:

- `Float`
- Nullable fields
- `const` options
- JS export behavior (no Kenvy equivalent for `exposeObjectWithName` JS behavior)
- Flavor selection through `buildkonfig.flavor`
- Gradle-DSL-defined values as the primary contract
- HMPP target-config behavior that has no direct Kenvy concept

Don't claim a one-to-one migration for those features. Either keep BuildKonfig
for that surface temporarily, change the consumer code, or ask the user to
choose a different representation.

## Migration flow

1. Inventory BuildKonfig configuration, generated package/object names,
   targets, flavors, field types, and call sites.
2. Add Kenvy beside BuildKonfig in the same owning module or the closest KMP
   module that should own the contract.
3. Create `kenvy.toml` with public defaults under `[properties.<name>]`.
4. Move environment-specific or secret values to `[overrides.*]`,
   `local.properties`, or scoped `KENVY_` env vars.
5. Configure `packageName` and `interfaceName` only when matching the existing
   generated API is useful.
6. Run `generateKenvy` and inspect generated accessors.
7. Update call sites in small batches.
8. Compile affected source sets.
9. Remove BuildKonfig only after generated values and call sites reach parity.

## Target and flavor mapping

For a BuildKonfig Android debug value, prefer this Kenvy shape when the value is
public:

```toml
[properties.api_key]
type = "String"
default = "placeholder"
sensitive = true

[overrides.android.debug]
api_key = "placeholder"
```

For local or CI secrets, keep the TOML placeholder and use names only:

- `api_key.android.debug` in local properties.
- `KENVY_API_KEY_ANDROID_DEBUG` in CI.

BuildKonfig's flavored precedence doesn't map exactly to Kenvy. Model flavors
as Kenvy variants only when one Gradle generation context can select the
variant explicitly or infer it for Android.

## Parity checklist

Before removing BuildKonfig, verify:

- Generated package and object expectations.
- Field names and lower-camel conversion.
- Supported field types.
- Target-specific values.
- Flavor or variant behavior.
- Visibility: configure `generatedVisibility.set("internal")` to match BuildKonfig's default internal generated object.
- Call sites in common, Android, iOS, JVM, and tests.
- CI and local secret names.
- Generation and compile tasks.

Stop if any BuildKonfig feature has no Kenvy equivalent and the user hasn't
approved a behavior change.
