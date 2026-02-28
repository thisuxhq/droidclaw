# How DroidClaw Works

> A complete guide to DroidClaw — an AI agent that controls Android phones through voice and text commands. Written for developers, contributors, and the curious.

---

## What is DroidClaw?

DroidClaw is an AI agent that can **use your Android phone for you**. You tell it what to do in plain English — "Send hello to John on WhatsApp", "Book an Uber to the airport", "Turn on Wi-Fi" — and it figures out the steps, taps the right buttons, types the right text, and gets it done.

It works by combining three things:

1. **An AI brain** (GPT-4, Claude, Gemini, Llama — your choice) that looks at your phone screen and decides what to do next
2. **A server** that runs the thinking loop and coordinates everything
3. **An Android app** that actually taps, swipes, and types on your phone using Android's Accessibility API

Think of it like giving a smart assistant physical control of your phone's touchscreen — except it's doing it through official Android APIs, not by literally touching the screen.

---

## The Big Picture

```
┌─────────────┐     WebSocket      ┌──────────────┐     WebSocket      ┌──────────────┐
│   You (Web   │ ◄────────────────► │    Server     │ ◄────────────────► │  Android App  │
│  Dashboard)  │   live updates     │  (the brain)  │   commands +       │ (the hands)   │
└─────────────┘                     └──────────────┘   screen data       └──────────────┘
                                          │
                                          ▼
                                    ┌──────────┐
                                    │   LLM    │
                                    │ (OpenAI, │
                                    │  Groq,   │
                                    │  etc.)   │
                                    └──────────┘
```

**You** give a goal through the web dashboard or by voice on your phone.
**The server** runs a loop: look at the screen → ask the AI what to do → send the action to the phone → repeat.
**The Android app** executes each action (tap, type, swipe) and sends back what's on the screen.
**The LLM** is the AI model that makes decisions — it sees the screen elements and figures out the next step.

---

## The Agent Loop: Perception → Reasoning → Action

This is the heart of DroidClaw. Every goal runs through a loop that repeats up to **30 steps**:

### Step 1: Perception — "What's on the screen?"

The server asks the Android app: "Send me your screen."

The app responds with two things:
- **The accessibility tree** — a structured list of every UI element (buttons, text fields, labels) with their positions, text, and states (enabled, clickable, checked, etc.)
- **A screenshot** — only when needed (when the tree is empty or the agent is stuck)

This is like giving the AI both a blueprint and a photo of the screen.

### Step 2: Reasoning — "What should I do next?"

The server packages everything up and sends it to the LLM:
- The **goal** ("Send hello to John on WhatsApp")
- The **screen context** (all visible elements as JSON)
- **Recent actions** (what it already tried)
- **Screen changes** (what changed since the last step)
- **Installed apps** (so it knows what's available)

The LLM thinks through it and returns a JSON response:
```json
{
  "think": "I see WhatsApp is installed. I should open it first.",
  "plan": ["Open WhatsApp", "Search for John", "Type hello", "Send"],
  "action": "launch",
  "package": "com.whatsapp",
  "reason": "Opening WhatsApp to find John's chat"
}
```

### Step 3: Action — "Do the thing"

The server translates the LLM's decision into a command and sends it to the Android app. The app executes it using the Accessibility API — tapping buttons, typing text, swiping, opening apps.

Then it waits 500ms for the UI to settle, and loops back to Step 1.

### When Does It Stop?

- The LLM says `"action": "done"` — goal completed successfully
- It hits 30 steps — something probably went wrong
- You press the stop button
- A critical error occurs

---

## The 26 Actions

DroidClaw can perform 26 different actions on your phone:

### Touch & Navigation
| Action | What it does |
|--------|-------------|
| `tap(x, y)` | Tap a specific point on screen |
| `longpress(x, y)` | Long press (for context menus, drag, etc.) |
| `scroll(direction)` | Scroll up, down, left, or right |
| `swipe(x1, y1, x2, y2)` | Custom swipe between two points |
| `enter` | Press the Enter/Send key on the keyboard |
| `back` | Press the Back button |
| `home` | Go to home screen |

### Text
| Action | What it does |
|--------|-------------|
| `type(text)` | Type text into the focused field |
| `clear` | Clear the current text field |
| `paste` | Paste from clipboard |

### Apps & System
| Action | What it does |
|--------|-------------|
| `launch(package)` | Open an app by package name |
| `open_url(url)` | Open a URL in the browser |
| `switch_app(package)` | Switch to a running app |
| `open_settings(setting)` | Open a specific settings page (Wi-Fi, Bluetooth, Display, etc.) |
| `notifications` | Pull down the notification shade |
| `recents` | Open the recent apps view |
| `screenshot` | Take a screenshot |

### Data
| Action | What it does |
|--------|-------------|
| `clipboard_set(text)` | Copy text to clipboard programmatically |
| `clipboard_get` | Read from clipboard |
| `intent(action, uri, extras)` | Launch Android intents (deep links, calls, emails, maps, etc.) |
| `keyevent(code)` | Send a raw Android key code |
| `wait(ms)` | Wait for a specified time |
| `done` | Mark the goal as complete |

### Multi-Step Skills (Server-Side)
| Skill | What it does |
|-------|-------------|
| `read_screen` | Scroll through content and collect all visible text |
| `submit_message` | Find the Send button, tap it, and verify the message was sent |
| `find_and_tap(query)` | Scroll to find specific text and tap it |
| `compose_email(to, subject, body)` | Open email app with fields pre-filled |

Skills are special — they run multiple actions in sequence on the server side, so the LLM doesn't have to micromanage every sub-step.

---

## How the Android App Executes Actions

The Android app uses **AccessibilityService** — the same API that screen readers and automation tools use. This is important: DroidClaw doesn't root your phone or use hacky workarounds. It uses official Android APIs.

### How a Tap Works

1. Server sends: `{ type: "tap", x: 540, y: 1200 }`
2. App finds the accessibility node at those coordinates
3. If the node is clickable → performs `ACTION_CLICK` on it
4. If no clickable node → dispatches a gesture (a synthetic touch event) at those coordinates

### How Typing Works

1. Server sends: `{ type: "type", text: "hello" }`
2. App finds the currently focused input field
3. Performs `ACTION_SET_TEXT` with the new text
4. If no field is focused but coordinates are given → taps first, then types

### How Screen Capture Works

The app captures the screen in two ways:

**Accessibility Tree** (always available):
- Walks through every UI element on screen
- Extracts: text, position, size, clickable/editable/scrollable states
- Filters to the most relevant ~100 elements
- Returns as compact JSON

**Screenshot** (on demand):
- Uses Android's MediaProjection API
- Captures at 720px width, JPEG 50% quality
- Only taken when the accessibility tree is empty or insufficient
- Requires one-time user permission

---

## How Pairing Works

Getting your phone connected to DroidClaw takes about 30 seconds:

```
1. You open the web dashboard and click "Pair Device"
   → Server generates a 6-digit code (expires in 5 minutes)

2. You enter the code in the Android app
   → App sends the code to the server (no login needed)
   → Server validates, generates an API key

3. App receives the API key and server URL
   → Stores them locally
   → Connects via WebSocket

4. Done! Device appears in your dashboard.
```

The pairing endpoint is intentionally public (no auth required) so you don't need to log in on your phone. The 6-digit code acts as a one-time password. Rate limited to 5 attempts per minute to prevent brute force.

---

## Smart Failure Recovery

LLMs are not perfect. They get stuck, repeat themselves, and sometimes make bad decisions. DroidClaw has built-in detection for common failure modes:

### Stuck Loop Detection
The server hashes the screen state after every step. If the screen hasn't changed for 3+ steps, it knows the agent is stuck and injects aggressive recovery hints:

> "You have been stuck for 3 steps. Your plan is NOT working. Create a NEW plan."

It also triggers a screenshot capture so the LLM can literally see what's going on.

### Repetition Detection
The last 8 actions are tracked. If the same action appears 3+ times, the agent gets a warning:

> "Repetition detected. This action clearly is NOT working — do NOT attempt it again."

### Drift Detection
If the agent performs 4+ navigation actions (scroll, back, home) without doing anything productive (tap, type), it gets called out:

> "Drift warning. Stop scrolling. Take a DIRECT action."

These mechanisms turn what would be infinite loops into recoverable situations.

---

## The Tech Stack

| Component | Technology |
|-----------|-----------|
| Server | Hono (HTTP framework) + Bun (runtime) |
| Database | PostgreSQL + Drizzle ORM |
| Auth | better-auth (sessions + API keys) |
| Web Dashboard | SvelteKit + Svelte 5 + Tailwind CSS |
| Android App | Kotlin + Jetpack Compose |
| Connections | WebSocket (Hono + Ktor) |
| Voice | Groq Whisper (speech-to-text) |
| LLMs | OpenAI, Groq, OpenRouter, Ollama, AWS Bedrock |

### Supported LLM Providers

DroidClaw is **model-agnostic**. You bring your own API key and choose your provider:

| Provider | Example Models | Notes |
|----------|---------------|-------|
| OpenAI | GPT-4.1, GPT-4o, o3, o4-mini | Most reliable, best vision |
| Groq | Llama 3.3 70B, Llama 4 Scout | Very fast, free tier available |
| OpenRouter | Any of 100+ models | Aggregator, access to everything |
| Ollama | Llama, Mistral, Phi | Local, private, free |
| AWS Bedrock | Claude, Llama | Enterprise, runs in your AWS account |

Each user configures their own LLM provider and API key through the web settings page. Keys are stored per-user in the database, never in environment variables.

---

## Database Schema

DroidClaw tracks everything in PostgreSQL:

| Table | What it stores |
|-------|---------------|
| `user` | Accounts (email, name, plan) |
| `device` | Connected phones (name, model, Android version, battery, installed apps) |
| `apikey` | Device API keys (hashed, with rate limits and expiry) |
| `llmConfig` | Per-user LLM settings (provider, API key, model) |
| `pairingCode` | Temporary 6-digit OTP codes (5-min TTL) |
| `agentSession` | Goal executions (goal text, status, steps used, timestamps) |
| `agentStep` | Individual steps (action, reasoning, screen hash, result) |

Every goal execution is fully logged — you can replay exactly what the agent did, step by step.

---

## CLI Agent (Standalone Mode)

Don't want to run a server? The CLI agent connects directly to your phone via USB/ADB:

```bash
bun run src/kernel.ts
```

It runs the same perception → reasoning → action loop, but:
- Uses `adb shell uiautomator dump` to get the screen tree (instead of AccessibilityService)
- Executes actions via `adb shell input` commands (instead of WebSocket)
- Runs entirely on your computer — no server, no cloud

Good for development, testing, and one-off automation tasks.

---

## Limitations

### What DroidClaw Can't Do

1. **Apps with FLAG_SECURE** — Banking apps, password managers, and some DRM-protected apps block screen capture. DroidClaw can still see the accessibility tree but can't take screenshots. Some banking apps also block the accessibility tree entirely.

2. **Games and custom canvas UIs** — If an app draws its own UI (games, custom controls, maps), the accessibility tree returns nothing useful. DroidClaw falls back to screenshot-based vision, which is less accurate for tapping coordinates.

3. **CAPTCHA and human verification** — By design, DroidClaw can't solve CAPTCHAs. These exist specifically to block automation.

4. **Multi-finger gestures** — Pinch-to-zoom, two-finger rotation, and other multi-touch gestures aren't supported. The Accessibility API only supports single-finger gestures.

5. **Speed** — Each step takes 2-5 seconds (screen capture + LLM call + action execution). A task that takes you 10 seconds might take the agent 30-60 seconds across 6-10 steps.

6. **100% reliability** — LLMs hallucinate. They sometimes tap the wrong button, misread text, or get confused by complex UIs. Success rate varies by task complexity and model quality (GPT-4 class models: ~85-95% on common tasks; smaller models: ~60-80%).

7. **Real-time interactions** — Things that require split-second timing (catching a notification, responding to a timer) aren't practical with the current loop speed.

8. **Background execution** — The phone screen must stay on and unlocked while the agent works. Android's power management can interfere with long-running tasks.

### What Works Really Well

- **Messaging** — Sending texts, WhatsApp messages, emails. High success rate because the UI patterns are consistent.
- **Settings changes** — Toggling Wi-Fi, Bluetooth, display brightness. Android intents make these nearly 100% reliable.
- **App launching and navigation** — Opening apps, searching within them, scrolling through content.
- **Copy/paste workflows** — Reading content from one app and putting it in another.
- **Form filling** — Filling in text fields, selecting dropdowns, checking boxes.

---

## Scalability

### Current Architecture

DroidClaw's server is designed for **multi-user, multi-device** operation:

- **One server, many devices** — Each user can pair multiple phones. Each phone maintains its own WebSocket connection.
- **Isolated agent loops** — Each goal runs in its own async context. Multiple goals on different devices can execute simultaneously.
- **Per-user LLM keys** — Users bring their own API keys, so LLM costs scale with individual usage, not server load.
- **Lightweight server** — Bun is fast. The server mostly coordinates WebSocket messages and makes LLM API calls. CPU usage is minimal.

### Scaling Bottlenecks

| Bottleneck | Current Limit | How to Scale |
|-----------|---------------|-------------|
| WebSocket connections | ~10K per Bun instance | Horizontal scaling with sticky sessions or connection broker |
| LLM API calls | Provider rate limits (varies) | Users use their own keys; no shared bottleneck |
| Database | Single PostgreSQL instance | Read replicas, connection pooling (PgBouncer) |
| Agent loops | One per device, sequential steps | Already parallel across devices; each loop is independent |
| Screenshot bandwidth | ~50-100KB per capture at 720p JPEG | Only captured when needed; most steps use tree only |

### Scaling to 1,000+ Devices

The architecture naturally supports this because:
1. Each device connection is independent (no shared state between devices)
2. LLM calls are the slowest part and already externalized to providers
3. The server is mostly I/O bound (WebSocket + HTTP), not CPU bound
4. Database writes are lightweight (one row per step)

For serious scale, you'd add:
- A Redis layer for session management and pub/sub
- Multiple server instances behind a load balancer with WebSocket affinity
- A message queue for goal execution to handle bursts
- Database sharding by user ID

---

## Possibilities

### What You Can Build Today

**Personal Automation**
- "Every morning at 8am, check my bank balance and text it to me"
- "When I get a WhatsApp from Mom, auto-reply that I'm in a meeting"
- "Download all photos from this Instagram profile"

**Accessibility**
- Voice-controlled phone operation for users with motor disabilities
- Automated workflows for repetitive tasks (filing expenses, logging health data)
- Simplified interfaces — "just tell it what you want" instead of navigating complex app UIs

**Testing**
- Automated UI testing across real devices (not emulators)
- Regression testing — "open the app, create an account, make a purchase, verify receipt"
- Monkey testing with intelligence — the agent can actually verify outcomes

**Data Collection**
- Scrape app-only content that has no web version
- Monitor prices across shopping apps
- Collect accessibility audit data from apps

### What Becomes Possible with Scale

**Multi-Device Orchestration**
- Coordinate actions across multiple phones simultaneously
- "Send this message to 50 different contacts" across a fleet
- Distributed testing across different Android versions and screen sizes

**Workflow Marketplace**
- Users share reusable workflows ("how to order from Swiggy", "how to file ITR on ClearTax")
- Workflows adapt to different phone configurations and app versions
- Community-built automation library

**Enterprise Automation**
- Automate internal mobile-only tools (field service apps, inventory scanners)
- QA automation for mobile apps in development
- Compliance monitoring — verify app behavior matches specifications

**Agent-to-Agent Communication**
- DroidClaw controlling your phone while another AI agent manages your calendar
- Multi-agent workflows: "research agent finds restaurants, DroidClaw books a table"
- Phone as an API — other systems trigger DroidClaw actions via the server API

### Future Technical Directions

- **On-device LLM** — Run small models (Phi, Gemma) directly on the phone to eliminate server round-trips and enable offline operation
- **Parallel action execution** — Execute independent actions simultaneously (open two apps in split screen, interact with both)
- **Learning from demonstrations** — Watch the user perform a task once, then replay it with variations
- **Proactive agents** — Monitor the phone for triggers (new notification, low battery, specific app state) and act automatically
- **Multi-modal input** — Combine voice commands with pointing at the screen ("tap that thing" + camera pointing)

---

## Example: End-to-End Goal Execution

Here's exactly what happens when you say **"Send hello to John on WhatsApp"**:

```
Step 1 — Perception: Home screen, 15 app icons visible
         Reasoning: "WhatsApp is installed. I should open it."
         Action: launch(com.whatsapp)
         Result: WhatsApp opens to chat list

Step 2 — Perception: Chat list, 20 conversations, search bar at top
         Reasoning: "I need to find John. I'll search for him."
         Action: tap(540, 180) → type("John")
         Result: Search shows "John Doe" result

Step 3 — Perception: Search results, "John Doe" visible at (540, 450)
         Reasoning: "Found John. I'll tap to open the chat."
         Action: tap(540, 450)
         Result: Chat with John opens

Step 4 — Perception: Chat window, message input at bottom, keyboard visible
         Reasoning: "I need to type the message."
         Action: type("hello")
         Result: "hello" appears in input field

Step 5 — Perception: Input shows "hello", Send button visible
         Reasoning: "Message typed. Time to send."
         Action: submit_message
         Result: Message sent, input field cleared

Step 6 — Perception: Chat shows "hello" as sent message with checkmarks
         Reasoning: "Message sent successfully."
         Action: done
         Result: Goal completed in 6 steps (~15 seconds)
```

Every step is logged in the database and streamed live to your dashboard.

---

## Security Model

- **API keys are hashed** (SHA-256) before storage — the server never stores raw keys
- **Pairing codes expire in 5 minutes** and are deleted after use
- **Rate limiting** on pairing (5/min per IP) and API keys (configurable per key)
- **User-owned LLM keys** — your API keys are encrypted in the database, never shared
- **No root required** — everything runs through official Android APIs
- **Session-based auth** on the web dashboard via better-auth
- **Screen data stays in the loop** — screenshots and accessibility trees are processed and discarded, not stored permanently (only screen hashes are logged)

---

## Architecture Decisions & Trade-offs

| Decision | Why |
|----------|-----|
| **Server-side agent loop** (not on-device) | Phones have limited compute. Running the loop on the server allows using powerful LLMs and keeps the phone app simple. |
| **Accessibility API** (not ADB) | Works without USB connection, doesn't require developer mode, works over the internet. ADB is used only in CLI mode for local development. |
| **WebSocket** (not HTTP polling) | Real-time bidirectional communication. The server needs to push commands; the phone needs to push screen updates. HTTP polling would add unacceptable latency. |
| **User-provided LLM keys** (not server-managed) | Eliminates API cost for the server operator. Users choose their provider and model. No vendor lock-in. |
| **JSON actions** (not natural language) | Structured output is more reliable than parsing natural language. Flat schemas work better with weaker models than nested ones. |
| **30-step limit** | Prevents runaway loops from burning through LLM tokens. Most tasks complete in 5-15 steps. |
| **720p screenshots** | Balance between detail and bandwidth. Full resolution would be 3-4x larger with minimal benefit for the LLM. |

---

*DroidClaw is open source. The server, web dashboard, and Android app are all in this repository.*
