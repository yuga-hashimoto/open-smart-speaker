# Security Policy

## Supported Versions

We currently support the latest `main` branch. Security fixes land there first
and are backported to the most recent tagged release when feasible.

## Reporting a Vulnerability

If you believe you've found a security issue — particularly in the areas of:

- Secret handling (API keys, tokens)
- Permission misuse (notification listener, accessibility service, SMS, camera)
- Arbitrary code execution via SKILL.md or tool payloads
- Network calls exposing user data
- Local storage that should be encrypted but isn't

Please do NOT open a public GitHub issue.

Instead, report privately via GitHub's
[Security Advisory flow](https://github.com/yuga-hashimoto/open-dash/security/advisories/new)
or contact the maintainer directly.

We'll acknowledge within 72 hours and aim to ship a fix within 14 days for
critical issues (definition: RCE, data exfiltration, arbitrary permission
escalation).

## Threat Model

OpenDash is designed to be **on-device first**. The threat model assumes:

- **Untrusted input** via voice / STT — must not produce unsafe tool calls
- **Tool boundaries** — each tool validates its own arguments; the LLM is
  never trusted to pre-validate
- **Skills are descriptive, not executable** — SKILL.md is instructions to
  the LLM only, it cannot run arbitrary code
- **Network calls** are minimized; weather / search / news use public APIs
  with no authentication
- **Secrets** stored in `SecurePreferences` (EncryptedSharedPreferences),
  never in logs

## Known Limitations

- Android Accessibility Service exposes screen content to the agent. Users
  must grant this explicitly, but compromise of the agent would compromise
  everything visible on screen.
- Notification listener similarly exposes notification content.
- SMS send tool can send on user's behalf; always confirm with the user
  before calling.
