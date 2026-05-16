# Contributing to Kenvy

Thanks for taking the time to improve Kenvy. This guide explains how to set up
the project, run the verification gates, and send a focused pull request.

## Before you begin

Kenvy is a Gradle plugin for build-time Kotlin Multiplatform configuration.
Before changing behavior, read the user-facing docs so your change stays
consistent with the public contract:

- [Getting started](docs/getting-started.md)
- [Examples](docs/examples.md)
- [Known limitations](docs/known-limitations.md)
- [Release checklist](docs/release-checklist.md)

You need Java 17 and a recent Gradle wrapper-compatible environment. Use the
checked-in `./gradlew` wrapper instead of a system Gradle installation.

## Development workflow

Create a branch from `main` and keep each pull request scoped to one behavior
change, fix, or documentation update.

1. Fork and clone the repository.
2. Create a branch with a descriptive name.
3. Make your change with tests or documentation updates.
4. Run the relevant verification commands.
5. Open a pull request and describe the behavior change and verification you
   ran.

## Verification

Run the full test gate before opening a pull request that changes plugin code:

```sh
./gradlew :kenvy-plugin:test :kenvy-plugin:functionalTest
```

For documentation-only changes, run this scan and review the matches for
accuracy:

```sh
rg -n "Plugin Portal|generateKenvy|generateKenvyExample|mergeKenvy|local.properties|KENVY_" README.md docs
```

If you change publication metadata or generated artifacts, also run local staged
publication:

```sh
./gradlew :kenvy-plugin:publishToMavenLocal
```

## Pull request expectations

Use clear examples and tests for behavior that users can observe. A pull request
is easier to review when it includes:

- A short description of the problem and solution.
- Tests for plugin behavior, especially source generation and diagnostics.
- Documentation updates when commands, generated output, or limitations change.
- Notes about compatibility risks for existing consumers.

Do not commit local secrets, Gradle credentials, generated build output, local
planning notes, or assistant-specific workspace files.

## Reporting security issues

Do not open a public issue for a vulnerability or accidental secret disclosure.
Follow the private reporting instructions in [SECURITY.md](SECURITY.md).
