# Skills

Skills are markdown files with YAML frontmatter that describe a reusable
capability the agent can pick up. Stolen directly from OpenClaw.

## Anatomy

```md
---
name: cooking-assistant
version: 1
description: Helps with recipes, timing, and substitutions.
triggers:
  - cook
  - recipe
  - kitchen
tools:
  - set_timer
  - convert_units
  - get_knowledge
---

# Cooking assistant

You help the user with cooking tasks. When they name a dish, list
ingredients + steps + set timers as needed. Always ask about dietary
restrictions before suggesting substitutions.
```

## Discovery

1. **Bundled** — `app/src/main/assets/skills/*/SKILL.md`
2. **Installed** — downloaded via `install_skill_from_url` → `filesDir/skills/`
3. Both are loaded by `SkillRegistry` and injected into the system prompt as
   `<available_skills>...</available_skills>` XML.

## Authoring rules

- `triggers` are word-boundary substrings; keep them short and unambiguous.
- `tools` must match actual tool names from [tools.md](tools.md).
- Body is the system-prompt append that activates when a trigger fires.
- Keep the body under ~300 tokens so the system prompt stays lean.

## Install flow

```
POST install_skill_from_url({ url: "https://example.com/my-skill.zip" })
  → SkillInstaller downloads to filesDir/skills/my-skill/
  → SkillParser validates frontmatter
  → SkillRegistry.register
  → visible in Settings → Skills
```
