---
title: Skill picker — which bundled skill for which mood
---

# Skill picker

21 bundled skills ship with the app. This page maps "the situation
you're in" to "the skill the agent should use" so the LLM (and you)
pick the right flow. The agent itself reads the `description` line in
each `SKILL.md`, but when two skills seem to fit the same utterance,
this table breaks the tie.

## By time of day

| Moment | Skill | Why this and not the other |
|---|---|---|
| Waking up (soft) | `wake-up-gently` | Ramp over 10 min — not a single chime |
| Waking up (quick) | (use `AlarmMatcher`) | One chime at the target time |
| Morning routine (post-alarm) | `morning-routine` | Briefing + lights + music |
| Throughout the day | `voice-assistant` | Conversational guardrails |
| Pre-dinner / cooking | `cooking-session` | Multi-timer orchestration |
| Cooking with recipe help | `cooking-assistant` | Recipe walkthrough + converter |
| Family dinner ready | `dinner-call` | Broadcasts to every speaker |
| Evening wind-down | `bedtime-routine` | Pause media, dim lights |
| Kids to bed | `bedtime-for-kids` | 15-min broadcast warning |
| Reading in bed | `reading-time` | Very quiet, no broadcasts |
| Meditation | `meditation` | Strict silence, no announcements |
| Final goodnight | (use `goodnight` composite tool) | Single lights-off + timer cancel |
| Quiet all night | `quiet-hours` | Stateful — persists until morning |

## By activity

| Activity | Skill | Why |
|---|---|---|
| Deep work | `focus-mode` | 20 % volume, lights 70 %, clear notifications |
| Homework / Pomodoro | `study-mode` | Pomodoro timer, Digital Wellbeing nudge |
| Workout / exercise | `workout` | High energy — lights 100 %, music 70 %, intervals |
| Party / gathering | `party-mode` | Lights full + colour cycle, music, broadcast |
| Movie / TV | `movie-night` | Dim to 15 %, volume 30, clear notifications |
| Reading | `reading-time` | Library-quiet |
| Meditation | `meditation` | Silence and a bell at end |

## By audience

| Audience | Skill | Why |
|---|---|---|
| Alone | whichever the activity dictates | — |
| With visitors | `guest-mode` | Silences memory/contacts/photos/etc. |
| With kids | `bedtime-for-kids`, `wake-up-gently` (kid room), `dinner-call` | Whole-house signalling |
| Baby asleep | `quiet-hours` | No broadcasts anywhere |

## By state you want the house in

| Goal | Skill |
|---|---|
| Everything off, safe, leaving | `travel-mode` (long trip) or `leave_home` composite (short trip) |
| Everyone here, comfortable, relaxed | `arrive_home` composite, then pick by activity |
| Absolute minimum noise | `quiet-hours` or `meditation` |
| Absolute maximum vibe | `party-mode` |
| Informational briefing only | `news-briefing` |
| Smart-home control without extras | `home-control` |
| Todo / memory management | `task-manager` |

## Defaults the agent uses when the user is ambiguous

- Utterance = "let's relax" → `reading-time` (assume quiet > active)
- Utterance = "it's bedtime" alone → `bedtime-routine` (not `goodnight` — routine is gentler)
- Utterance = "bedtime for the kids" → `bedtime-for-kids` (multi-room signalling)
- Utterance = "quiet down" → `quiet-hours` if late; drop volume only if daytime
- Utterance = "party" → `party-mode` only if at least one smart-home device is registered; otherwise fall back to `set_volume 80` + acknowledge

## Which skill to author next

When adding a new bundled skill, check:

1. Does an existing skill already cover this? (Use this page to pin
   which.)
2. Is the new flow **behavioural** (rules the agent follows) or
   **compositional** (chaining multiple tools)? Bundled skills are
   best at behavioural — compositional is usually a composite tool.
3. Does it need multi-room? If yes, add it to the "By audience →
   with kids / multi-room" row; if no, don't reference
   `broadcast_*` tools.

Full list (with each skill's tools + trigger phrasing) lives in
[skills.md](skills.md).
