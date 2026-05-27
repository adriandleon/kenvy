# Migrating ad hoc configuration to Kenvy

Use this reference when a project stores configuration in Android
`BuildConfig`, `resValue`, direct Gradle parsing, checked-in constants,
CI-generated files, shell scripts, or native Apple handoff files.

## Android BuildConfig and resValue

1. Inventory fields, resource names, variants, and call sites.
2. Move public defaults to `[properties.<name>]` in `kenvy.toml`.
3. Move Android-specific public values to `[overrides.android]` or
   `[overrides.android.<variant>]`.
4. Move secrets to local scoped keys or scoped `KENVY_` CI env vars.
5. Replace app code with generated Kenvy accessors, such as `Kenvy.apiKey`.
6. Compile the Android variant that uses the migrated values.

Don't keep two writable sources of truth for the same value after parity is
proven.

## Direct local.properties parsing

Keep untracked local storage, but move the schema, generated API, diagnostics,
and resolution rules to Kenvy:

1. Add a Kenvy property for each logical value.
2. Keep existing local keys only if they match the Kenvy property names.
3. Rename local keys manually when needed; report names, not values.
4. Remove custom Gradle parsing after generation and compile checks pass.

## gradle.properties, -P, and shell env

Separate public build settings from secrets:

- Keep non-secret Gradle behavior in `gradle.properties` or `-P` when it truly
  controls the build rather than app configuration.
- Move app configuration contracts to `kenvy.toml`.
- Prefer scoped `KENVY_` env vars for CI secrets.
- Use legacy unprefixed env overrides only as a temporary opt-in bridge.

## Checked-in Kotlin constants

Move only public values into `kenvy.toml`. Move secrets out of tracked source to
local files or CI env vars, then update call sites to the generated Kenvy API.

If tracked source already contains real secrets, stop and ask. Removing the
current file value is not enough; Git history cleanup and credential rotation
are separate security processes.

## CI-generated files

Prefer scoped `KENVY_` env vars visible to Gradle. Generating
`local.properties` in CI is a compatibility fallback, not the primary Kenvy
path.

When a legacy CI-generated file must remain temporarily:

1. Configure `localPropertiesFiles` only when the file path is stable.
2. Ensure generated files are not committed.
3. Plan removal after CI env vars are wired.

## Xcode, Info.plist, and Swift handoff

Kenvy generates Kotlin/KMP source. It doesn't provide a Swift runtime API,
Info.plist automation, or Xcode secret bridge in the current public docs.

For KMP iOS code:

- Use `[overrides.ios]` for public iOS values.
- Use `api_key.ios.release` style local keys for local iOS variants.
- Use `KENVY_API_KEY_IOS_RELEASE` style CI env vars.
- Set `kenvy.variant` explicitly for iOS variant-specific generation.

Don't invent native Apple automation. If the app needs Swift-visible values,
ask how the existing KMP-to-Swift boundary exposes generated Kotlin APIs.
