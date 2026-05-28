# Kenvy docs map

Use this map before making claims about Kenvy behavior. Search public docs,
source, and tests with `rg`; never cite internal `_bmad-output` planning files
as product documentation.

## Public docs

- `README.md`: product overview, requirements, quick start, task names,
  resolution order, platform keys, and current release status.
- `docs/toolchain-compatibility.md`: exact Kotlin, Gradle, AGP, Java, Android,
  iOS, CI, and unsupported-combination evidence.
- `docs/getting-started.md`: install steps, `kenvy.toml` schema, extension
  defaults, local values, overrides, generated names, secret safety,
  diagnostics, and `mergeKenvy`.
- `docs/examples.md`: tested copy-paste examples for common-only KMP, Android,
  iOS, multi-module root local properties, custom generated names, example
  generation, merge, and CI usage.
- `docs/known-limitations.md`: unsupported or deferred behavior, including
  release-channel boundaries, provider adapters, diagnostics timing, iOS
  variants, build-cache caution, and naming collisions.
- `docs/release-checklist.md`: release gates, GitHub and Portal evidence,
  public consumer smoke proof, Portal credential requirements, and version
  drift handling.

## Source and tests

- `kenvy-plugin/src/main/kotlin/io/github/adriandleon/kenvy/KenvyPlugin.kt`:
  extension defaults, task registration, KMP source-set wiring, target tasks,
  local properties defaults, and env provider wiring.
- `KenvyExtension.kt`: public Gradle extension properties.
- `KenvyConfig.kt`: TOML parsing, property metadata, overrides, security
  fields, and schema validation.
- `KenvyResolver.kt`: resolution order, local key scoping, env var naming,
  scoped env precedence, legacy env behavior, and env collisions.
- `GenerateKenvyTask.kt`: generated source shape, object/package validation,
  property-name styles, missing-value checks, type checks, and cache policy.
- `GenerateKenvyExampleTask.kt`, `MergeKenvyTask.kt`, and
  `KenvyExampleGenerator.kt`: example and merge behavior.
- `kenvy-plugin/src/test/kotlin/io/github/adriandleon/kenvy/`: unit evidence
  for parser, resolver, masking, metadata, merge, diagnostics, naming, and
  gitignore behavior.
- `kenvy-plugin/src/functionalTest/kotlin/io/github/adriandleon/kenvy/`:
  TestKit evidence for examples, Android/iOS behavior, task wiring, and
  scoped CI environment overrides.

## Search patterns

Use focused searches before editing:

```sh
rg -n "generateKenvy|mergeKenvy|generateKenvyExample" README.md docs kenvy-plugin/src
rg -n "localPropertiesFiles|legacyUnprefixed|generatedPropertyNameStyle|platform|variant" README.md docs kenvy-plugin/src
rg -n "KENVY_|toScopedEnvVarNames|api_key.android|overrides.android" README.md docs kenvy-plugin/src
rg -n "Plugin Portal|public consumer smoke|KENVY_PUBLIC_VERSION|publishToMavenLocal" README.md docs agent-skills
rg -n "Kotlin|AGP|Android Gradle|Gradle|Java|JDK|toolchain|compatibility" README.md docs agent-skills .github kenvy-plugin/src scripts gradle
```

When docs and source appear to disagree, treat source plus tests as the
behavioral truth and update public docs only if the user asked for docs work or
the mismatch blocks the requested Kenvy change.
