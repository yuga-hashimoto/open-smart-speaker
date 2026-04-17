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

| Skill | Purpose | Sample triggers | Tools used |
|---|---|---|---|
| `bedtime-routine` | Wind-down — pause media, dim lights, optional alarm-style timer, evening summary | "bedtime", "wind down" | execute_command, set_volume, set_timer |
| `bedtime-for-kids` | 15-min bedtime warning: broadcast_announcement + broadcast_timer across every speaker, then local goodnight | "kids bedtime", "子どもの寝る時間" | broadcast_announcement, broadcast_timer, broadcast_tts |
| `breathing-exercise` | Short 4-7-8 breathing guide — four cycles of inhale-hold-exhale voice cues over roughly one minute, without disturbing quiet-hours | "breathing exercise", "box breathing", "4 7 8 breathing", "深呼吸", "呼吸法" | set_timer, cancel_all_timers, set_volume |
| `cooking-assistant` | Recipe walkthroughs with set_timer + unit_converter | "cook", "recipe", "kitchen" | set_timer, convert_units, get_knowledge |
| `cooking-session` | Multi-timer cooking helper — parallel timers with labels, optional whole-house "dinner is ready" broadcast at the end | "cooking mode", "I'm starting dinner", "料理モード", "晩ご飯の準備" | set_timer, cancel_timer, cancel_all_timers, list_timers, broadcast_tts, broadcast_announcement, execute_command, launch_app, web_search, convert_units |
| `dinner-call` | "Call everyone to dinner" — broadcast_tts + broadcast_announcement with 5-min TTL banner | "call everyone to dinner", "夕飯できた" | broadcast_tts, broadcast_announcement |
| `dog-walk` | Kick off a dog-walk session — optional weather cue at the door, a 30-minute backstop timer, and a louder welcome-home prompt when the timer fires | "dog walk", "walk the dog", "taking the dog out", "犬の散歩", "散歩行ってくる" | set_timer, cancel_all_timers, get_weather, set_volume, recall |
| `eye-break` | 20-20-20 eye-strain reminder for screen-heavy work — short verbal cue every 20 minutes to look 20 feet away for 20 seconds | "eye break", "20-20-20", "rest my eyes", "目を休めて", "画面疲れ" | set_timer, cancel_all_timers |
| `focus-mode` | Deep-work session: lights→70, volume→20, clear notifications, optional Pomodoro timer | "focus mode", "deep work" | execute_command, set_volume, clear_notifications, set_timer |
| `gratitude-journal` | Nightly 3-item gratitude prompt — ask for three good things from today, store each via `remember` under the `gratitude` namespace, close with a gentle good night | "gratitude journal", "three good things", "感謝日記", "今日良かったこと" | remember, recall |
| `guest-mode` | Relax the agent for visitors — silence wake-word + personal data tools, surface a welcome banner, restrict destructive actions | "guest mode", "guests are coming", "we have visitors", "ゲストモード", "お客さん来るから" | execute_command, set_volume, broadcast_announcement |
| `home-control` | General smart-home control verbs and disambiguation | "turn on the lights", "set thermostat" | execute_command |
| `hydration-reminder` | Low-friction hourly nudge to drink water — warm, brief, and easy to dismiss | "hydration reminder", "remind me to drink water", "水分補給のリマインド", "1時間ごとに水を思い出させて" | set_timer, cancel_all_timers, remember, recall, execute_command |
| `meditation` | Quiet meditation or breath-work session — very dim warm light, no media, silenced notifications, optional bell-ended timer | "meditation", "let's meditate", "瞑想モード", "呼吸のセッション" | execute_command, clear_notifications, set_volume, set_timer, cancel_all_timers, retrieve_document |
| `morning-routine` | Wake-up flow — morning_briefing + lights + ambient music | "good morning", "start my day" | morning_briefing, execute_command, media_play |
| `movie-night` | Movie ambience: lights→15, volume→30, clear notifications, optional service launch | "movie night", "watch a movie" | execute_command, set_volume, clear_notifications, launch_app |
| `news-briefing` | Reading the news without dumping every headline | "news briefing", "what's happening" | get_news |
| `party-mode` | Social gathering: lights→100 with colour cycle, media_play, volume→80, optional broadcast_tts to all speakers | "party mode", "party time" | execute_command, set_volume, media_play, broadcast_tts |
| `pomodoro` | Classic 25/5 pomodoro cycle — strict 25-minute work blocks, 5-minute rests, and a longer 15-minute rest every fourth cycle | "pomodoro", "start pomodoro", "25 minute timer", "ポモドーロ" | set_timer, cancel_all_timers, set_volume |
| `power-nap` | 20-minute restorative nap — dim lights to 20%, set a short timer, whisper a goodnight, then a gentle wake cue | "power nap", "quick nap", "20 minute nap", "パワーナップ", "昼寝" | execute_command, set_timer, cancel_all_timers |
| `quick-note` | Rapid voice-note capture — acknowledge with "Listening.", store the next utterance via `remember` under `notes`, confirm "Saved." | "quick note", "take a note", "note to self", "メモ", "記録して" | remember, recall, search_memory |
| `quiet-hours` | Night-time noise cap — low TTS volume, skip broadcast fan-outs, pause alerts until morning | "quiet hours", "sleep-friendly mode", "静かにして" | set_volume, execute_command |
| `rainy-day` | Rainy-day check-in — inspect the forecast, and if rain is likely today, suggest a cozy indoor plan (playlist, close blinds/windows reminder) without taking destructive action | "rainy day", "is it raining", "will it rain today", "should I bring an umbrella", "雨の日", "今日雨降る" | get_forecast, get_weather, recall, play_media_by_source, set_volume, execute_command |
| `reading-time` | Bedtime story or solo reading session — warm light, silence everything else, optional gentle timer | "reading time", "read a bedtime story", "読書モード", "寝る前に本読む" | execute_command, set_volume, clear_notifications, set_timer, retrieve_document |
| `stretch-break` | Short movement break after long sitting — 5-minute guided stretch routine with gentle voice cues | "stretch break", "movement break", "need a break", "ストレッチ休憩", "体を伸ばしたい" | set_timer, cancel_all_timers, set_volume, execute_command |
| `study-mode` | Schoolwork focus: lights→60%, volume→15, media pause, clear notifications, Pomodoro timer; explicitly avoids force-killing apps (needs root) and nudges the user to Digital Wellbeing instead | "study mode", "homework", "study time" | execute_command, set_volume, clear_notifications, set_timer |
| `task-manager` | "Remind me to X" → memory store + recall + forget | "remind me to", "add a task" | remember, recall, forget |
| `travel-mode` | Leaving home for a trip — turn off every light, pause media, cancel timers, optional away-style briefing, broadcast a goodbye to other speakers | "travel mode", "I'm going on a trip", "starting a vacation", "旅行モード", "出かける" | get_forecast, execute_command, cancel_all_timers, broadcast_tts, arrive_home, list_notifications, get_weather |
| `voice-assistant` | Conversational guardrails — keep replies under 30s of speech | (always-on) | (none — guardrail only) |
| `wake-up-gently` | Progressive sunrise-style wake — lights ramp up over 10 minutes while volume stays quiet, then gentle music + briefing | "wake me up at 7 gently", "sunrise alarm", "ゆっくり起こして", "朝7時にゆっくり起こして" | execute_command, set_timer, set_volume, morning_briefing, cancel_all_timers |
| `workout` | Workout mode: lights→100, volume→70, interval timers, cooldown recovery | "workout", "exercise time" | execute_command, set_volume, set_timer |

## Install flow

```
POST install_skill_from_url({ url: "https://example.com/my-skill.zip" })
  → SkillInstaller downloads to filesDir/skills/my-skill/
  → SkillParser validates frontmatter
  → SkillRegistry.register
  → visible in Settings → Skills
```
