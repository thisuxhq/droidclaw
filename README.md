# droidclaw

ai agent that controls your android phone. give it a goal in plain english — it figures out what to tap, type, and swipe.

reads the screen (accessibility tree + optional screenshot), asks an llm what to do, executes via adb, repeats.

```
$ bun run src/kernel.ts
enter your goal: open youtube and search for "lofi hip hop"

--- step 1/30 ---
think: i'm on the home screen. launching youtube.
action: launch (842ms)

--- step 2/30 ---
think: youtube is open. tapping search icon.
action: tap (623ms)

--- step 3/30 ---
think: search field focused.
action: type "lofi hip hop" (501ms)

--- step 4/30 ---
action: enter (389ms)

--- step 5/30 ---
think: search results showing. done.
action: done (412ms)
```

## setup

you need **bun**, **adb**, and an api key for any llm provider.

```bash
bun install
cp .env.example .env
```

edit `.env` — fastest way to start is with groq (free tier):

```bash
LLM_PROVIDER=groq
GROQ_API_KEY=gsk_your_key_here
```

connect your phone (usb debugging on):

```bash
adb devices   # should show your device
bun run src/kernel.ts
```

## workflows

chain goals across apps:

```bash
bun run src/kernel.ts --workflow examples/weather-to-whatsapp.json
```

each workflow is a simple json file:

```json
{
  "name": "slack standup",
  "steps": [
    {
      "app": "com.Slack",
      "goal": "open #standup channel, type the message and send it",
      "formData": { "Message": "yesterday: api integration\ntoday: tests\nblockers: none" }
    }
  ]
}
```

35 ready-to-use workflows in `examples/` — messaging, social media, productivity, research, lifestyle.

## deterministic flows

for repeatable tasks that don't need ai, use yaml flows:

```bash
bun run src/kernel.ts --flow examples/flows/send-whatsapp.yaml
```

no llm calls, just step-by-step adb commands.

## providers

| provider | cost | vision | notes |
|---|---|---|---|
| groq | free tier | no | fastest to start |
| openrouter | per token | yes | 200+ models |
| openai | per token | yes | gpt-4o |
| bedrock | per token | yes | claude on aws |

## config

all in `.env`:

| key | default | what |
|---|---|---|
| `MAX_STEPS` | 30 | steps before giving up |
| `STEP_DELAY` | 2 | seconds between actions |
| `STUCK_THRESHOLD` | 3 | steps before stuck recovery |
| `VISION_MODE` | fallback | `off` / `fallback` / `always` |
| `MAX_ELEMENTS` | 40 | ui elements sent to llm |

## how it works

each step: dump accessibility tree → filter elements → send to llm → execute action → repeat.

the llm thinks before acting — returns `{ think, plan, action }`. if the screen doesn't change for 3 steps, stuck recovery kicks in. when the accessibility tree is empty (webviews, flutter), it falls back to screenshots.

## source

```
src/
  kernel.ts          main loop
  actions.ts         22 actions + adb retry
  skills.ts          6 multi-step skills
  workflow.ts        workflow orchestration
  flow.ts            yaml flow runner
  llm-providers.ts   4 providers + system prompt
  sanitizer.ts       accessibility xml parser
  config.ts          env config
  constants.ts       keycodes, coordinates
  logger.ts          session logging
```

## troubleshooting

**"adb: command not found"** — install adb or set `ADB_PATH` in `.env`

**"no devices found"** — check usb debugging is on, tap "allow" on the phone

**agent repeating** — stuck detection handles this. if it persists, use a better model

## license

mit
