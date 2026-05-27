# Troubleshooting Kenvy changes

Use this reference when generation, configuration, or migration does not behave
as expected. Confirm behavior against public docs, source, and tests before
claiming a plugin bug.

## Missing kenvy.toml

1. Confirm which Gradle module applies `io.github.adriandleon.kenvy`.
2. Check whether `kenvy.toml` exists in that module's project directory.
3. If the contract lives elsewhere, check `kenvy { configFile.set(...) }`.
4. Do not move source sets or create a new module just to satisfy Kenvy.

If ownership is ambiguous, stop and ask which module should own the contract.

## Unresolved placeholders

1. Check the property name in `kenvy.toml`.
2. Check public overrides in `[overrides.common]`, `[overrides.<platform>]`, and
   `[overrides.<platform>.<variant>]`.
3. Check local key names only by name, not by printing secret values.
4. Check CI env var names, including scoped `KENVY_` names.
5. If a `help_url` exists, report it to the user.

Report missing local and CI names without values.

## Wrong package or object name

Kenvy uses the Gradle project `group` as the default generated package and
`Kenvy` as the default object name. If generated imports or call sites do not
match, inspect:

- `kenvy.packageName`
- `kenvy.interfaceName`
- Existing imports and generated object references
- `generatedPropertyNameStyle`

Run generation before changing call sites broadly.

## Scoped key mistakes

Use one logical property name and add scopes only to the source that provides
the value:

- Local: `<name>`, `<name>.<platform>`, `<name>.<platform>.<variant>`
- CI: `KENVY_<NAME>`, `KENVY_<NAME>_<PLATFORM>`,
  `KENVY_<NAME>_<PLATFORM>_<VARIANT>`

For iOS, the platform segment is always `ios`, even when the Kotlin target is
`iosArm64` or `iosSimulatorArm64`.

## Environment collisions

Unprefixed names such as `API_KEY` are ignored unless
`legacyUnprefixedEnvironmentOverrides` is enabled. Prefer `KENVY_` names and use
legacy opt-in only as a temporary migration bridge.

## Gitignore warnings

Kenvy checks secret-bearing files against `.gitignore`, but the matcher is
intentionally narrow. Prefer exact entries such as `local.properties` or
`/config/secrets.properties`. Do not assume complex glob rules are enough
without manual review.

## Android variants

Android variant-bearing tasks can infer one variant. Lifecycle tasks such as
`build` and `check` do not imply one variant, and multi-variant invocations need
an explicit `kenvy.variant`.

## iOS variants

iOS variant selection is manual. Use the canonical platform key `ios` and set
`kenvy.variant` explicitly when release/debug-specific iOS values are needed.

## Generated source may contain values

Generated source can contain resolved local or CI values. Keep generated output
out of shared caches unless the team has reviewed the exposure risk, and do not
paste generated secret-bearing source into logs or review comments.
