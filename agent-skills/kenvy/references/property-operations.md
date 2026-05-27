# Property operations

Use this reference when adding, updating, renaming, or removing Kenvy
properties. Keep one logical Kenvy property API unless the project truly needs
separate concepts.

## Before changing a property

Inspect the current contract and call sites:

```sh
files=$(git ls-files '*.kt' '*.kts' '*.toml' '*.md' '*.yml' '*.yaml')
[ -n "$files" ] && printf '%s\n' "$files" | xargs rg -n "apiKey|api_key|Kenvy\\.|AppConfig\\."

ci_files=$(git ls-files README.md docs '.github/**' '.gitlab/**' bitrise.yml Jenkinsfile)
[ -n "$ci_files" ] && printf '%s\n' "$ci_files" | xargs rg -n "KENVY_|local.properties|overrides\\."
```

Adapt names and file globs to the project. Search tracked source, docs, and CI
configuration first; don't recursively scan the whole working tree because
untracked `local.properties` can contain real values. Don't print secret values
from local files or CI configuration.

Record:

- Current contract key and generated Kotlin name.
- `packageName`, `interfaceName`, and `generatedPropertyNameStyle`.
- Platform and variant override usage.
- Local and CI variable names the user must set.
- Compile/test tasks that cover affected call sites.

## Add a property

1. Add `[properties.<name>]` with a supported type.
2. Use a public default only when it is safe to commit.
3. Mark secret-like values with `sensitive = true`.
4. Add `[overrides.*]` only when public platform or variant defaults differ.
5. For developer-local values, document keys such as `<name>`,
   `<name>.android`, and `<name>.android.debug`.
6. For CI, document `KENVY_` names without values.
7. Run `generateKenvy` and compile affected source sets.
8. Update docs or examples only when the property is part of public setup.

## Update a property

1. Determine whether the change is type, default, metadata, override, generated
   name, or call-site behavior.
2. Update `kenvy.toml` first, then local/CI docs or call sites.
3. Preserve the contract key if users already have local or CI values under
   that key.
4. Run generation and a compile/test task that reaches affected source sets.

If changing a type, check every override and local/CI value source for
conversion compatibility. Don't read or echo real local secret values.

## Rename a property

Renames can break local files, CI env vars, generated accessors, and docs. Use
an incremental plan:

1. Search call sites and all documented env/local names.
2. Add the new property while keeping the old property if needed for a staged
   rollout.
3. Update call sites from the old generated accessor to the new accessor.
4. Tell the user the old local keys and `KENVY_` variables that must be
   renamed manually.
5. Run generation and compile/tests.
6. Remove the old property only after parity is proven.

If only the generated Kotlin accessor needs compatibility, prefer
`generatedPropertyNameStyle.set("preserve")` temporarily instead of renaming
the contract.

## Remove a property

1. Confirm no generated accessor call sites remain.
2. Remove the property and related public overrides from `kenvy.toml`.
3. Remove docs references and CI variable mentions that are no longer valid.
4. Tell the user which local keys or CI secrets can be deleted manually.
5. Run generation and compile/tests.

Don't edit untracked `local.properties` unless the user explicitly asks and
confirms that no real secrets will be exposed.

## Output template

Use this shape in your final report:

```text
Property change:
- Contract: <added|updated|renamed|removed> <property>
- Generated API: <object>.<accessor>
- Local keys to set manually: <names only>
- CI env vars to set manually: <names only>
- Call sites changed: <files>
- Validation: <commands and results>
```
