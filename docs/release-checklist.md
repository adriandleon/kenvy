# Kenvy MVP release checklist

This checklist defines the minimum evidence required before the first public
open-source release of Kenvy. It is intentionally strict because the MVP code
is ahead of its release packaging. Each section is labelled **blocking** or
**advisory** so the go/no-go decision is unambiguous.

## Current status

The repository has passing unit and functional tests, publication wiring via
`com.gradle.plugin-publish` 2.0.0, and a verified local staged artifact that a
fresh external consumer can apply without any repo-local shortcuts.

The **Test gate** and **Consumer smoke gate** pass for local staged validation.
The **Publication gate** passes for local Maven staging only. Public Gradle
Plugin Portal release remains blocked until Portal credentials
(`GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET`) are supplied and the final
publish command succeeds.

<!-- prettier-ignore -->
> [!NOTE]
> `publishToMavenLocal` and the external consumer smoke test pass as of
> 2026-05-09. Re-run `publishPlugins --validate-only` after every metadata
> change before public Portal publication.

## Test gate — blocking

Run these commands from the repository root and keep the command output as
release evidence.

```sh
./gradlew :kenvy-plugin:test :kenvy-plugin:functionalTest
```

This single command covers:

- Unit tests for type-safe code generation, example generation, merge logic,
  hierarchical resolution, sensitive-value masking, naming validation, and
  external provider timeout handling
- Functional tests for `generateKenvy`, `generateKenvyExample`, `mergeKenvy`,
  Android bridge, iOS bridge, Git ignore diagnostics, Gradle isolation and
  Configuration Cache compatibility, and the performance baseline

**Gate passes when:** the build succeeds with no failing unit or functional
tests. Record the full build output.

**Gate fails when:** any test fails, the build exits non-zero, or the output
is not preserved as release evidence.

## Documentation gate — blocking

Verify public docs before release so the project does not ship behavior that
contradicts the test suite.

Run this scan from the repository root and record the output:

```sh
rg -n "publication|Plugin Portal|Maven Central|staged|smoke|wizard|provider|gitignore|cacheGeneratedOutput|generateKenvy|generateKenvyExample|mergeKenvy" README.md docs
```

Review each match to confirm it is consistent with tested behavior and with the
limitations page. Then verify the following:

- `README.md` explains what Kenvy does, how to install it, and how to generate
  the first contract
- `docs/getting-started.md` matches the current plugin DSL and task names
- `docs/examples.md` matches tested Android, iOS, and shared use cases
- `docs/known-limitations.md` documents current deferred items honestly
- Sensitive-value behavior, `.gitignore` checks, and task-time diagnostics are
  explained in user-facing language
- Generated Kotlin naming behavior is documented clearly: contract keys stay
  canonical, generated accessors default to lower camel case, and the
  `generatedPropertyNameStyle` compatibility path is explained
- All relative links among `README.md` and `docs/*.md` resolve to real files

**Gate passes when:** the scan output shows no doc contradicting tested
behavior, and every relative link resolves. Record the scan command and output.

**Gate fails when:** any public doc implies publication is available, implies
unsupported diagnostics timing, or contradicts tested task names or behaviors.

## Publication gate — partial pass (local staged)

`kenvy-plugin/build.gradle.kts` now applies `com.gradle.plugin-publish` 2.0.0,
which automatically applies `maven-publish`. Publication metadata is configured:

- Group: `io.github.adriandleon.kenvy`
- Artifact: `kenvy-plugin`
- Version: `0.1.0`
- Plugin ID: `io.github.adriandleon.kenvy`
- Repository: `https://github.com/adriandleon/kenvy`

**Local staged publication command (verified 2026-05-09):**

```sh
./gradlew :kenvy-plugin:publishToMavenLocal
```

This produces the implementation JAR and the Gradle plugin marker artifact:

```
~/.m2/repository/io/github/adriandleon/kenvy/kenvy-plugin/0.1.0/
~/.m2/repository/io/github/adriandleon/kenvy/io.github.adriandleon.kenvy.gradle.plugin/0.1.0/
```

**Gradle Plugin Portal validation command:**

```sh
./gradlew :kenvy-plugin:publishPlugins --validate-only
```

Run this command before public publication and keep the output as release
evidence.

**Credentials required for Portal publication (not committed):**

Read from Gradle properties or environment variables:

```properties
# ~/.gradle/gradle.properties
gradle.publish.key=<your-key>
gradle.publish.secret=<your-secret>
```

Or via environment: `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`.

**Signing (opt-in):** Configure `signing.key` and `signing.password` in
`~/.gradle/gradle.properties`, or set `SIGNING_KEY` and `SIGNING_PASSWORD`
environment variables. Not required for `publishToMavenLocal`.

**Gate passes for local Maven staging.** Public Portal release is still blocked
by missing Portal credentials and the final publish command.

## Consumer smoke gate — passing

Verified 2026-05-09 with a fresh project outside this repository using no
`withPluginClasspath()`, `includeBuild`, or source checkout shortcuts.

**Smoke project structure:**

```
kenvy-consumer-smoke-20260509/
├── settings.gradle.kts   # pluginManagement { repositories { mavenLocal() … } }
├── build.gradle.kts      # id("io.github.adriandleon.kenvy") version "0.1.0"
└── kenvy.toml            # [properties.base_url] type = "String" …
```

**Commands run:**

```sh
./gradlew generateKenvy generateKenvyExample compileKotlinJvm
```

**Verified outputs:**

- `build/generated/kenvy/commonMain/kotlin/com/example/smoke/Kenvy.kt` exists
- `local.properties.example` exists with correct property scaffolding
- `compileKotlinJvm` succeeds — consumer code compiles against the generated API
- `BUILD SUCCESSFUL` with no TestKit or classpath shortcuts

**Gate passes for local staged artifact.** Re-run the smoke project after
Portal publication using the public plugin coordinate.

## Release evidence to preserve

For each passing gate, keep the following as a permanent release record:

- **Test gate:** full output of `./gradlew :kenvy-plugin:test :kenvy-plugin:functionalTest`
- **Docs gate:** full output of the `rg` scan and list of link checks performed
- **Publication gate:** dry-run or staged publish command and output; any
  failures or waivers and their justification
- **Consumer smoke gate:** smoke project steps, commands run, generated file
  paths confirmed, and task output from `generateKenvy` and `generateKenvyExample`

## Migration note for generated property naming

Generated Kotlin property names now default to lower camel case. For example,
`api_key` becomes `apiKey`, `retry_count` becomes `retryCount`, and `timeout_ms`
becomes `timeoutMs` in generated Kotlin source.

This does not change contract keys in `kenvy.toml`, `[overrides.*]`,
`local.properties`, `local.properties.example`, `mergeKenvy`, or
`KENVY_<NORMALIZED_NAME>` environment variables.

If a release needs temporary compatibility for existing consumers, document and
verify this opt-out:

```kotlin
kenvy {
    generatedPropertyNameStyle.set("preserve")
}
```

## Kotlin upgrade checkpoint

When upgrading Kotlin, re-check generated `expect`/`actual` source output before
publishing:

- Confirm whether Kotlin still emits the expect/actual classifier beta warning.
- Confirm whether `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` is still the
  correct generated-source suppression key.
- Remove the generated suppression if Kotlin no longer emits the warning.
- Re-run KMP functional tests that compile generated common and platform sources.

## Go or no-go criteria

Use these criteria to make the release decision repeatable.

- **Go:** test gate passes, documentation gate passes, local staged publication
  passes, public Portal validation passes for a fixed version, and a fresh
  consumer smoke project succeeds.
- **No-go:** any blocking gate fails, any public doc contradicts tested
  behavior, publication remains unconfigured, or release evidence is missing
  for any gate.

There is no release if a blocking gate is waived without documented justification.

## Non-blocking advisory checks

These checks improve quality but do not block the release by themselves.

- Confirm Kotlin version and Gradle runtime target in public docs match the
  versions used in the test suite
- Confirm AGP version mentioned for Android examples is the version exercised
  in functional tests
- Review the go/no-go decision with at least one other person familiar with the
  tested behavior before publishing

## Next steps

Local staged validation is complete. Before public release:

1. Set `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` (do not commit these).
2. Run `./gradlew :kenvy-plugin:publishPlugins` (omit `--validate-only` for the
   real publish).
3. Re-run the external consumer smoke project using the Portal-published
   coordinate to confirm end-to-end resolution.
