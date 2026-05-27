---
name: kenvy
description: Helps AI agents install, configure, troubleshoot, manage properties, safely handle secrets, search docs, and migrate to the Kenvy Gradle plugin for Kotlin Multiplatform, KMP, and Compose Multiplatform projects, including kenvy.toml, local.properties, KENVY_ environment variables, CI, property add/update/remove flows, and BuildKonfig migration.
license: Apache-2.0
---

# Kenvy agent workflow

Use this skill when working with Kenvy, the build-time Gradle plugin for
Kotlin Multiplatform configuration. Treat the public repository docs, source,
and tests as the source of truth before making factual claims.

## Operating contract

1. Inspect the project shape before editing.
2. Identify the Gradle module that owns `kenvy.toml`; apply Kenvy only there.
3. Find existing configuration and secret sources, including `.gitignore`.
4. Search public Kenvy docs/source/tests with `rg` before product claims.
5. Choose one flow: install, configure, property operation, troubleshooting, or
   migration.
6. Edit narrowly and preserve existing Gradle, Kotlin, Android, and Compose
   conventions.
7. Validate generation and compile/test tasks before reporting completion.
8. Report exact changed files, validation commands, and unresolved manual
   secret steps.

## Hard guardrails

- Kenvy is build-time configuration; don't add runtime lookup APIs.
- Don't invent Swift, Info.plist, setup wizard, provider adapter, or cloud
  secret APIs.
- The plugin ID is `io.github.adriandleon.kenvy`.
- Kenvy is not yet on the Gradle Plugin Portal; external consumers need the
  documented local staged evaluation path until publication evidence changes.
- iOS uses the canonical platform key `ios`.
- Generated Kotlin property names default to lower camel case.
- Root `local.properties` is read before module `local.properties`; module
  values win for matching keys.
- Safe CI env names are `KENVY_` prefixed and can be generic,
  platform-scoped, or platform-plus-variant-scoped.
- Don't print, commit, log, preserve, or invent literal real secrets.

## Before editing

Capture these facts first:

- Gradle files, included modules, and plugin management repositories.
- KMP targets, Android variants, and iOS variant strategy.
- The module and path that should own `kenvy.toml`.
- Current Kenvy package/object names and generated property naming style.
- Existing `BuildConfig`, `resValue`, BuildKonfig, constants,
  `local.properties`, `gradle.properties`, shell env, and CI secret patterns.
- Whether secret files are ignored by Git.

## Choose a reference

- Install Kenvy in KMP or Compose projects:
  [references/install-kmp.md](references/install-kmp.md).
- Configure `kenvy.toml` and the Gradle extension:
  [references/configuration-model.md](references/configuration-model.md).
- Add, update, rename, or remove properties:
  [references/property-operations.md](references/property-operations.md).
- Handle local values, CI env vars, and secrets:
  [references/secrets-and-resolution.md](references/secrets-and-resolution.md).
- Troubleshoot common Kenvy failures:
  [references/troubleshooting.md](references/troubleshooting.md).
- Migrate from BuildKonfig:
  [references/migrate-buildkonfig.md](references/migrate-buildkonfig.md).
- Migrate ad hoc config patterns:
  [references/migrate-ad-hoc-config.md](references/migrate-ad-hoc-config.md).
- Use agent output templates:
  [references/output-templates.md](references/output-templates.md).
- Find source-of-truth docs, source, and tests:
  [references/docs-map.md](references/docs-map.md).

## Common flows

For install/setup, read the install and configuration references, then apply
the plugin in the owning module, create or edit `kenvy.toml`, configure the
extension only when defaults don't fit, update call sites, and run generation.

For property work, inspect generated API call sites first. Keep one logical
property API when platform, variant, local, or CI values differ. Run generation
and a compile/test task that exercises affected source sets.

For migration, introduce Kenvy beside the existing system, prove parity, update
call sites, validate generated output, and remove the old system only after the
project compiles and the user agrees the migration is complete.

## Validation

Prefer commands that match the changed project. Common checks are:

```sh
./gradlew generateKenvy
./gradlew compileKotlinMetadata
./gradlew compileDebugKotlinAndroid
./gradlew compileKotlinIosArm64
```

For this skill package, maintainers can run:

```sh
skills-ref validate ./agent-skills/kenvy
rg -n --glob '!docs/superpowers/**' "secret-value|android-secret|ios-secret|api[-_ ]?key\s*[:=]|KENVY_[A-Z0-9_]+\s*[:=]" agent-skills docs README.md
```

If `skills-ref` is unavailable, manually verify that frontmatter parses, the
name matches the directory, the description is trigger-focused and under 1024
characters, links resolve, and no real secrets appear.

## Stop conditions

Stop and ask before continuing when real secrets are visible in tracked files,
the plugin publication/version path is unresolved, `kenvy.toml` ownership is
ambiguous, the requested behavior is unsupported by public docs/source, or
validation fails for a reason unrelated to the intended Kenvy change.

## Report format

End with:

- Files changed.
- Kenvy contract/API changes.
- Local or CI secret names the user must set manually, without values.
- Validation run and result.
- Any remaining unsupported behavior or release-state caveat.
