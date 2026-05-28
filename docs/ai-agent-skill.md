# Kenvy AI agent skill

Kenvy includes a repository-local Agent Skill for AI coding agents that install,
configure, troubleshoot, or migrate Kenvy in Kotlin Multiplatform and Compose
Multiplatform projects.

## Skill location

The public skill package lives at:

```text
agent-skills/kenvy/
```

Use that folder as the source of truth. If an agent client scans a different
local directory, copy or symlink `agent-skills/kenvy/` into that client's
skills directory rather than maintaining a second divergent copy.

## Optional distribution

The repository copy is the canonical distribution source today. Compatible
agent clients can install it by copying or symlinking `agent-skills/kenvy/` into
their local skills directory.

skills.sh discovery is optional. Do not add a skills.sh badge or claim
`npx skills add ...` installation until the repository is actually discoverable
there. Kenvy runtime behavior must not depend on skills.sh or any agent-skill
registry.

## What the skill covers

The skill is procedural agent guidance. It points agents back to the public docs
and source before product claims, then routes them to focused references for:

- Installing Kenvy in KMP and Compose Multiplatform projects
- Creating and updating `kenvy.toml`
- Adding, updating, renaming, and removing properties
- Handling `local.properties`, scoped `KENVY_` environment variables, CI, and
  secrets safely
- Migrating from legacy generated configuration and ad hoc configuration
  patterns

The human-facing product docs remain the source of truth for runtime behavior,
release state, examples, and known limitations.

## Validation

Maintainers can validate the skill package with:

```sh
skills-ref validate ./agent-skills/kenvy
```

If `skills-ref` isn't installed, manually verify that:

- `agent-skills/kenvy/SKILL.md` exists.
- YAML frontmatter parses.
- `name: kenvy` matches the directory.
- The description is trigger-focused and under 1024 characters.
- Reference links resolve.
- No real secrets appear in examples.

Run this scan before publishing or advertising the skill:

```sh
rg -n --glob '!docs/superpowers/**' "secret-value|android-secret|ios-secret|api[-_ ]?key\s*[:=]|KENVY_[A-Z0-9_]+\s*[:=]" agent-skills docs README.md
```

Review any matches as potential examples or placeholders. Don't publish the
skill if a match contains a real secret value.

## Activation checks

Use these prompts to check that compatible clients route Kenvy tasks to the
skill:

- Positive: `install Kenvy in a Kotlin Multiplatform app`
- Positive: `add an Android debug and iOS release API key with Kenvy`
- Positive: `migrate this BuildKonfig block to Kenvy`
- Negative: `configure Gradle build cache for a generic project`
- Negative: `create an Android runtime settings screen`

The negative prompts can mention adjacent Gradle or Android concepts, but they
must not activate the Kenvy skill unless the user explicitly asks for Kenvy.
