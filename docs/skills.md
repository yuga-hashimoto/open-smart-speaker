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

## Bundled skills

These ship in `app/src/main/assets/skills/` and are auto-registered on
launch. Each is editable in `Settings → Skills` (toggle on/off).
User-installed skills can override or supplement these.

| Skill | Purpose |
|---|---|
| `bedtime-routine` | Wind-down — pause media, dim lights, optional alarm-style timer, evening summary |
| `cooking-assistant` | Recipe walkthroughs with set_timer + unit_converter |
| `focus-mode` | Deep-work session: lights→70, volume→20, clear notifications, optional Pomodoro timer |
| `home-control` | General smart-home control verbs and disambiguation |
| `morning-routine` | Wake-up flow — morning_briefing + lights + ambient music |
| `movie-night` | Movie ambience: lights→15, volume→30, clear notifications, optional service launch |
| `news-briefing` | Reading the news without dumping every headline |
| `party-mode` | Social gathering: lights→100 with colour cycle, media_play, volume→80, optional broadcast_tts to all speakers |
| `dinner-call` | "Call everyone to dinner" — broadcast_tts + broadcast_announcement with 5-min TTL banner |
| `bedtime-for-kids` | 15-min bedtime warning: broadcast_announcement + broadcast_timer across every speaker, then local goodnight |
| `study-mode` | Schoolwork focus: lights→60%, volume→15, media pause, clear notifications, Pomodoro timer; explicitly avoids force-killing apps (needs root) and nudges the user to Digital Wellbeing instead |
| `task-manager` | "Remind me to X" → memory store + recall + forget |
| `voice-assistant` | Conversational guardrails — keep replies under 30s of speech |
| `workout` | Workout mode: lights→100, volume→70, interval timers, cooldown recovery |

## Install flow

```
POST install_skill_from_url({ url: "https://example.com/my-skill.zip" })
  → SkillInstaller downloads to filesDir/skills/my-skill/
  → SkillParser validates frontmatter
  → SkillRegistry.register
  → visible in Settings → Skills
```
