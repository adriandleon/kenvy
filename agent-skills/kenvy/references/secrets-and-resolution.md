# Secrets and resolution

Use this reference when working with `local.properties`, CI variables,
placeholder values, sensitive metadata, or secret safety.

## Resolution order

Kenvy resolves each property with later sources winning:

1. `properties.<name>.default`
2. `overrides.common.<name>`
3. `overrides.<platform>.<name>`
4. `overrides.<platform>.<variant>.<name>`
5. Root and module local properties using `<name>`
6. Root and module local properties using `<name>.<platform>`
7. Root and module local properties using `<name>.<platform>.<variant>`
8. Unprefixed environment variable only when legacy opt-in is enabled
9. `KENVY_<NORMALIZED_NAME>`
10. `KENVY_<NORMALIZED_NAME>_<NORMALIZED_PLATFORM>`
11. `KENVY_<NORMALIZED_NAME>_<NORMALIZED_PLATFORM>_<NORMALIZED_VARIANT>`

Blank more-specific env vars don't mask nonblank less-specific values.

## Local properties

Default lookup reads the root `local.properties` first, then the module
`local.properties`; module values win for the same key. When the plugin is
applied to the root project, Kenvy reads the root file once.

Use scoped keys for one logical property:

```properties
api_key=<fill locally>
api_key.android=<fill locally>
api_key.android.debug=<fill locally>
api_key.ios.release=<fill locally>
```

Don't commit real local values. Ensure `local.properties` and any custom local
files are ignored before storing secrets. Use exact `.gitignore` entries when
possible because Kenvy's gitignore matcher is intentionally narrow.

Custom local files can be appended or replace defaults:

```kotlin
kenvy {
    localPropertiesFiles.from(layout.projectDirectory.file("config/local.properties"))
    // localPropertiesFiles.setFrom(layout.projectDirectory.file("config/custom.properties"))
}
```

## CI environment variables

Kenvy is CI-agnostic. CI systems must expose secrets as environment variables
visible to the Gradle process.

For property `api_key`, platform `android`, and variant `debug`, the safe
candidates are:

- `KENVY_API_KEY`
- `KENVY_API_KEY_ANDROID`
- `KENVY_API_KEY_ANDROID_DEBUG`

For iOS release, use the canonical platform key:

- `KENVY_API_KEY_IOS_RELEASE`

Use CI provider secret stores to populate those env vars. Don't generate
`local.properties` in CI as the primary path; file generation is only a
compatibility fallback.

Unprefixed variables such as `API_KEY` are ignored unless the build opts in
with:

```kotlin
kenvy {
    legacyUnprefixedEnvironmentOverrides.set(true)
}
```

Use that only as a temporary migration bridge. `KENVY_` variables still win
when both names are present.

## Sensitive values

Mark secret-like properties as sensitive:

```toml
[properties.api_key]
type = "String"
default = "placeholder"
sensitive = true
```

Kenvy masks sensitive values in diagnostics and example output. Generated
source can still contain resolved values, so don't share generated output or
build-cache artifacts without reviewing secret exposure.

## Manual secret steps

Always report names, never values:

```text
Manual secret steps:
- Add api_key.android.debug to local.properties for local Android debug builds.
- Add KENVY_API_KEY_ANDROID_DEBUG in CI for Android debug builds.
- Add KENVY_API_KEY_IOS_RELEASE in CI for iOS release builds.
```

If you discover literal real secrets in tracked files, stop and ask how the
user wants to handle cleanup. Git history cleanup is a separate security
process.
