# droidclaw

> **experimental.** i wanted to build something to turn my old android devices into ai agents. after a few hours reverse engineering accessibility trees and the kernel and playing with tailscale.. it worked.

ai agent that controls your android phone. give it a goal in plain english - it figures out what to tap, type, and swipe. it reads the screen, asks an llm what to do, executes via adb, and repeats until the job is done.

one of the biggest things it can do right now is delegate incoming requests to chatgpt, gemini, or google search on the device... and give us the result back. few years back we could run this kind of automation with predefined flows. now think of this as automation with ai intelligence... it can do stuff. you don't need to worry about messy api's. just install your fav apps, write workflows or tell them on the fly. it will get it done.

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
# install adb if you don't have it
brew install android-platform-tools

bun install
cp .env.example .env
```

edit `.env` - fastest way to start is with groq (free tier):

```bash
LLM_PROVIDER=groq
GROQ_API_KEY=gsk_your_key_here
```

connect your phone (usb debugging on):

```bash
adb devices   # should show your device
bun run src/kernel.ts
```

that's the simplest way - just type a goal and let the agent figure it out. but for anything you want to run repeatedly, there are two modes: **workflows** and **flows**.

## workflows

workflows are ai-powered. you describe goals in natural language, and the llm decides how to navigate, what to tap, what to type. use these when the ui might change, when you need the agent to think, or when chaining goals across multiple apps.

```bash
bun run src/kernel.ts --workflow examples/workflows/research/weather-to-whatsapp.json
```

each workflow is a json file - just a name and a list of steps:

```json
{
  "name": "weather to whatsapp",
  "steps": [
    { "app": "com.google.android.googlequicksearchbox", "goal": "search for chennai weather today" },
    { "goal": "share the result to whatsapp contact Sanju" }
  ]
}
```

you can also pass form data into steps when you need to inject specific text:

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

### examples

35 ready-to-use workflows organised by category:

**[messaging](examples/workflows/messaging/)** - whatsapp, telegram, slack, email
- [slack-standup](examples/workflows/messaging/slack-standup.json) - post daily standup to a channel
- [whatsapp-broadcast](examples/workflows/messaging/whatsapp-broadcast.json) - send a message to multiple contacts
- [telegram-send-message](examples/workflows/messaging/telegram-send-message.json) - send a telegram message
- [email-reply](examples/workflows/messaging/email-reply.json) - draft and send an email reply
- [whatsapp-to-email](examples/workflows/messaging/whatsapp-to-email.json) - forward whatsapp messages to email
- [slack-check-messages](examples/workflows/messaging/slack-check-messages.json) - read unread slack messages
- [email-digest](examples/workflows/messaging/email-digest.json) - summarise recent emails
- [telegram-channel-digest](examples/workflows/messaging/telegram-channel-digest.json) - digest a telegram channel
- [whatsapp-reply](examples/workflows/messaging/whatsapp-reply.json) - reply to a whatsapp message
- [send-whatsapp-vi](examples/workflows/messaging/send-whatsapp-vi.json) - send whatsapp to a specific contact

**[social](examples/workflows/social/)** - instagram, youtube, cross-posting
- [social-media-post](examples/workflows/social/social-media-post.json) - post across platforms
- [social-media-engage](examples/workflows/social/social-media-engage.json) - like/comment on posts
- [instagram-post-check](examples/workflows/social/instagram-post-check.json) - check recent instagram posts
- [youtube-watch-later](examples/workflows/social/youtube-watch-later.json) - save videos to watch later

**[productivity](examples/workflows/productivity/)** - calendar, notes, github, notifications
- [morning-briefing](examples/workflows/productivity/morning-briefing.json) - read messages, calendar, weather across apps
- [github-check-prs](examples/workflows/productivity/github-check-prs.json) - check open pull requests
- [calendar-create-event](examples/workflows/productivity/calendar-create-event.json) - create a calendar event
- [notes-capture](examples/workflows/productivity/notes-capture.json) - capture a quick note
- [notification-cleanup](examples/workflows/productivity/notification-cleanup.json) - clear and triage notifications
- [screenshot-share-slack](examples/workflows/productivity/screenshot-share-slack.json) - screenshot and share to slack
- [translate-and-reply](examples/workflows/productivity/translate-and-reply.json) - translate a message and reply
- [logistics-workflow](examples/workflows/productivity/logistics-workflow.json) - multi-app logistics coordination

**[research](examples/workflows/research/)** - search, compare, monitor
- [weather-to-whatsapp](examples/workflows/research/weather-to-whatsapp.json) - get weather via google ai mode, share to whatsapp
- [multi-app-research](examples/workflows/research/multi-app-research.json) - research across multiple apps
- [price-comparison](examples/workflows/research/price-comparison.json) - compare prices across shopping apps
- [news-roundup](examples/workflows/research/news-roundup.json) - collect news from multiple sources
- [google-search-report](examples/workflows/research/google-search-report.json) - search google and save results
- [check-flight-status](examples/workflows/research/check-flight-status.json) - check flight status

**[lifestyle](examples/workflows/lifestyle/)** - food, transport, music, fitness
- [food-order](examples/workflows/lifestyle/food-order.json) - order food from a delivery app
- [uber-ride](examples/workflows/lifestyle/uber-ride.json) - book an uber ride
- [spotify-playlist](examples/workflows/lifestyle/spotify-playlist.json) - create or add to a spotify playlist
- [maps-commute](examples/workflows/lifestyle/maps-commute.json) - check commute time
- [fitness-log](examples/workflows/lifestyle/fitness-log.json) - log a workout
- [expense-tracker](examples/workflows/lifestyle/expense-tracker.json) - log an expense
- [wifi-password-share](examples/workflows/lifestyle/wifi-password-share.json) - share wifi password
- [do-not-disturb](examples/workflows/lifestyle/do-not-disturb.json) - toggle do not disturb with exceptions

## flows

for tasks where you don't need ai thinking at all - just a fixed sequence of taps and types. no llm calls, instant execution. good for things you do exactly the same way every time.

```bash
bun run src/kernel.ts --flow examples/flows/send-whatsapp.yaml
```

```yaml
appId: com.whatsapp
name: Send WhatsApp Message
---
- launchApp
- wait: 2
- tap: "Contact Name"
- wait: 1
- tap: "Message"
- type: "hello from droidclaw"
- tap: "Send"
- done: "Message sent"
```

### examples

5 flow templates in [`examples/flows/`](examples/flows/):

- [send-whatsapp](examples/flows/send-whatsapp.yaml) - send a whatsapp message
- [google-search](examples/flows/google-search.yaml) - run a google search
- [create-contact](examples/flows/create-contact.yaml) - add a new contact
- [clear-notifications](examples/flows/clear-notifications.yaml) - clear all notifications
- [toggle-wifi](examples/flows/toggle-wifi.yaml) - toggle wifi on/off

## quick comparison

| | workflows | flows |
|---|---|---|
| format | json | yaml |
| uses ai | yes | no |
| handles ui changes | yes | no |
| speed | slower (llm calls) | instant |
| best for | complex/multi-app tasks | simple repeatable tasks |

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

the llm thinks before acting - returns `{ think, plan, action }`. if the screen doesn't change for 3 steps, stuck recovery kicks in. when the accessibility tree is empty (webviews, flutter), it falls back to screenshots.

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

## remote control with tailscale

the default setup is usb - phone plugged into your laptop. but you can go further.

install [tailscale](https://tailscale.com) on both your android device and your laptop/vps. once they're on the same tailnet, connect adb over the network:

```bash
# on your phone: enable wireless debugging (developer options → wireless debugging)
# note the ip:port shown on the screen

# from your laptop/vps, anywhere in the world:
adb connect <phone-tailscale-ip>:<port>
adb devices   # should show your phone

bun run src/kernel.ts
```

now your phone is a remote ai agent. leave it on a desk, plugged into power, and control it from your vps, your laptop at a cafe, or a cron job running workflows at 8am every morning. the phone doesn't need to be on the same wifi or even in the same country.

this is what makes old android devices useful again - they become always-on agents that can do things on apps that don't have api's.

## troubleshooting

**"adb: command not found"** - install adb or set `ADB_PATH` in `.env`

**"no devices found"** - check usb debugging is on, tap "allow" on the phone

**agent repeating** - stuck detection handles this. if it persists, use a better model

## contributors

built by [unitedby.ai](https://unitedby.ai) — an open ai community

- [sanju sivalingam](https://sanju.sh)
- [somasundaram mahesh](https://msomu.com)

## acknowledgements

droidclaw's workflow orchestration was influenced by [android action kernel](https://github.com/Action-State-Labs/android-action-kernel) from action state labs. we took the core idea of sub-goal decomposition and built a different system around it — with stuck recovery, 22 actions, multi-step skills, and vision fallback.

## license

mit
