# Kenvy known limitations

This page documents the current MVP boundaries so external adopters understand
what is stable, what is intentionally deferred, and what current checks do not
guarantee.

## Public release channel

Kenvy resolves from the Gradle Plugin Portal with plugin ID
`io.github.adriandleon.kenvy`. The current verified consumer version is
`0.1.2`, and the supported normal install path is the Gradle plugins DSL:

```kotlin
plugins {
    id("io.github.adriandleon.kenvy") version "0.1.2"
}
```

Kenvy does not currently claim Maven Central availability as a separate
consumer channel. Maintainers can still use local Maven staging for development
fallbacks, but public consumers should follow the Portal install path. See
[release checklist](release-checklist.md) for the release evidence process and
for handling temporary GitHub Release versus Portal version drift.

## Git ignore verification is intentionally narrow

Kenvy verifies that existing secret-bearing files are present in `.gitignore`,
but the current matcher does not implement full Git wildcard and glob semantics.
The verifier handles exact matches, anchored prefix matches (rules that start
with `/` or contain a `/` before the end), directory suffix matches, and
contained path segment matches for non-anchored rules. Complex glob patterns
such as `*.env`, `**/*.secret`, and character classes are not interpreted as Git
would interpret them.

Simple exact entries such as `local.properties` or anchored entries such as
`/config/secrets.properties` are the safest path for the MVP. If your
repository relies on wildcard or glob ignore rules for secret files, validate
them manually before treating Kenvy warnings as authoritative.

**The verifier does not mutate `.gitignore` or any secret file.** It reads
`.gitignore` at task execution time and writes only diagnostic output.

**Missing configured secret files do not warn.** Kenvy only warns for secret
files that actually exist in the project. If you list a file in
`kenvy.toml [security] secret_files` but that file does not yet exist,
no warning is emitted for it. The warning appears as soon as the file is
created.

## External provider support is internal-only

The codebase contains timeout infrastructure for external providers, but the
MVP does not expose a public provider integration contract. The internal timeout
gate groups requests by provider name, applies a 30-second default timeout per
provider batch, avoids leaking secret values in timeout or failure messages, and
rejects configurations that assign the same property to multiple providers.

Treat this as internal groundwork for a later phase. There is no public API for
implementing a custom provider adapter in the MVP.

## Diagnostics appear at task execution time

Kenvy emits its actionable diagnostics while tasks execute. It does not promise
early configuration-time warnings or IDE-sync-time validation for every issue.
Gradle configuration-phase or portal-phase diagnostics are not part of the MVP
design.

Use task output from `generateKenvy`, `generateKenvyExample`, and `mergeKenvy`
as the supported diagnostics path for the MVP.

## Local properties lookup reads root then module

Kenvy reads the root `local.properties` (next to `settings.gradle.kts`) first,
then the module `local.properties` second. Module values override root values
for the same key. Scoped keys such as `api_key.android.debug` follow the same
merge order.

Use `kenvy { localPropertiesFiles.from(...) }` to append additional files or
`kenvy { localPropertiesFiles.setFrom(...) }` to replace the default list.
Missing configured files are silently skipped. When a required property is
unresolved, Kenvy lists every configured file that was checked and whether
each file exists, so you can identify the correct path to create.

## Unprefixed environment overrides are legacy-only

Kenvy resolves environment overrides from `KENVY_<NORMALIZED_NAME>` by default.
It ignores unprefixed names such as `API_KEY` unless you opt in with
`kenvy { legacyUnprefixedEnvironmentOverrides.set(true) }`.

This avoids accidental collisions with ambient build variables such as
`PLATFORM_NAME`, `CONFIGURATION`, and `HOME`. When Kenvy detects a known unsafe
unprefixed variable for a declared property and no safe source resolves that
property, generation fails with a resolution conflict that tells you how to move
to the supported `KENVY_` name, configure another explicit source, or enable the
legacy opt-in explicitly.

## iOS variant selection is manual

Kenvy uses the canonical platform key `ios` for every iOS architecture target,
and scoped local keys such as `api_key.ios.release` are supported. Kenvy does
not infer an iOS variant automatically from Gradle task names. If you need
variant-specific iOS values, set `kenvy.variant` explicitly in the Gradle build
before running generation.

## Generated output caching is conservative by default

Kenvy defaults `cacheGeneratedOutput` to `false` because generated output can
contain values resolved from `local.properties` or environment variables. If you
enable caching, review your build-cache policy carefully before sharing
artifacts across developers or CI environments. Generated source may embed
credentials or environment-specific values that should not travel between
machines via the build cache.

## Package and object naming are validated strictly

Kenvy uses the project `group` as the default package for generated code and
`Kenvy` as the default object name. Invalid package names or object names fail
generation instead of silently producing broken source.

Set `kenvy.packageName` and `kenvy.interfaceName` explicitly if your defaults
do not form valid Kotlin identifiers.

## Generated declarations are public by default

Kenvy generates declarations without an explicit visibility modifier, making
them public in Kotlin. Shared modules with a constrained public API surface
should set `kenvy { generatedVisibility.set("internal") }` to generate
`internal object` declarations that remain module-private.

When using the default public visibility, Kenvy emits a lifecycle diagnostic
at task execution time. This diagnostic fires for any build using public
visibility, including when `generatedVisibility.set("public")` is set
explicitly. Set `generatedVisibility.set("internal")` to suppress it.

Only `"public"` and `"internal"` are accepted values. `"private"` and
`"protected"` are not valid for top-level generated declarations and will fail
the build.

## Generated Kotlin names can collide after lower camel conversion

Kenvy keeps contract keys canonical, but it now emits generated Kotlin
properties in lower camel case by default. That means distinct contract keys
such as `app_platform_name` and `appPlatformName` can both map to the same
generated accessor.

When that happens, generation fails before Kenvy writes ambiguous source. If
you need temporary migration compatibility, configure
`kenvy { generatedPropertyNameStyle.set("preserve") }`.

## Next steps

Check [release checklist](release-checklist.md) before releasing and use
[getting started](getting-started.md) to stay within the tested MVP behavior.
