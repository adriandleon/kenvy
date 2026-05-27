# Agent output templates

Use these templates to keep Kenvy work reviewable and safe. Report secret names
only, never values.

## Before-editing inspection

```text
Before editing:
- Owning Gradle module:
- Gradle files inspected:
- KMP targets:
- Android variant strategy:
- iOS variant strategy:
- Existing config sources:
- Existing secret files:
- Generated package/object:
- Generated property naming style:
- Validation commands planned:
```

## Install plan

```text
Install plan:
- Apply plugin in:
- Contract path:
- Generated package/object:
- Release path: <Plugin Portal|Maven Local staged evaluation>
- Local values needed:
- CI env vars needed:
- Validation:
```

## Property change

```text
Property change:
- Contract: <added|updated|renamed|removed> <property>
- Generated API: <object>.<accessor>
- Local keys to set manually:
- CI env vars to set manually:
- Call sites changed:
- Validation:
```

## Migration plan

```text
Migration plan:
- Source system:
- Existing generated API:
- Kenvy contract mapping:
- Non-equivalent behavior:
- Staged rollout:
- Old system removal criteria:
- Validation:
```

## Final report

```text
Kenvy work complete:
- Files changed:
- Contract/API changes:
- Manual local secret steps:
- Manual CI secret steps:
- Validation run:
- Release-state or unsupported-behavior caveats:
```
