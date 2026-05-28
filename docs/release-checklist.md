# Kenvy release checklist

This checklist defines the evidence maintainers must record for each public
Kenvy release. It is intentionally strict because release metadata can drift
between GitHub Releases, the Gradle Plugin Portal, docs, and agent-facing
install guidance.

## Current verified release

- GitHub release: [`v0.1.2`](https://github.com/adriandleon/kenvy/releases/tag/v0.1.2)
- Release name: `0.1.2`
- GitHub published time: `2026-05-27T13:14:41Z`
- Gradle Plugin Portal version: `0.1.2`
- Portal created date: `27 May 2026`
- Portal plugin ID: `io.github.adriandleon.kenvy`
- Portal install snippet:

```kotlin
plugins {
    id("io.github.adriandleon.kenvy") version "0.1.2"
}
```

- Latest successful Release workflow run:
  [`26513369161`](https://github.com/adriandleon/kenvy/actions/runs/26513369161)
- Workflow release tag: `v0.1.2`
- Workflow result: `success`
- Workflow started: `2026-05-27T13:14:44Z`
- Workflow completed: `2026-05-27T13:21:32Z`

Normal consumers should install the latest verified Gradle Plugin Portal
version. If GitHub Releases and the Portal temporarily differ, document both
versions here and keep public install docs pointed at the version that resolves
from the Portal.

## Test gate

Run these commands from the repository root and keep the command output as
release evidence.

```sh
./gradlew :kenvy-plugin:test :kenvy-plugin:functionalTest
```

This single command covers unit tests for generation, example output, merge
logic, hierarchical resolution, sensitive-value masking, naming validation, and
external provider timeout handling. It also covers functional tests for
`generateKenvy`, `generateKenvyExample`, `mergeKenvy`, Android bridge, iOS
bridge, Git ignore diagnostics, Gradle isolation, Configuration Cache
compatibility, and the performance baseline.

**Gate passes when:** the build succeeds with no failing unit or functional
tests and the output is preserved.

## Documentation gate

Verify public docs before every release so the repository does not ship
contradictory install or runtime behavior.

Run these scans from the repository root and record the output:

```sh
rg -n "not yet on|not yet resolve|local staged|mavenLocal|publishToMavenLocal|Portal release remains blocked|0\\.1\\.1-SNAPSHOT|0\\.1\\.0" README.md docs agent-skills kenvy-plugin/build.gradle.kts
rg -n "KENVY_PUBLIC_VERSION" .github/workflows/ci.yml
rg -n "publication|Plugin Portal|Maven Central|staged|smoke|provider|gitignore|cacheGeneratedOutput|generateKenvy|generateKenvyExample|mergeKenvy" README.md docs
```

The second scan surfaces the version pinned in CI so you can confirm it matches the Portal version before merging.

Review each match to confirm it is current, intentional, and consistent with
the tested behavior. Then verify:

- `README.md` explains the supported Portal install path and current version.
- `docs/getting-started.md` matches the current plugins DSL and task names.
- `docs/examples.md` uses the documented public plugin version for normal
  consumers.
- `docs/known-limitations.md` documents current MVP boundaries without claiming
  the Portal plugin is unavailable.
- `agent-skills/kenvy` directs normal consumers to the Portal version and keeps
  local Maven staging only as a maintainer fallback.
- All relative links among `README.md`, `docs/*.md`, and
  `agent-skills/kenvy/**/*.md` resolve to real files.

**Gate passes when:** the scans show no stale release-state text and every
relative link resolves.

## Publication gate

`kenvy-plugin/build.gradle.kts` applies `com.gradle.plugin-publish` 2.1.1,
which automatically applies `maven-publish`. Publication metadata is configured
as follows:

- Group: `io.github.adriandleon.kenvy`
- Artifact: `kenvy-plugin`
- Development version default: `0.1.3-SNAPSHOT`
- Release version override: `-PkenvyVersion=<version>`
- Plugin ID: `io.github.adriandleon.kenvy`
- Repository: `https://github.com/adriandleon/kenvy`
- Gradle feature compatibility: Configuration Cache supported

Run Portal validation before public publication:

```sh
./gradlew :kenvy-plugin:publishPlugins --validate-only -PkenvyVersion=<version>
```

The public repository includes a GitHub Actions release workflow that publishes
the Gradle plugin when a GitHub Release is published. The release tag must start
with `v`. The workflow strips the leading `v` and passes the remaining version
to Gradle with `-PkenvyVersion=<version>`.

Before creating a GitHub Release, configure the `release` GitHub environment
with these secrets:

- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`

Signing is optional and activated only when in-memory PGP credentials are
present through `signing.key` / `signing.password` Gradle properties or the
`SIGNING_KEY` / `SIGNING_PASSWORD` environment variables.

Maintainers may still use local Maven staging for development fallback:

```sh
./gradlew :kenvy-plugin:publishToMavenLocal -PkenvyVersion=<version>
```

Do not present local staging as the normal consumer install path in public docs.

## Public consumer smoke gate

Run a fresh consumer smoke against the documented public Portal version:

```sh
KENVY_PUBLIC_VERSION=0.1.2 ./scripts/public-consumer-smoke.sh
```

The script creates a project outside the repository checkout, configures only
public repositories, applies:

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    id("io.github.adriandleon.kenvy") version "0.1.2"
}
```

and runs:

```sh
./gradlew generateKenvy generateKenvyExample compileKotlinJvm
```

The smoke project must not use `mavenLocal()`, `includeBuild`,
`withPluginClasspath()`, or locally published artifacts. It verifies:

- `build/generated/kenvy/commonMain/kotlin/com/example/smoke/Kenvy.kt` exists.
- `local.properties.example` exists.
- `compileKotlinJvm` succeeds against the generated API.

Record the exact `KENVY_PUBLIC_VERSION`, temp project path, Gradle command, and
success output for release evidence.

## Release evidence to preserve

For each release, keep these records:

- **GitHub release:** tag, name, URL, and publish time.
- **Gradle Plugin Portal:** latest version, created date, plugin ID, and install
  snippet.
- **Release workflow:** run URL, run ID, tag, status, start time, and completion
  time.
- **Test gate:** full output of
  `./gradlew :kenvy-plugin:test :kenvy-plugin:functionalTest`.
- **Docs gate:** stale-text scan output and link-check result.
- **Publication gate:** Portal validation output and publish workflow result.
- **Consumer smoke gate:** script command, generated paths confirmed, and
  Gradle output.

## Migration note for generated property naming

Generated Kotlin property names default to lower camel case. For example,
`api_key` becomes `apiKey`, `retry_count` becomes `retryCount`, and
`timeout_ms` becomes `timeoutMs` in generated Kotlin source.

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
- Re-run KMP functional tests that compile generated common and platform
  sources.

## Toolchain compatibility checkpoint

Before publishing, rerun the exact compatibility matrix scenarios documented in
`docs/toolchain-compatibility.md` and record the Kotlin, Gradle, AGP, Java
runtime, Java toolchain, Android plugin shape, iOS target shape, command, and CI
run evidence.

Required local or CI entrypoints:

```sh
./scripts/compatibility-matrix.sh android-baseline
./scripts/compatibility-matrix.sh android-modern
./scripts/compatibility-matrix.sh ios-modern
./scripts/compatibility-matrix.sh jvm-modern
```

If a toolchain cannot be validated, update the matrix with the exact failing
constraint and the recommended fallback before release.

## Go or no-go criteria

- **Go:** test gate passes, documentation gate passes, Portal validation passes,
  the Release workflow publishes successfully, the Plugin Portal shows the
  expected version, and the public consumer smoke succeeds.
- **No-go:** any blocking gate fails, any public doc contradicts tested
  behavior, GitHub and Portal version drift is undocumented, or release evidence
  is missing for any gate.

There is no release if a blocking gate is waived without documented
justification.
