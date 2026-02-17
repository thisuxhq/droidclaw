# Option 1: Web Dashboard + Backend Design

> Date: 2026-02-17
> Status: Approved
> Scope: Web (SvelteKit) + Backend (Hono.js) + Android app plan

---

## Decisions

- **Monorepo**: `web/` (SvelteKit dashboard) + `server/` (Hono.js backend) + `android/` (future)
- **Separate Hono server** for WebSocket + agent loop (independent lifecycle from dashboard)
- **SvelteKit** with node adapter for dashboard (deploy to Railway)
- **Multiple API keys** per user with labels (Better Auth apiKey plugin)
- **LLM config on dashboard only** (BYOK -- user provides their own API keys)
- **Goals sent from both** web dashboard and Android app
- **Dashboard v1**: API keys, LLM config, connected devices, goal input, step logs
- **Server runs the agent loop** (phone is eyes + hands)
- **Shared Postgres** on Railway (both services connect to same DB)
- **Build order**: web + server first, Android later

---

## Monorepo Structure

```
droidclaw/
├── src/                  # existing CLI agent (kernel.ts, actions.ts, etc.)
├── web/                  # SvelteKit dashboard (existing, extend)
├── server/               # Hono.js backend (WebSocket + agent loop)
├── android/              # Kotlin companion app (future)
├── packages/shared/      # shared TypeScript types
├── package.json          # root
└── CLAUDE.md
```

---

## Auth & API Key System

Both apps share the same Postgres DB and the same Better Auth tables.

SvelteKit handles user-facing auth (login, signup, sessions). Hono verifies API keys from Android devices.

### Better Auth Config

Both apps use Better Auth with the `apiKey` plugin. SvelteKit adds `sveltekitCookies`, Hono adds session middleware.

```typescript
// shared pattern
plugins: [
  apiKey()  // built-in API key plugin
]
```

### Flow

1. User signs up/logs in on SvelteKit dashboard (existing)
2. Dashboard "API Keys" page -- user creates keys with labels (e.g., "Pixel 8", "Work Phone")
3. Better Auth's apiKey plugin handles create/list/delete
4. User copies key, pastes into Android app SharedPreferences
5. Android app connects to Hono server via WebSocket, sends API key in handshake
6. Hono calls `auth.api.verifyApiKey({ body: { key } })` -- if valid, establishes device session
7. Dashboard WebSocket connections use session cookies (user already logged in)

### Database Schema

Better Auth manages: `user`, `session`, `account`, `verification`, `api_key`

Additional tables (Drizzle):

```
llm_config
  - id: text PK
  - userId: text FK -> user.id
  - provider: text (openai | groq | ollama | bedrock | openrouter)
  - apiKey: text (encrypted)
  - model: text
  - createdAt: timestamp
  - updatedAt: timestamp

device
  - id: text PK
  - userId: text FK -> user.id
  - name: text
  - lastSeen: timestamp
  - status: text (online | offline)
  - deviceInfo: jsonb (model, androidVersion, screenWidth, screenHeight)
  - createdAt: timestamp

agent_session
  - id: text PK
  - userId: text FK -> user.id
  - deviceId: text FK -> device.id
  - goal: text
  - status: text (running | completed | failed | cancelled)
  - stepsUsed: integer
  - startedAt: timestamp
  - completedAt: timestamp

agent_step
  - id: text PK
  - sessionId: text FK -> agent_session.id
  - stepNumber: integer
  - screenHash: text
  - action: jsonb
  - reasoning: text
  - result: text
  - timestamp: timestamp
```

---

## Hono Server Architecture (`server/`)

```
server/
├── src/
│   ├── index.ts              # Hono app + Bun.serve with WebSocket upgrade
│   ├── auth.ts               # Better Auth instance (same DB, apiKey plugin)
│   ├── middleware/
│   │   ├── auth.ts           # Session middleware (dashboard WebSocket)
│   │   └── api-key.ts        # API key verification (Android WebSocket)
│   ├── ws/
│   │   ├── device.ts         # WebSocket handler for Android devices
│   │   ├── dashboard.ts      # WebSocket handler for web dashboard (live logs)
│   │   └── sessions.ts       # In-memory session manager (connected devices + active loops)
│   ├── agent/
│   │   ├── loop.ts           # Agent loop (adapted from kernel.ts)
│   │   ├── llm.ts            # LLM provider factory (adapted from llm-providers.ts)
│   │   ├── stuck.ts          # Stuck-loop detection
│   │   └── skills.ts         # Multi-step skills (adapted from skills.ts)
│   ├── routes/
│   │   ├── devices.ts        # GET /devices
│   │   ├── goals.ts          # POST /goals
│   │   └── health.ts         # GET /health
│   ├── db.ts                 # Drizzle instance (same Postgres)
│   └── env.ts                # Environment config
├── package.json
├── tsconfig.json
└── Dockerfile
```

### Key Design Points

1. **Bun.serve() with WebSocket upgrade** -- Hono handles HTTP, Bun native WebSocket handles upgrades. No extra WS library.

2. **Two WebSocket paths:**
   - `/ws/device` -- Android app connects with API key
   - `/ws/dashboard` -- Web dashboard connects with session cookie

3. **sessions.ts** -- In-memory map tracking connected devices, active agent loops, dashboard subscribers.

4. **Agent loop (loop.ts)** -- Adapted from kernel.ts. Same perception/reasoning/action cycle. Sends WebSocket commands instead of ADB calls.

5. **Goal submission:**
   - Dashboard: POST /goals -> starts agent loop -> streams steps via dashboard WebSocket
   - Android: device sends `{ type: "goal", text: "..." }` -> same agent loop

---

## SvelteKit Dashboard (`web/`)

Follows existing patterns: remote functions (`$app/server` form/query), Svelte 5 runes, Tailwind v4, Valibot schemas.

### Route Structure

```
web/src/routes/
├── +layout.svelte              # add nav bar
├── +layout.server.ts           # load session for all pages
├── +page.svelte                # redirect: logged in -> /dashboard, else -> /login
├── login/+page.svelte          # existing
├── signup/+page.svelte         # existing
├── dashboard/
│   ├── +layout.svelte          # dashboard shell (sidebar nav)
│   ├── +page.svelte            # overview: connected devices, quick goal input
│   ├── api-keys/
│   │   └── +page.svelte        # list keys, create with label, copy, delete
│   ├── settings/
│   │   └── +page.svelte        # LLM provider config (provider, API key, model)
│   └── devices/
│       ├── +page.svelte        # list connected devices with status
│       └── [deviceId]/
│           └── +page.svelte    # device detail: send goal, live step log
```

### Remote Functions

```
web/src/lib/api/
├── auth.remote.ts         # existing (signup, login, signout, getUser)
├── api-keys.remote.ts     # createKey, listKeys, deleteKey (Better Auth client)
├── settings.remote.ts     # getConfig, updateConfig (LLM provider/key)
├── devices.remote.ts      # listDevices (queries Hono server)
└── goals.remote.ts        # submitGoal (POST to Hono server)
```

Dashboard WebSocket for live step logs connects directly to Hono server from the browser (not through SvelteKit).

---

## WebSocket Protocol

### Device -> Server (Android app sends)

```json
// Handshake
{ "type": "auth", "apiKey": "dc_xxxxx" }

// Screen tree response
{ "type": "screen", "requestId": "uuid", "elements": [], "screenshot": "base64?", "packageName": "com.app" }

// Action result
{ "type": "result", "requestId": "uuid", "success": true, "error": null, "data": null }

// Goal from phone
{ "type": "goal", "text": "open youtube and search lofi" }

// Heartbeat
{ "type": "pong" }
```

### Server -> Device (Hono sends)

```json
// Auth
{ "type": "auth_ok", "deviceId": "uuid" }
{ "type": "auth_error", "message": "invalid key" }

// Commands (all 22 actions)
{ "type": "get_screen", "requestId": "uuid" }
{ "type": "tap", "requestId": "uuid", "x": 540, "y": 1200 }
{ "type": "type", "requestId": "uuid", "text": "lofi beats" }
{ "type": "swipe", "requestId": "uuid", "x1": 540, "y1": 1600, "x2": 540, "y2": 400 }
{ "type": "enter", "requestId": "uuid" }
{ "type": "back", "requestId": "uuid" }
{ "type": "home", "requestId": "uuid" }
{ "type": "launch", "requestId": "uuid", "packageName": "com.google.android.youtube" }
// ... remaining actions follow same pattern

// Heartbeat
{ "type": "ping" }

// Goal lifecycle
{ "type": "goal_started", "sessionId": "uuid", "goal": "..." }
{ "type": "goal_completed", "sessionId": "uuid", "success": true, "stepsUsed": 12 }
```

### Server -> Dashboard (live step stream)

```json
// Device status
{ "type": "device_online", "deviceId": "uuid", "name": "Pixel 8" }
{ "type": "device_offline", "deviceId": "uuid" }

// Step stream
{ "type": "step", "sessionId": "uuid", "step": 3, "action": {}, "reasoning": "...", "screenHash": "..." }
{ "type": "goal_started", "sessionId": "uuid", "goal": "...", "deviceId": "uuid" }
{ "type": "goal_completed", "sessionId": "uuid", "success": true, "stepsUsed": 12 }
```

---

## Shared Types (`packages/shared/`)

```
packages/shared/
├── src/
│   ├── types.ts          # UIElement, Bounds, Point
│   ├── commands.ts       # Command, CommandResult type unions
│   ├── actions.ts        # ActionDecision type (all 22 actions)
│   └── protocol.ts       # WebSocket message types
├── package.json          # name: "@droidclaw/shared"
└── tsconfig.json
```

Replaces duplicated types across src/, server/, web/. Android app mirrors in Kotlin via @Serializable data classes.

---

## Android App (future, plan only)

```
android/
├── app/src/main/kotlin/ai/droidclaw/companion/
│   ├── DroidClawApp.kt
│   ├── MainActivity.kt                    # API key input, setup checklist, status
│   ├── accessibility/
│   │   ├── DroidClawAccessibilityService.kt
│   │   ├── ScreenTreeBuilder.kt
│   │   └── GestureExecutor.kt
│   ├── capture/
│   │   └── ScreenCaptureService.kt
│   ├── connection/
│   │   ├── ConnectionService.kt           # Foreground service
│   │   ├── ReliableWebSocket.kt           # Reconnect, heartbeat, message queue
│   │   └── CommandRouter.kt
│   └── model/
│       ├── UIElement.kt                   # Mirrors @droidclaw/shared types
│       ├── Command.kt
│       └── DeviceInfo.kt
├── build.gradle.kts
└── AndroidManifest.xml
```

Follows OPTION1-IMPLEMENTATION.md structure. Not building now, but server protocol is designed for it.

---

## Deployment (Railway)

| Service | Source | Port | Notes |
|---|---|---|---|
| web | `web/` | 3000 | SvelteKit + node adapter |
| server | `server/` | 8080 | Hono + Bun.serve |
| postgres | Railway managed | 5432 | Shared by both services |

Both services get the same `DATABASE_URL`. Web calls Hono via Railway internal networking for REST. Browser connects directly to Hono's public URL for WebSocket.

---

## Data Flow

```
USER (browser)                     HONO SERVER                      PHONE (Android app)
     |                               |                               |
     |  signs in (SvelteKit)         |                               |
     |  creates API key              |                               |
     |                               |                               |
     |                               |    { type: "auth", key: "dc_xxx" }
     |                               |<------------------------------|
     |                               |    { type: "auth_ok" }        |
     |                               |------------------------------>|
     |                               |                               |
     |  POST /goals                  |                               |
     |  "open youtube, search lofi"  |                               |
     |------------------------------>|                               |
     |                               |  { type: "get_screen" }       |
     |                               |------------------------------>|
     |                               |                               |
     |                               |  { type: "screen", elements } |
     |                               |<------------------------------|
     |                               |                               |
     |                               |  LLM: "launch youtube"        |
     |                               |                               |
     |  { type: "step", action }     |  { type: "launch", pkg }      |
     |<------------------------------|------------------------------>|
     |                               |                               |
     |                               |  { success: true }            |
     |                               |<------------------------------|
     |                               |                               |
     |  ... repeat until done ...    |                               |
     |                               |                               |
     |  { type: "goal_completed" }   |  { type: "goal_completed" }   |
     |<------------------------------|------------------------------>|
```
