# Security policy

Kenvy handles build-time configuration and secret-adjacent workflows. Treat
secret disclosure, unsafe generated output, and credential handling bugs as
security-sensitive issues.

## Supported versions

Security fixes target the latest released version. Before the first stable
release, fixes target `main`.

## Report a vulnerability

Do not open a public GitHub issue for a vulnerability. Use GitHub's private
vulnerability reporting for this repository with:

- A description of the issue.
- Steps to reproduce it.
- The affected Kenvy version or commit.
- Any known workaround.

You can expect an initial response within 7 days. If the report is valid, the
fix will be prepared privately when practical and disclosed after a patched
release is available.

## Secret handling guidance

Kenvy is designed to avoid printing sensitive values in diagnostics, but users
are still responsible for keeping local secret files out of version control.
Before publishing examples or reproduction projects, remove real values from
`local.properties`, `.env` files, CI logs, generated source, and build output.
