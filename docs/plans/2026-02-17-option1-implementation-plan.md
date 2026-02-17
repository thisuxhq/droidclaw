# Option 1: Web + Backend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the SvelteKit dashboard and Hono.js backend so users can sign up, manage API keys, configure LLM providers, connect Android devices via WebSocket, and run the DroidClaw agent loop from the browser.

**Architecture:** Monorepo with `packages/shared/` (types), `server/` (Hono + Bun WebSocket + agent loop), and `web/` (SvelteKit dashboard). Both services share the same Postgres via Drizzle. Better Auth handles user auth (SvelteKit) and API key verification (Hono). The agent loop runs server-side, sending commands to connected phones via WebSocket.

**Tech Stack:** SvelteKit 2 (Svelte 5, node adapter), Hono.js (Bun), Drizzle ORM, Postgres, Better Auth (apiKey plugin), Tailwind v4, Valibot, TypeScript.

**Design doc:** `docs/plans/2026-02-17-option1-web-backend-design.md`

---

## Task 1: Shared Types Package

**Files:**
- Create: `packages/shared/package.json`
- Create: `packages/shared/tsconfig.json`
- Create: `packages/shared/src/index.ts`
- Create: `packages/shared/src/types.ts`
- Create: `packages/shared/src/protocol.ts`

**Step 1: Create package.json**

```json
{
  "name": "@droidclaw/shared",
  "version": "0.0.1",
  "type": "module",
  "exports": {
    ".": "./src/index.ts"
  },
  "scripts": {
    "typecheck": "tsc --noEmit"
  },
  "devDependencies": {
    "typescript": "^5.9.2"
  }
}
```

**Step 2: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "declaration": true,
    "outDir": "dist",
    "rootDir": "src"
  },
  "include": ["src/**/*.ts"]
}
```

**Step 3: Create types.ts**

Port the core types from `src/sanitizer.ts` (UIElement) and `src/actions.ts` (ActionDecision, ActionResult) into shared types that work for both ADB and WebSocket connections.

```typescript
// packages/shared/src/types.ts

export interface UIElement {
  id: string;
  text: string;
  type: string;
  bounds: string;
  center: [number, number];
  size: [number, number];
  clickable: boolean;
  editable: boolean;
  enabled: boolean;
  checked: boolean;
  focused: boolean;
  selected: boolean;
  scrollable: boolean;
  longClickable: boolean;
  password: boolean;
  hint: string;
  action: "tap" | "type" | "longpress" | "scroll" | "read";
  parent: string;
  depth: number;
}

export interface ActionDecision {
  action: string;
  coordinates?: [number, number];
  text?: string;
  direction?: string;
  reason?: string;
  package?: string;
  activity?: string;
  uri?: string;
  extras?: Record<string, string>;
  command?: string;
  filename?: string;
  think?: string;
  plan?: string[];
  planProgress?: string;
  skill?: string;
  query?: string;
  url?: string;
  path?: string;
  source?: string;
  dest?: string;
  code?: number;
  setting?: string;
}

export interface ActionResult {
  success: boolean;
  message: string;
  data?: string;
}

export interface DeviceInfo {
  model: string;
  androidVersion: string;
  screenWidth: number;
  screenHeight: number;
}

export interface ScreenState {
  elements: UIElement[];
  screenshot?: string; // base64 PNG
  packageName?: string;
  fallbackReason?: string;
}
```

**Step 4: Create protocol.ts**

```typescript
// packages/shared/src/protocol.ts

import type { UIElement, ActionResult, DeviceInfo } from "./types.js";

// --- Device -> Server messages ---

export type DeviceMessage =
  | { type: "auth"; apiKey: string; deviceInfo?: DeviceInfo }
  | { type: "screen"; requestId: string; elements: UIElement[]; screenshot?: string; packageName?: string }
  | { type: "result"; requestId: string; success: boolean; error?: string; data?: string }
  | { type: "goal"; text: string }
  | { type: "pong" };

// --- Server -> Device messages ---

export type ServerToDeviceMessage =
  | { type: "auth_ok"; deviceId: string }
  | { type: "auth_error"; message: string }
  | { type: "get_screen"; requestId: string }
  | { type: "tap"; requestId: string; x: number; y: number }
  | { type: "type"; requestId: string; text: string }
  | { type: "swipe"; requestId: string; x1: number; y1: number; x2: number; y2: number; duration?: number }
  | { type: "enter"; requestId: string }
  | { type: "back"; requestId: string }
  | { type: "home"; requestId: string }
  | { type: "longpress"; requestId: string; x: number; y: number }
  | { type: "launch"; requestId: string; packageName: string }
  | { type: "clear"; requestId: string }
  | { type: "clipboard_set"; requestId: string; text: string }
  | { type: "clipboard_get"; requestId: string }
  | { type: "paste"; requestId: string }
  | { type: "open_url"; requestId: string; url: string }
  | { type: "switch_app"; requestId: string; packageName: string }
  | { type: "notifications"; requestId: string }
  | { type: "keyevent"; requestId: string; code: number }
  | { type: "open_settings"; requestId: string }
  | { type: "wait"; requestId: string; duration?: number }
  | { type: "ping" }
  | { type: "goal_started"; sessionId: string; goal: string }
  | { type: "goal_completed"; sessionId: string; success: boolean; stepsUsed: number };

// --- Server -> Dashboard messages ---

export type DashboardMessage =
  | { type: "device_online"; deviceId: string; name: string }
  | { type: "device_offline"; deviceId: string }
  | { type: "step"; sessionId: string; step: number; action: Record<string, unknown>; reasoning: string; screenHash: string }
  | { type: "goal_started"; sessionId: string; goal: string; deviceId: string }
  | { type: "goal_completed"; sessionId: string; success: boolean; stepsUsed: number };
```

**Step 5: Create index.ts (barrel export)**

```typescript
export * from "./types.js";
export * from "./protocol.js";
```

**Step 6: Install dependencies and verify typecheck**

```bash
cd packages/shared && bun install && bun run typecheck
```

**Step 7: Commit**

```bash
git add packages/shared
git commit -m "feat: add @droidclaw/shared types package"
```

---

## Task 2: Hono Server Scaffolding

**Files:**
- Create: `server/package.json`
- Create: `server/tsconfig.json`
- Create: `server/src/index.ts`
- Create: `server/src/env.ts`
- Create: `server/src/db.ts`
- Create: `server/src/auth.ts`
- Create: `server/.env.example`
- Create: `server/Dockerfile`

**Step 1: Create package.json**

```json
{
  "name": "@droidclaw/server",
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "bun --watch src/index.ts",
    "start": "bun src/index.ts",
    "typecheck": "tsc --noEmit",
    "db:push": "drizzle-kit push",
    "db:generate": "drizzle-kit generate",
    "db:migrate": "drizzle-kit migrate"
  },
  "dependencies": {
    "hono": "^4.7.0",
    "better-auth": "^1.3.27",
    "drizzle-orm": "^0.44.5",
    "postgres": "^3.4.7"
  },
  "devDependencies": {
    "@types/bun": "^1.1.0",
    "drizzle-kit": "^0.31.4",
    "typescript": "^5.9.2"
  }
}
```

**Step 2: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "outDir": "dist",
    "rootDir": "src",
    "types": ["bun-types"],
    "paths": {
      "@droidclaw/shared": ["../packages/shared/src"]
    }
  },
  "include": ["src/**/*.ts"]
}
```

**Step 3: Create .env.example**

```
DATABASE_URL="postgres://user:password@host:port/db-name"
PORT=8080
CORS_ORIGIN="http://localhost:5173"
```

**Step 4: Create env.ts**

```typescript
// server/src/env.ts

export const env = {
  DATABASE_URL: process.env.DATABASE_URL!,
  PORT: parseInt(process.env.PORT || "8080"),
  CORS_ORIGIN: process.env.CORS_ORIGIN || "http://localhost:5173",
};

if (!env.DATABASE_URL) {
  throw new Error("DATABASE_URL is not set");
}
```

**Step 5: Create db.ts**

```typescript
// server/src/db.ts

import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import { env } from "./env.js";

const client = postgres(env.DATABASE_URL);
export const db = drizzle(client);
```

**Step 6: Create auth.ts**

Better Auth instance with apiKey plugin, pointing to same Postgres. No sveltekitCookies — Hono uses its own session middleware.

```typescript
// server/src/auth.ts

import { betterAuth } from "better-auth";
import { apiKey } from "better-auth/plugins";
import { drizzleAdapter } from "better-auth/adapters/drizzle";
import { db } from "./db.js";

export const auth = betterAuth({
  database: drizzleAdapter(db, {
    provider: "pg",
  }),
  plugins: [apiKey()],
});
```

**Step 7: Create index.ts**

Minimal Hono app with Better Auth handler, CORS, health check. WebSocket upgrade via Bun.serve.

```typescript
// server/src/index.ts

import { Hono } from "hono";
import { cors } from "hono/cors";
import { auth } from "./auth.js";
import { env } from "./env.js";

const app = new Hono();

// CORS for dashboard
app.use(
  "*",
  cors({
    origin: env.CORS_ORIGIN,
    allowHeaders: ["Content-Type", "Authorization"],
    allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    credentials: true,
  })
);

// Better Auth handler
app.on(["POST", "GET"], "/api/auth/*", (c) => {
  return auth.handler(c.req.raw);
});

// Health check
app.get("/health", (c) => c.json({ status: "ok" }));

// Start server with WebSocket support
const server = Bun.serve({
  port: env.PORT,
  fetch: app.fetch,
  websocket: {
    open(ws) {
      console.log("WebSocket connected");
    },
    message(ws, message) {
      // placeholder — Task 4 implements device/dashboard handlers
    },
    close(ws) {
      console.log("WebSocket disconnected");
    },
  },
});

console.log(`Server running on port ${server.port}`);
```

**Step 8: Create Dockerfile**

```dockerfile
FROM oven/bun:1

WORKDIR /app

COPY packages/shared ./packages/shared
COPY server ./server

WORKDIR /app/server
RUN bun install

EXPOSE 8080
CMD ["bun", "src/index.ts"]
```

**Step 9: Install dependencies and verify**

```bash
cd server && bun install && bun run typecheck
```

**Step 10: Start dev server and test health endpoint**

```bash
cd server && bun run dev
# In another terminal:
curl http://localhost:8080/health
# Expected: {"status":"ok"}
```

**Step 11: Commit**

```bash
git add server
git commit -m "feat: scaffold Hono server with auth and health check"
```

---

## Task 3: Extended Database Schema

**Files:**
- Modify: `web/src/lib/server/db/schema.ts` (add new tables)
- Modify: `web/src/lib/server/auth.ts` (add apiKey plugin)
- Modify: `web/src/lib/auth-client.ts` (add apiKey client plugin)

**Step 1: Add apiKey plugin to Better Auth server config**

In `web/src/lib/server/auth.ts`, add the apiKey plugin:

```typescript
import { betterAuth } from 'better-auth';
import { sveltekitCookies } from 'better-auth/svelte-kit';
import { apiKey } from 'better-auth/plugins';
import { drizzleAdapter } from 'better-auth/adapters/drizzle';
import { db } from './db';
import { getRequestEvent } from '$app/server';

export const auth = betterAuth({
  database: drizzleAdapter(db, {
    provider: 'pg'
  }),
  plugins: [sveltekitCookies(getRequestEvent), apiKey()],
  emailAndPassword: {
    enabled: true
  }
});
```

**Step 2: Add apiKey client plugin**

In `web/src/lib/auth-client.ts`:

```typescript
import { createAuthClient } from 'better-auth/svelte';
import { apiKeyClient } from 'better-auth/client/plugins';

export const authClient = createAuthClient({
  baseURL: 'http://localhost:5173',
  plugins: [apiKeyClient()]
});
```

**Step 3: Add new tables to schema.ts**

Append to `web/src/lib/server/db/schema.ts`:

```typescript
import { pgTable, text, timestamp, boolean, integer, jsonb } from 'drizzle-orm/pg-core';

// ... existing user, session, account, verification tables stay unchanged ...

export const llmConfig = pgTable('llm_config', {
  id: text('id').primaryKey(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  provider: text('provider').notNull(), // openai | groq | ollama | bedrock | openrouter
  apiKey: text('api_key').notNull(), // encrypted
  model: text('model'),
  createdAt: timestamp('created_at').defaultNow().notNull(),
  updatedAt: timestamp('updated_at')
    .defaultNow()
    .$onUpdate(() => new Date())
    .notNull()
});

export const device = pgTable('device', {
  id: text('id').primaryKey(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  lastSeen: timestamp('last_seen'),
  status: text('status').notNull().default('offline'), // online | offline
  deviceInfo: jsonb('device_info'), // { model, androidVersion, screenWidth, screenHeight }
  createdAt: timestamp('created_at').defaultNow().notNull()
});

export const agentSession = pgTable('agent_session', {
  id: text('id').primaryKey(),
  userId: text('user_id')
    .notNull()
    .references(() => user.id, { onDelete: 'cascade' }),
  deviceId: text('device_id')
    .notNull()
    .references(() => device.id, { onDelete: 'cascade' }),
  goal: text('goal').notNull(),
  status: text('status').notNull().default('running'), // running | completed | failed | cancelled
  stepsUsed: integer('steps_used').default(0),
  startedAt: timestamp('started_at').defaultNow().notNull(),
  completedAt: timestamp('completed_at')
});

export const agentStep = pgTable('agent_step', {
  id: text('id').primaryKey(),
  sessionId: text('session_id')
    .notNull()
    .references(() => agentSession.id, { onDelete: 'cascade' }),
  stepNumber: integer('step_number').notNull(),
  screenHash: text('screen_hash'),
  action: jsonb('action'),
  reasoning: text('reasoning'),
  result: text('result'),
  timestamp: timestamp('timestamp').defaultNow().notNull()
});
```

**Step 4: Generate and run migration**

```bash
cd web && bun run db:generate && bun run db:push
```

**Step 5: Verify Better Auth apiKey table was created**

Better Auth auto-manages its `api_key` table. Check with:

```bash
cd web && bun run db:studio
```

Verify tables exist: `user`, `session`, `account`, `verification`, `api_key`, `llm_config`, `device`, `agent_session`, `agent_step`.

**Step 6: Commit**

```bash
git add web/src/lib/server/db/schema.ts web/src/lib/server/auth.ts web/src/lib/auth-client.ts web/drizzle/
git commit -m "feat: add apiKey plugin and new schema tables"
```

---

## Task 4: Hono WebSocket Handlers

**Files:**
- Create: `server/src/ws/sessions.ts`
- Create: `server/src/ws/device.ts`
- Create: `server/src/ws/dashboard.ts`
- Modify: `server/src/index.ts` (wire up WebSocket upgrade with path routing)

**Step 1: Create sessions.ts (in-memory session manager)**

```typescript
// server/src/ws/sessions.ts

import type { ServerWebSocket } from "bun";
import type { DeviceInfo } from "@droidclaw/shared";

export interface ConnectedDevice {
  deviceId: string;
  userId: string;
  ws: ServerWebSocket<WebSocketData>;
  deviceInfo?: DeviceInfo;
  connectedAt: Date;
}

export interface DashboardSubscriber {
  userId: string;
  ws: ServerWebSocket<WebSocketData>;
}

export interface WebSocketData {
  path: string; // "/ws/device" or "/ws/dashboard"
  userId?: string;
  deviceId?: string;
  authenticated: boolean;
}

// request/response tracking for command-response pattern
export interface PendingRequest {
  resolve: (value: unknown) => void;
  reject: (reason: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

class SessionManager {
  // deviceId -> ConnectedDevice
  devices = new Map<string, ConnectedDevice>();
  // userId -> deviceId[] (one user can have multiple devices)
  userDevices = new Map<string, Set<string>>();
  // userId -> DashboardSubscriber[]
  dashboardSubscribers = new Map<string, DashboardSubscriber[]>();
  // requestId -> PendingRequest (for command-response pattern)
  pendingRequests = new Map<string, PendingRequest>();

  addDevice(device: ConnectedDevice) {
    this.devices.set(device.deviceId, device);
    const userDevs = this.userDevices.get(device.userId) ?? new Set();
    userDevs.add(device.deviceId);
    this.userDevices.set(device.userId, userDevs);
  }

  removeDevice(deviceId: string) {
    const device = this.devices.get(deviceId);
    if (device) {
      this.devices.delete(deviceId);
      const userDevs = this.userDevices.get(device.userId);
      if (userDevs) {
        userDevs.delete(deviceId);
        if (userDevs.size === 0) this.userDevices.delete(device.userId);
      }
    }
  }

  getDevice(deviceId: string): ConnectedDevice | undefined {
    return this.devices.get(deviceId);
  }

  getDevicesForUser(userId: string): ConnectedDevice[] {
    const deviceIds = this.userDevices.get(userId);
    if (!deviceIds) return [];
    return [...deviceIds]
      .map((id) => this.devices.get(id))
      .filter((d): d is ConnectedDevice => d !== undefined);
  }

  addDashboardSubscriber(sub: DashboardSubscriber) {
    const subs = this.dashboardSubscribers.get(sub.userId) ?? [];
    subs.push(sub);
    this.dashboardSubscribers.set(sub.userId, subs);
  }

  removeDashboardSubscriber(ws: ServerWebSocket<WebSocketData>) {
    for (const [userId, subs] of this.dashboardSubscribers) {
      const filtered = subs.filter((s) => s.ws !== ws);
      if (filtered.length === 0) {
        this.dashboardSubscribers.delete(userId);
      } else {
        this.dashboardSubscribers.set(userId, filtered);
      }
    }
  }

  // send message to all dashboard subscribers for a user
  notifyDashboard(userId: string, message: object) {
    const subs = this.dashboardSubscribers.get(userId);
    if (!subs) return;
    const data = JSON.stringify(message);
    for (const sub of subs) {
      sub.ws.send(data);
    }
  }

  // send command to device, return promise that resolves when device responds
  sendCommand(deviceId: string, command: object, timeout = 15_000): Promise<unknown> {
    const device = this.devices.get(deviceId);
    if (!device) return Promise.reject(new Error("device not connected"));

    const requestId = crypto.randomUUID();
    const commandWithId = { ...command, requestId };

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingRequests.delete(requestId);
        reject(new Error(`command timeout: ${JSON.stringify(command)}`));
      }, timeout);

      this.pendingRequests.set(requestId, { resolve, reject, timer });
      device.ws.send(JSON.stringify(commandWithId));
    });
  }

  // resolve a pending request (called when device sends a response)
  resolveRequest(requestId: string, data: unknown) {
    const pending = this.pendingRequests.get(requestId);
    if (pending) {
      clearTimeout(pending.timer);
      this.pendingRequests.delete(requestId);
      pending.resolve(data);
    }
  }
}

export const sessions = new SessionManager();
```

**Step 2: Create device.ts (device WebSocket handler)**

```typescript
// server/src/ws/device.ts

import type { ServerWebSocket } from "bun";
import { auth } from "../auth.js";
import { sessions, type WebSocketData } from "./sessions.js";
import type { DeviceMessage } from "@droidclaw/shared";

export async function handleDeviceMessage(
  ws: ServerWebSocket<WebSocketData>,
  raw: string
) {
  let msg: DeviceMessage;
  try {
    msg = JSON.parse(raw);
  } catch {
    ws.send(JSON.stringify({ type: "error", message: "invalid JSON" }));
    return;
  }

  // handle auth handshake
  if (msg.type === "auth") {
    try {
      const result = await auth.api.verifyApiKey({
        body: { key: msg.apiKey },
      });

      if (!result || !result.valid || !result.key) {
        ws.send(JSON.stringify({ type: "auth_error", message: "invalid API key" }));
        ws.close();
        return;
      }

      const deviceId = crypto.randomUUID();
      ws.data.userId = result.key.userId;
      ws.data.deviceId = deviceId;
      ws.data.authenticated = true;

      sessions.addDevice({
        deviceId,
        userId: result.key.userId,
        ws,
        deviceInfo: msg.deviceInfo,
        connectedAt: new Date(),
      });

      ws.send(JSON.stringify({ type: "auth_ok", deviceId }));

      // notify dashboard subscribers
      sessions.notifyDashboard(result.key.userId, {
        type: "device_online",
        deviceId,
        name: msg.deviceInfo?.model ?? "Unknown Device",
      });

      console.log(`Device ${deviceId} connected for user ${result.key.userId}`);
    } catch (e) {
      ws.send(
        JSON.stringify({
          type: "auth_error",
          message: e instanceof Error ? e.message : "auth failed",
        })
      );
      ws.close();
    }
    return;
  }

  // all other messages require authentication
  if (!ws.data.authenticated) {
    ws.send(JSON.stringify({ type: "auth_error", message: "not authenticated" }));
    ws.close();
    return;
  }

  switch (msg.type) {
    case "screen":
    case "result":
      // resolve the pending command request
      sessions.resolveRequest(msg.requestId, msg);
      break;

    case "goal":
      // device-initiated goal — will be handled by agent loop (Task 6)
      // for now, acknowledge
      console.log(`Goal from device ${ws.data.deviceId}: ${msg.text}`);
      break;

    case "pong":
      // heartbeat response — device is alive
      break;
  }
}

export function handleDeviceClose(ws: ServerWebSocket<WebSocketData>) {
  if (ws.data.deviceId && ws.data.userId) {
    sessions.removeDevice(ws.data.deviceId);
    sessions.notifyDashboard(ws.data.userId, {
      type: "device_offline",
      deviceId: ws.data.deviceId,
    });
    console.log(`Device ${ws.data.deviceId} disconnected`);
  }
}
```

**Step 3: Create dashboard.ts (dashboard WebSocket handler)**

```typescript
// server/src/ws/dashboard.ts

import type { ServerWebSocket } from "bun";
import { auth } from "../auth.js";
import { sessions, type WebSocketData } from "./sessions.js";

export async function handleDashboardMessage(
  ws: ServerWebSocket<WebSocketData>,
  raw: string
) {
  let msg: { type: string; [key: string]: unknown };
  try {
    msg = JSON.parse(raw);
  } catch {
    ws.send(JSON.stringify({ type: "error", message: "invalid JSON" }));
    return;
  }

  // auth via session token (sent as first message)
  if (msg.type === "auth") {
    try {
      const token = msg.token as string;
      const session = await auth.api.getSession({
        headers: new Headers({ Authorization: `Bearer ${token}` }),
      });

      if (!session) {
        ws.send(JSON.stringify({ type: "auth_error", message: "invalid session" }));
        ws.close();
        return;
      }

      ws.data.userId = session.user.id;
      ws.data.authenticated = true;

      sessions.addDashboardSubscriber({
        userId: session.user.id,
        ws,
      });

      ws.send(JSON.stringify({ type: "auth_ok" }));

      // send current device list
      const devices = sessions.getDevicesForUser(session.user.id);
      for (const device of devices) {
        ws.send(
          JSON.stringify({
            type: "device_online",
            deviceId: device.deviceId,
            name: device.deviceInfo?.model ?? "Unknown Device",
          })
        );
      }
    } catch {
      ws.send(JSON.stringify({ type: "auth_error", message: "auth failed" }));
      ws.close();
    }
    return;
  }

  if (!ws.data.authenticated) {
    ws.send(JSON.stringify({ type: "auth_error", message: "not authenticated" }));
    return;
  }

  // dashboard messages handled here (e.g., goal submission via WebSocket)
  // REST endpoint POST /goals is the primary way — this is a secondary path
}

export function handleDashboardClose(ws: ServerWebSocket<WebSocketData>) {
  sessions.removeDashboardSubscriber(ws);
}
```

**Step 4: Update index.ts with WebSocket upgrade routing**

Replace the placeholder websocket handlers in `server/src/index.ts`:

```typescript
// server/src/index.ts

import { Hono } from "hono";
import { cors } from "hono/cors";
import { auth } from "./auth.js";
import { env } from "./env.js";
import { handleDeviceMessage, handleDeviceClose } from "./ws/device.js";
import { handleDashboardMessage, handleDashboardClose } from "./ws/dashboard.js";
import type { WebSocketData } from "./ws/sessions.js";

const app = new Hono();

app.use(
  "*",
  cors({
    origin: env.CORS_ORIGIN,
    allowHeaders: ["Content-Type", "Authorization"],
    allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    credentials: true,
  })
);

app.on(["POST", "GET"], "/api/auth/*", (c) => {
  return auth.handler(c.req.raw);
});

app.get("/health", (c) => c.json({ status: "ok" }));

const server = Bun.serve<WebSocketData>({
  port: env.PORT,
  fetch(req, server) {
    const url = new URL(req.url);

    // WebSocket upgrade for device connections
    if (url.pathname === "/ws/device") {
      const upgraded = server.upgrade(req, {
        data: { path: "/ws/device", authenticated: false },
      });
      if (upgraded) return undefined;
      return new Response("WebSocket upgrade failed", { status: 400 });
    }

    // WebSocket upgrade for dashboard connections
    if (url.pathname === "/ws/dashboard") {
      const upgraded = server.upgrade(req, {
        data: { path: "/ws/dashboard", authenticated: false },
      });
      if (upgraded) return undefined;
      return new Response("WebSocket upgrade failed", { status: 400 });
    }

    // all other requests go to Hono
    return app.fetch(req);
  },
  websocket: {
    open(ws) {
      console.log(`WebSocket opened: ${ws.data.path}`);
    },
    message(ws, message) {
      const raw = typeof message === "string" ? message : new TextDecoder().decode(message);
      if (ws.data.path === "/ws/device") {
        handleDeviceMessage(ws, raw);
      } else if (ws.data.path === "/ws/dashboard") {
        handleDashboardMessage(ws, raw);
      }
    },
    close(ws) {
      if (ws.data.path === "/ws/device") {
        handleDeviceClose(ws);
      } else if (ws.data.path === "/ws/dashboard") {
        handleDashboardClose(ws);
      }
    },
  },
});

console.log(`Server running on port ${server.port}`);
```

**Step 5: Verify typecheck**

```bash
cd server && bun run typecheck
```

**Step 6: Commit**

```bash
git add server/src/ws/ server/src/index.ts
git commit -m "feat: add WebSocket handlers for device and dashboard connections"
```

---

## Task 5: Hono REST Routes

**Files:**
- Create: `server/src/routes/devices.ts`
- Create: `server/src/routes/goals.ts`
- Create: `server/src/routes/health.ts`
- Modify: `server/src/index.ts` (mount routes)

**Step 1: Create session middleware for REST routes**

```typescript
// server/src/middleware/auth.ts

import type { Context, Next } from "hono";
import { auth } from "../auth.js";

export async function sessionMiddleware(c: Context, next: Next) {
  const session = await auth.api.getSession({
    headers: c.req.raw.headers,
  });

  if (!session) {
    return c.json({ error: "unauthorized" }, 401);
  }

  c.set("user", session.user);
  c.set("session", session.session);
  await next();
}
```

**Step 2: Create devices route**

```typescript
// server/src/routes/devices.ts

import { Hono } from "hono";
import { sessionMiddleware } from "../middleware/auth.js";
import { sessions } from "../ws/sessions.js";

const devices = new Hono();

devices.use("*", sessionMiddleware);

// list connected devices for the authenticated user
devices.get("/", (c) => {
  const user = c.get("user");
  const userDevices = sessions.getDevicesForUser(user.id);

  return c.json(
    userDevices.map((d) => ({
      deviceId: d.deviceId,
      name: d.deviceInfo?.model ?? "Unknown Device",
      deviceInfo: d.deviceInfo,
      connectedAt: d.connectedAt.toISOString(),
    }))
  );
});

export { devices };
```

**Step 3: Create goals route**

```typescript
// server/src/routes/goals.ts

import { Hono } from "hono";
import { sessionMiddleware } from "../middleware/auth.js";
import { sessions } from "../ws/sessions.js";

const goals = new Hono();

goals.use("*", sessionMiddleware);

// submit a goal for a connected device
goals.post("/", async (c) => {
  const user = c.get("user");
  const body = await c.req.json<{ deviceId: string; goal: string }>();

  if (!body.deviceId || !body.goal) {
    return c.json({ error: "deviceId and goal are required" }, 400);
  }

  const device = sessions.getDevice(body.deviceId);
  if (!device) {
    return c.json({ error: "device not connected" }, 404);
  }

  if (device.userId !== user.id) {
    return c.json({ error: "device does not belong to you" }, 403);
  }

  // TODO (Task 6): start agent loop for this device+goal
  // For now, acknowledge the goal
  const sessionId = crypto.randomUUID();

  return c.json({
    sessionId,
    deviceId: body.deviceId,
    goal: body.goal,
    status: "queued",
  });
});

export { goals };
```

**Step 4: Extract health route**

```typescript
// server/src/routes/health.ts

import { Hono } from "hono";
import { sessions } from "../ws/sessions.js";

const health = new Hono();

health.get("/", (c) => {
  return c.json({
    status: "ok",
    connectedDevices: sessions.devices.size,
  });
});

export { health };
```

**Step 5: Mount routes in index.ts**

Add to `server/src/index.ts` after the CORS middleware, replacing the inline health check:

```typescript
import { devices } from "./routes/devices.js";
import { goals } from "./routes/goals.js";
import { health } from "./routes/health.js";

// ... after CORS and auth handler ...

app.route("/devices", devices);
app.route("/goals", goals);
app.route("/health", health);
```

Remove the old inline `app.get("/health", ...)`.

**Step 6: Verify typecheck and test**

```bash
cd server && bun run typecheck
```

**Step 7: Commit**

```bash
git add server/src/routes/ server/src/middleware/ server/src/index.ts
git commit -m "feat: add REST routes for devices, goals, and health"
```

---

## Task 6: Agent Loop (Server-Side)

**Files:**
- Create: `server/src/agent/loop.ts`
- Create: `server/src/agent/llm.ts`
- Create: `server/src/agent/stuck.ts`
- Modify: `server/src/routes/goals.ts` (wire up agent loop)
- Modify: `server/src/ws/device.ts` (handle device-initiated goals)

This is the biggest task. It adapts the existing `src/kernel.ts` logic to work over WebSocket instead of ADB.

**Step 1: Create llm.ts**

Adapt `src/llm-providers.ts` — same LLM provider factory, but reads config from the user's `llm_config` DB row instead of env vars.

```typescript
// server/src/agent/llm.ts

// This file adapts src/llm-providers.ts to work with per-user LLM config.
// The SYSTEM_PROMPT, provider factory, and response parsing all come from
// the existing codebase. Key differences:
//   - Config comes from DB (llm_config table) not env vars
//   - Same LLMProvider interface
//   - Same parseJsonResponse() logic

// Import the SYSTEM_PROMPT and provider logic from existing src/
// OR copy and adapt the relevant portions.
// The exact approach depends on whether we want to share code via
// packages/shared or duplicate for server independence.

// For v1: duplicate the SYSTEM_PROMPT and provider factory here.
// The prompt is ~200 lines and changes rarely. Duplication is acceptable
// for deployment independence (server deploys without src/).

export interface LLMConfig {
  provider: string; // openai | groq | ollama | bedrock | openrouter
  apiKey: string;
  model?: string;
}

export interface LLMProvider {
  getAction(
    systemPrompt: string,
    userPrompt: string,
    imageBase64?: string
  ): Promise<string>;
}

export function getLlmProvider(config: LLMConfig): LLMProvider {
  // Adapt from src/llm-providers.ts
  // Each provider uses config.apiKey and config.model
  // instead of reading from process.env
  throw new Error("TODO: adapt from src/llm-providers.ts");
}

export function parseJsonResponse(raw: string): Record<string, unknown> | null {
  // Same logic as src/llm-providers.ts parseJsonResponse()
  // Handle clean JSON and markdown-wrapped code blocks
  const cleaned = raw.replace(/```(?:json)?\s*/g, "").replace(/```/g, "").trim();
  try {
    return JSON.parse(cleaned);
  } catch {
    return null;
  }
}
```

> **Note for implementer:** Copy the SYSTEM_PROMPT, provider implementations (OpenAI, Groq, etc.), and parseJsonResponse from `src/llm-providers.ts`. Adapt each provider constructor to accept `LLMConfig` instead of reading env vars. The core logic is identical.

**Step 2: Create stuck.ts**

```typescript
// server/src/agent/stuck.ts

// Adapted from kernel.ts stuck-loop detection.
// Same algorithm: track recent actions in a sliding window,
// detect repetition, inject recovery hints.

export interface StuckDetector {
  recordAction(action: string, screenHash: string): void;
  isStuck(): boolean;
  getRecoveryHint(): string;
  reset(): void;
}

export function createStuckDetector(windowSize: number = 5): StuckDetector {
  const recentActions: string[] = [];
  const recentHashes: string[] = [];

  return {
    recordAction(action: string, screenHash: string) {
      recentActions.push(action);
      recentHashes.push(screenHash);
      if (recentActions.length > windowSize) recentActions.shift();
      if (recentHashes.length > windowSize) recentHashes.shift();
    },

    isStuck(): boolean {
      if (recentActions.length < 3) return false;
      // all recent actions are the same
      const allSame = recentActions.every((a) => a === recentActions[0]);
      // all recent screen hashes are the same
      const allSameHash = recentHashes.every((h) => h === recentHashes[0]);
      return allSame || allSameHash;
    },

    getRecoveryHint(): string {
      return (
        "STUCK DETECTED: You have been repeating the same action or seeing the same screen. " +
        "Try a completely different approach: scroll to find new elements, go back, " +
        "use the home button, or try a different app."
      );
    },

    reset() {
      recentActions.length = 0;
      recentHashes.length = 0;
    },
  };
}
```

**Step 3: Create loop.ts (main agent loop)**

```typescript
// server/src/agent/loop.ts

import { sessions } from "../ws/sessions.js";
import { getLlmProvider, parseJsonResponse, type LLMConfig } from "./llm.js";
import { createStuckDetector } from "./stuck.js";
import type { UIElement, ScreenState, ActionDecision } from "@droidclaw/shared";

export interface AgentLoopOptions {
  deviceId: string;
  userId: string;
  goal: string;
  llmConfig: LLMConfig;
  maxSteps?: number;
  onStep?: (step: AgentStep) => void;
  onComplete?: (result: AgentResult) => void;
}

export interface AgentStep {
  stepNumber: number;
  action: ActionDecision;
  reasoning: string;
  screenHash: string;
}

export interface AgentResult {
  success: boolean;
  stepsUsed: number;
  sessionId: string;
}

function computeScreenHash(elements: UIElement[]): string {
  const parts = elements.map(
    (e) => `${e.id}|${e.text}|${e.center[0]},${e.center[1]}|${e.enabled}|${e.checked}`
  );
  return parts.join(";");
}

function actionToCommand(action: ActionDecision): object {
  switch (action.action) {
    case "tap":
      return { type: "tap", x: action.coordinates?.[0], y: action.coordinates?.[1] };
    case "type":
      return { type: "type", text: action.text };
    case "enter":
      return { type: "enter" };
    case "back":
      return { type: "back" };
    case "home":
      return { type: "home" };
    case "swipe":
    case "scroll":
      return { type: "swipe", x1: action.coordinates?.[0], y1: action.coordinates?.[1], x2: 540, y2: 400 };
    case "longpress":
      return { type: "longpress", x: action.coordinates?.[0], y: action.coordinates?.[1] };
    case "launch":
      return { type: "launch", packageName: action.package };
    case "clear":
      return { type: "clear" };
    case "clipboard_set":
      return { type: "clipboard_set", text: action.text };
    case "clipboard_get":
      return { type: "clipboard_get" };
    case "paste":
      return { type: "paste" };
    case "open_url":
      return { type: "open_url", url: action.url };
    case "switch_app":
      return { type: "switch_app", packageName: action.package };
    case "notifications":
      return { type: "notifications" };
    case "keyevent":
      return { type: "keyevent", code: action.code };
    case "open_settings":
      return { type: "open_settings" };
    case "wait":
      return { type: "wait", duration: 2000 };
    case "done":
      return { type: "done" };
    default:
      return { type: action.action };
  }
}

export async function runAgentLoop(options: AgentLoopOptions): Promise<AgentResult> {
  const {
    deviceId,
    userId,
    goal,
    llmConfig,
    maxSteps = 30,
    onStep,
    onComplete,
  } = options;

  const sessionId = crypto.randomUUID();
  const llm = getLlmProvider(llmConfig);
  const stuck = createStuckDetector();
  let lastScreenHash = "";

  // notify dashboard
  sessions.notifyDashboard(userId, {
    type: "goal_started",
    sessionId,
    goal,
    deviceId,
  });

  let stepsUsed = 0;
  let success = false;

  try {
    for (let step = 0; step < maxSteps; step++) {
      stepsUsed = step + 1;

      // 1. Get screen state from device
      const screenResponse = (await sessions.sendCommand(deviceId, {
        type: "get_screen",
      })) as ScreenState & { type: string; requestId: string };

      const elements = screenResponse.elements ?? [];
      const screenHash = computeScreenHash(elements);
      const screenshot = screenResponse.screenshot;

      // 2. Build prompt
      let userPrompt = `GOAL: ${goal}\n\nSTEP: ${step + 1}/${maxSteps}\n\n`;
      userPrompt += `SCREEN ELEMENTS:\n${JSON.stringify(elements, null, 2)}\n\n`;

      if (screenHash === lastScreenHash) {
        userPrompt += "NOTE: Screen has not changed since last action.\n\n";
      }

      if (stuck.isStuck()) {
        userPrompt += stuck.getRecoveryHint() + "\n\n";
      }

      lastScreenHash = screenHash;

      // 3. Call LLM
      // TODO: use the actual SYSTEM_PROMPT from llm.ts once adapted
      const rawResponse = await llm.getAction(
        "You are a phone automation agent...", // placeholder
        userPrompt,
        elements.length < 3 ? screenshot : undefined
      );

      // 4. Parse response
      const parsed = parseJsonResponse(rawResponse);
      if (!parsed || !parsed.action) {
        stuck.recordAction("parse_error", screenHash);
        continue;
      }

      const action = parsed as unknown as ActionDecision;
      stuck.recordAction(action.action, screenHash);

      // 5. Check for "done"
      if (action.action === "done") {
        success = true;
        break;
      }

      // 6. Report step to dashboard
      const stepData: AgentStep = {
        stepNumber: step + 1,
        action,
        reasoning: action.reason ?? "",
        screenHash,
      };
      onStep?.(stepData);
      sessions.notifyDashboard(userId, {
        type: "step",
        sessionId,
        step: step + 1,
        action,
        reasoning: action.reason ?? "",
        screenHash,
      });

      // 7. Execute action on device
      const command = actionToCommand(action);
      await sessions.sendCommand(deviceId, command);

      // 8. Brief pause between steps
      await new Promise((r) => setTimeout(r, 500));
    }
  } catch (error) {
    console.error(`Agent loop error: ${error}`);
  }

  const result: AgentResult = { success, stepsUsed, sessionId };

  // notify dashboard
  sessions.notifyDashboard(userId, {
    type: "goal_completed",
    sessionId,
    success,
    stepsUsed,
  });

  onComplete?.(result);
  return result;
}
```

**Step 4: Wire up agent loop in goals route**

Update `server/src/routes/goals.ts` to start the agent loop:

```typescript
// Replace the TODO in goals.ts POST handler:

import { runAgentLoop } from "../agent/loop.js";
import { db } from "../db.js";

// Inside the POST handler, after validation:

// fetch user's LLM config from DB
// TODO: query llm_config table for this user
// For now, return error if not configured
const llmConfig = { provider: "groq", apiKey: "TODO", model: "TODO" };

const sessionId = crypto.randomUUID();

// start agent loop in background (don't await — it runs async)
runAgentLoop({
  deviceId: body.deviceId,
  userId: user.id,
  goal: body.goal,
  llmConfig,
}).catch((err) => console.error("Agent loop failed:", err));

return c.json({
  sessionId,
  deviceId: body.deviceId,
  goal: body.goal,
  status: "running",
});
```

**Step 5: Verify typecheck**

```bash
cd server && bun run typecheck
```

**Step 6: Commit**

```bash
git add server/src/agent/
git commit -m "feat: add agent loop with LLM integration and stuck detection"
```

---

## Task 7: Switch SvelteKit to Node Adapter

**Files:**
- Modify: `web/package.json` (swap adapter)
- Modify: `web/svelte.config.js` (use node adapter)

**Step 1: Install node adapter, remove cloudflare adapter**

```bash
cd web && bun remove @sveltejs/adapter-cloudflare && bun add -D @sveltejs/adapter-node
```

**Step 2: Update svelte.config.js**

```javascript
import adapter from '@sveltejs/adapter-node';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  preprocess: vitePreprocess(),
  kit: {
    experimental: {
      remoteFunctions: true
    },
    adapter: adapter(),
    alias: {
      '@/*': './src/lib/*'
    }
  },
  compilerOptions: {
    experimental: {
      async: true
    }
  }
};

export default config;
```

**Step 3: Verify build**

```bash
cd web && bun run build
```

**Step 4: Commit**

```bash
git add web/package.json web/svelte.config.js web/bun.lock
git commit -m "feat: switch SvelteKit from Cloudflare to node adapter"
```

---

## Task 8: Dashboard Layout & Navigation

**Files:**
- Modify: `web/src/routes/+layout.svelte` (add nav)
- Create: `web/src/routes/+layout.server.ts` (load session)
- Modify: `web/src/routes/+page.svelte` (redirect logic)
- Create: `web/src/routes/dashboard/+layout.svelte` (dashboard shell)
- Create: `web/src/routes/dashboard/+layout.server.ts` (auth guard)
- Create: `web/src/routes/dashboard/+page.svelte` (overview)

**Step 1: Create root layout.server.ts**

```typescript
// web/src/routes/+layout.server.ts

import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => {
  return {
    user: locals.user ?? null
  };
};
```

**Step 2: Update root +page.svelte (redirect)**

```svelte
<!-- web/src/routes/+page.svelte -->
<script lang="ts">
  import { redirect } from '@sveltejs/kit';
  import { getUser } from '$lib/api/auth.remote';

  // redirect based on auth state
  try {
    await getUser();
    redirect(307, '/dashboard');
  } catch {
    redirect(307, '/login');
  }
</script>
```

**Step 3: Create dashboard layout.server.ts (auth guard)**

```typescript
// web/src/routes/dashboard/+layout.server.ts

import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => {
  if (!locals.user) {
    redirect(307, '/login');
  }

  return {
    user: locals.user
  };
};
```

**Step 4: Create dashboard +layout.svelte**

```svelte
<!-- web/src/routes/dashboard/+layout.svelte -->
<script lang="ts">
  import { signout } from '$lib/api/auth.remote';

  let { children, data } = $props();
</script>

<div class="flex min-h-screen">
  <aside class="w-64 border-r border-neutral-200 bg-neutral-50 p-6">
    <h1 class="mb-8 text-xl font-bold">DroidClaw</h1>
    <nav class="flex flex-col gap-2">
      <a href="/dashboard" class="rounded px-3 py-2 hover:bg-neutral-200">Overview</a>
      <a href="/dashboard/devices" class="rounded px-3 py-2 hover:bg-neutral-200">Devices</a>
      <a href="/dashboard/api-keys" class="rounded px-3 py-2 hover:bg-neutral-200">API Keys</a>
      <a href="/dashboard/settings" class="rounded px-3 py-2 hover:bg-neutral-200">Settings</a>
    </nav>
    <div class="mt-auto pt-8">
      <p class="mb-2 text-sm text-neutral-500">{data.user.email}</p>
      <form {...signout}>
        <button type="submit" class="text-sm text-neutral-500 hover:text-neutral-800">
          Sign out
        </button>
      </form>
    </div>
  </aside>

  <main class="flex-1 p-8">
    {@render children?.()}
  </main>
</div>
```

**Step 5: Create dashboard overview page**

```svelte
<!-- web/src/routes/dashboard/+page.svelte -->
<script lang="ts">
  let { data } = $props();
</script>

<h2 class="mb-6 text-2xl font-bold">Dashboard</h2>
<p class="text-neutral-600">Welcome back, {data.user.name}.</p>

<div class="mt-8 grid grid-cols-3 gap-6">
  <a
    href="/dashboard/devices"
    class="rounded-lg border border-neutral-200 p-6 hover:border-neutral-400"
  >
    <h3 class="font-semibold">Devices</h3>
    <p class="mt-1 text-sm text-neutral-500">Manage connected phones</p>
  </a>
  <a
    href="/dashboard/api-keys"
    class="rounded-lg border border-neutral-200 p-6 hover:border-neutral-400"
  >
    <h3 class="font-semibold">API Keys</h3>
    <p class="mt-1 text-sm text-neutral-500">Create keys for your devices</p>
  </a>
  <a
    href="/dashboard/settings"
    class="rounded-lg border border-neutral-200 p-6 hover:border-neutral-400"
  >
    <h3 class="font-semibold">Settings</h3>
    <p class="mt-1 text-sm text-neutral-500">Configure LLM provider</p>
  </a>
</div>
```

**Step 6: Verify dev server**

```bash
cd web && bun run dev
```

Navigate to `http://localhost:5173` — should redirect to `/login` or `/dashboard`.

**Step 7: Commit**

```bash
git add web/src/routes/
git commit -m "feat: add dashboard layout with navigation and auth guard"
```

---

## Task 9: API Keys Page

**Files:**
- Create: `web/src/lib/api/api-keys.remote.ts`
- Create: `web/src/lib/schema/api-keys.ts`
- Create: `web/src/routes/dashboard/api-keys/+page.svelte`

**Step 1: Create Valibot schema**

```typescript
// web/src/lib/schema/api-keys.ts

import { object, string, pipe, minLength } from 'valibot';

export const createKeySchema = object({
  name: pipe(string(), minLength(1))
});
```

**Step 2: Create remote functions**

```typescript
// web/src/lib/api/api-keys.remote.ts

import { form, query, getRequestEvent } from '$app/server';
import { auth } from '$lib/server/auth';
import { createKeySchema } from '$lib/schema/api-keys';

export const listKeys = query(async () => {
  const { locals } = getRequestEvent();
  if (!locals.user) return [];

  const keys = await auth.api.listApiKeys({
    headers: getRequestEvent().request.headers
  });

  return keys ?? [];
});

export const createKey = form(createKeySchema, async (data) => {
  const { request } = getRequestEvent();

  const result = await auth.api.createApiKey({
    body: {
      name: data.name,
      prefix: 'dc',
      expiresIn: undefined, // no expiry by default
      remaining: undefined  // unlimited
    },
    headers: request.headers
  });

  return result;
});

export const deleteKey = form(async (formData: FormData) => {
  const { request } = getRequestEvent();
  const keyId = formData.get('keyId') as string;

  await auth.api.deleteApiKey({
    body: { keyId },
    headers: request.headers
  });
});
```

**Step 3: Create API Keys page**

```svelte
<!-- web/src/routes/dashboard/api-keys/+page.svelte -->
<script lang="ts">
  import { listKeys, createKey, deleteKey } from '$lib/api/api-keys.remote';

  const keys = await listKeys();

  let newKeyValue = $state<string | null>(null);
</script>

<h2 class="mb-6 text-2xl font-bold">API Keys</h2>

<div class="mb-8 max-w-lg">
  <h3 class="mb-3 text-lg font-semibold">Create New Key</h3>
  <form
    {...createKey}
    onsubmit={(e) => {
      // capture the returned key value after submission
    }}
    class="flex gap-3"
  >
    <input
      {...createKey.fields.name.as('text')}
      placeholder="Key name (e.g., Pixel 8)"
      class="flex-1 rounded border border-neutral-300 px-3 py-2"
    />
    {#each createKey.fields.name.issues() ?? [] as issue (issue.message)}
      <p class="text-sm text-red-500">{issue.message}</p>
    {/each}
    <button type="submit" class="rounded bg-neutral-900 px-4 py-2 text-white hover:bg-neutral-700">
      Create
    </button>
  </form>

  {#if newKeyValue}
    <div class="mt-4 rounded border border-yellow-300 bg-yellow-50 p-4">
      <p class="mb-2 text-sm font-semibold">Copy your API key now. It won't be shown again.</p>
      <code class="block break-all rounded bg-neutral-100 p-2 text-sm">{newKeyValue}</code>
    </div>
  {/if}
</div>

<div>
  <h3 class="mb-3 text-lg font-semibold">Your Keys</h3>
  {#if keys.length === 0}
    <p class="text-neutral-500">No API keys yet. Create one to connect your Android device.</p>
  {:else}
    <div class="space-y-3">
      {#each keys as key (key.id)}
        <div class="flex items-center justify-between rounded border border-neutral-200 p-4">
          <div>
            <p class="font-medium">{key.name ?? 'Unnamed Key'}</p>
            <p class="text-sm text-neutral-500">
              {key.prefix}_{'*'.repeat(20)} &middot; Created {new Date(key.createdAt).toLocaleDateString()}
            </p>
          </div>
          <form {...deleteKey}>
            <input type="hidden" name="keyId" value={key.id} />
            <button type="submit" class="text-sm text-red-500 hover:text-red-700">Delete</button>
          </form>
        </div>
      {/each}
    </div>
  {/if}
</div>
```

> **Note for implementer:** The `createKey` remote function returns the full key value only on creation. The `listKeys` response only shows the prefix (hashed key). The page needs to capture the creation response to show the full key once. The exact mechanism depends on how remote functions return data in Svelte 5 async mode — check the existing `auth.remote.ts` pattern and adapt. You may need to use `$effect` or a callback to capture the created key value.

**Step 4: Verify dev server**

```bash
cd web && bun run dev
```

Navigate to `/dashboard/api-keys`.

**Step 5: Commit**

```bash
git add web/src/lib/api/api-keys.remote.ts web/src/lib/schema/api-keys.ts web/src/routes/dashboard/api-keys/
git commit -m "feat: add API keys management page"
```

---

## Task 10: Settings Page (LLM Config)

**Files:**
- Create: `web/src/lib/api/settings.remote.ts`
- Create: `web/src/lib/schema/settings.ts`
- Create: `web/src/routes/dashboard/settings/+page.svelte`

**Step 1: Create Valibot schema**

```typescript
// web/src/lib/schema/settings.ts

import { object, string, pipe, minLength, optional } from 'valibot';

export const llmConfigSchema = object({
  provider: pipe(string(), minLength(1)),
  apiKey: pipe(string(), minLength(1)),
  model: optional(string())
});
```

**Step 2: Create remote functions**

```typescript
// web/src/lib/api/settings.remote.ts

import { form, query, getRequestEvent } from '$app/server';
import { db } from '$lib/server/db';
import { llmConfig } from '$lib/server/db/schema';
import { eq } from 'drizzle-orm';
import { llmConfigSchema } from '$lib/schema/settings';

export const getConfig = query(async () => {
  const { locals } = getRequestEvent();
  if (!locals.user) return null;

  const config = await db
    .select()
    .from(llmConfig)
    .where(eq(llmConfig.userId, locals.user.id))
    .limit(1);

  if (config.length === 0) return null;

  // mask the API key for display
  return {
    ...config[0],
    apiKey: config[0].apiKey.slice(0, 8) + '...' + config[0].apiKey.slice(-4)
  };
});

export const updateConfig = form(llmConfigSchema, async (data) => {
  const { locals } = getRequestEvent();
  if (!locals.user) return;

  const existing = await db
    .select()
    .from(llmConfig)
    .where(eq(llmConfig.userId, locals.user.id))
    .limit(1);

  if (existing.length > 0) {
    await db
      .update(llmConfig)
      .set({
        provider: data.provider,
        apiKey: data.apiKey,
        model: data.model ?? null
      })
      .where(eq(llmConfig.userId, locals.user.id));
  } else {
    await db.insert(llmConfig).values({
      id: crypto.randomUUID(),
      userId: locals.user.id,
      provider: data.provider,
      apiKey: data.apiKey,
      model: data.model ?? null
    });
  }
});
```

**Step 3: Create Settings page**

```svelte
<!-- web/src/routes/dashboard/settings/+page.svelte -->
<script lang="ts">
  import { getConfig, updateConfig } from '$lib/api/settings.remote';

  const config = await getConfig();
</script>

<h2 class="mb-6 text-2xl font-bold">Settings</h2>

<div class="max-w-lg">
  <h3 class="mb-4 text-lg font-semibold">LLM Provider</h3>

  <form {...updateConfig} class="space-y-4">
    <label class="block">
      <span class="text-sm font-medium">Provider</span>
      <select
        {...updateConfig.fields.provider.as('text')}
        class="mt-1 block w-full rounded border border-neutral-300 px-3 py-2"
      >
        <option value="openai">OpenAI</option>
        <option value="groq">Groq</option>
        <option value="ollama">Ollama</option>
        <option value="bedrock">AWS Bedrock</option>
        <option value="openrouter">OpenRouter</option>
      </select>
      {#each updateConfig.fields.provider.issues() ?? [] as issue (issue.message)}
        <p class="text-sm text-red-500">{issue.message}</p>
      {/each}
    </label>

    <label class="block">
      <span class="text-sm font-medium">API Key</span>
      <input
        {...updateConfig.fields.apiKey.as('password')}
        placeholder={config?.apiKey ?? 'Enter your API key'}
        class="mt-1 block w-full rounded border border-neutral-300 px-3 py-2"
      />
      {#each updateConfig.fields.apiKey.issues() ?? [] as issue (issue.message)}
        <p class="text-sm text-red-500">{issue.message}</p>
      {/each}
    </label>

    <label class="block">
      <span class="text-sm font-medium">Model (optional)</span>
      <input
        {...updateConfig.fields.model.as('text')}
        placeholder="e.g., gpt-4o, llama-3.3-70b-versatile"
        class="mt-1 block w-full rounded border border-neutral-300 px-3 py-2"
      />
    </label>

    <button type="submit" class="rounded bg-neutral-900 px-4 py-2 text-white hover:bg-neutral-700">
      Save
    </button>
  </form>

  {#if config}
    <p class="mt-4 text-sm text-neutral-500">
      Current: {config.provider} &middot; Key: {config.apiKey}
      {#if config.model}&middot; Model: {config.model}{/if}
    </p>
  {/if}
</div>
```

**Step 4: Verify dev server**

```bash
cd web && bun run dev
```

Navigate to `/dashboard/settings`.

**Step 5: Commit**

```bash
git add web/src/lib/api/settings.remote.ts web/src/lib/schema/settings.ts web/src/routes/dashboard/settings/
git commit -m "feat: add LLM provider settings page"
```

---

## Task 11: Devices Page

**Files:**
- Create: `web/src/lib/api/devices.remote.ts`
- Create: `web/src/routes/dashboard/devices/+page.svelte`
- Create: `web/src/routes/dashboard/devices/[deviceId]/+page.svelte`

**Step 1: Create remote functions**

```typescript
// web/src/lib/api/devices.remote.ts

import { query, getRequestEvent } from '$app/server';
import { env } from '$env/dynamic/private';

const SERVER_URL = env.SERVER_URL || 'http://localhost:8080';

export const listDevices = query(async () => {
  const { request } = getRequestEvent();

  const res = await fetch(`${SERVER_URL}/devices`, {
    headers: {
      cookie: request.headers.get('cookie') ?? ''
    }
  });

  if (!res.ok) return [];
  return res.json();
});
```

> **Note for implementer:** The dashboard calls the Hono server's `/devices` endpoint. In production on Railway, `SERVER_URL` points to the Hono server's internal URL. The session cookie is forwarded so Hono can verify the user. You may need to adjust the Hono session middleware to accept forwarded cookies from SvelteKit.

**Step 2: Create devices list page**

```svelte
<!-- web/src/routes/dashboard/devices/+page.svelte -->
<script lang="ts">
  import { listDevices } from '$lib/api/devices.remote';

  const devices = await listDevices();
</script>

<h2 class="mb-6 text-2xl font-bold">Devices</h2>

{#if devices.length === 0}
  <div class="rounded border border-neutral-200 p-8 text-center">
    <p class="text-neutral-500">No devices connected.</p>
    <p class="mt-2 text-sm text-neutral-400">
      Install the Android app, paste your API key, and your device will appear here.
    </p>
    <a href="/dashboard/api-keys" class="mt-4 inline-block text-sm text-blue-600 hover:underline">
      Create an API key
    </a>
  </div>
{:else}
  <div class="space-y-3">
    {#each devices as device (device.deviceId)}
      <a
        href="/dashboard/devices/{device.deviceId}"
        class="flex items-center justify-between rounded border border-neutral-200 p-4 hover:border-neutral-400"
      >
        <div>
          <p class="font-medium">{device.name}</p>
          <p class="text-sm text-neutral-500">
            Connected {new Date(device.connectedAt).toLocaleString()}
          </p>
        </div>
        <span class="inline-block h-2 w-2 rounded-full bg-green-500"></span>
      </a>
    {/each}
  </div>
{/if}
```

**Step 3: Create device detail page (goal input + live logs)**

```svelte
<!-- web/src/routes/dashboard/devices/[deviceId]/+page.svelte -->
<script lang="ts">
  import { page } from '$app/state';
  import { env } from '$env/dynamic/public';

  const deviceId = page.params.deviceId;
  const SERVER_WS_URL = env.PUBLIC_SERVER_WS_URL || 'ws://localhost:8080';

  let goal = $state('');
  let steps = $state<Array<{ step: number; action: string; reasoning: string }>>([]);
  let status = $state<'idle' | 'running' | 'completed' | 'failed'>('idle');

  async function submitGoal() {
    if (!goal.trim()) return;
    status = 'running';
    steps = [];

    const res = await fetch(`/api/goals`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId, goal })
    });

    if (!res.ok) {
      status = 'failed';
      return;
    }

    // connect to dashboard WebSocket for live step updates
    // TODO: implement WebSocket connection to Hono server
    // The dashboard WebSocket handler will stream steps for this session
  }
</script>

<h2 class="mb-6 text-2xl font-bold">Device: {deviceId.slice(0, 8)}...</h2>

<div class="max-w-2xl">
  <div class="mb-8">
    <label class="mb-2 block text-sm font-medium">Goal</label>
    <div class="flex gap-3">
      <input
        type="text"
        bind:value={goal}
        placeholder="e.g., Open YouTube and search for lofi beats"
        class="flex-1 rounded border border-neutral-300 px-3 py-2"
        disabled={status === 'running'}
      />
      <button
        onclick={submitGoal}
        disabled={status === 'running'}
        class="rounded bg-neutral-900 px-4 py-2 text-white hover:bg-neutral-700 disabled:opacity-50"
      >
        {status === 'running' ? 'Running...' : 'Run'}
      </button>
    </div>
  </div>

  {#if steps.length > 0}
    <h3 class="mb-3 text-lg font-semibold">Steps</h3>
    <div class="space-y-2">
      {#each steps as step (step.step)}
        <div class="rounded border border-neutral-200 p-3">
          <p class="text-sm font-medium">Step {step.step}: {step.action}</p>
          <p class="text-sm text-neutral-500">{step.reasoning}</p>
        </div>
      {/each}
    </div>
  {/if}

  {#if status === 'completed'}
    <p class="mt-4 text-green-600">Goal completed successfully.</p>
  {:else if status === 'failed'}
    <p class="mt-4 text-red-600">Goal failed.</p>
  {/if}
</div>
```

> **Note for implementer:** The device detail page needs a SvelteKit API route (`/api/goals`) that proxies to the Hono server, or it can call the Hono server directly via `PUBLIC_SERVER_URL`. The live step stream requires a WebSocket connection from the browser to Hono's `/ws/dashboard` endpoint. Implement the WebSocket connection in a `$effect` block that connects when the page mounts and disconnects on unmount.

**Step 4: Add `SERVER_URL` and `PUBLIC_SERVER_WS_URL` to web/.env.example**

Append to `web/.env.example`:

```
SERVER_URL="http://localhost:8080"
PUBLIC_SERVER_WS_URL="ws://localhost:8080"
```

**Step 5: Commit**

```bash
git add web/src/lib/api/devices.remote.ts web/src/routes/dashboard/devices/ web/.env.example
git commit -m "feat: add devices page with goal input and step log"
```

---

## Task 12: Wire Up Goal Proxy API Route

**Files:**
- Create: `web/src/routes/api/goals/+server.ts`

The device detail page needs to POST goals to the Hono server. Create a SvelteKit API route that proxies the request.

**Step 1: Create the API route**

```typescript
// web/src/routes/api/goals/+server.ts

import { json, error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import type { RequestHandler } from './$types';

const SERVER_URL = env.SERVER_URL || 'http://localhost:8080';

export const POST: RequestHandler = async ({ request, locals }) => {
  if (!locals.user) {
    return error(401, 'Unauthorized');
  }

  const body = await request.json();

  const res = await fetch(`${SERVER_URL}/goals`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      cookie: request.headers.get('cookie') ?? ''
    },
    body: JSON.stringify(body)
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: 'Unknown error' }));
    return error(res.status, err.error ?? 'Failed to submit goal');
  }

  return json(await res.json());
};
```

**Step 2: Commit**

```bash
git add web/src/routes/api/goals/
git commit -m "feat: add goal proxy API route"
```

---

## Task 13: Environment & Dockerfiles

**Files:**
- Create: `web/Dockerfile`
- Modify: `server/.env.example` (finalize)
- Modify: `web/.env.example` (finalize)

**Step 1: Create web Dockerfile**

```dockerfile
FROM oven/bun:1 AS builder

WORKDIR /app
COPY web/package.json web/bun.lock ./
RUN bun install --frozen-lockfile

COPY web/ .
RUN bun run build

FROM oven/bun:1

WORKDIR /app
COPY --from=builder /app/build ./build
COPY --from=builder /app/package.json .
COPY --from=builder /app/node_modules ./node_modules

EXPOSE 3000
ENV PORT=3000
CMD ["bun", "build/index.js"]
```

**Step 2: Finalize server .env.example**

```
DATABASE_URL="postgres://user:password@host:port/db-name"
PORT=8080
CORS_ORIGIN="http://localhost:5173"
```

**Step 3: Finalize web .env.example**

```
DATABASE_URL="postgres://user:password@host:port/db-name"
SERVER_URL="http://localhost:8080"
PUBLIC_SERVER_WS_URL="ws://localhost:8080"
```

**Step 4: Commit**

```bash
git add web/Dockerfile server/.env.example web/.env.example
git commit -m "feat: add Dockerfiles and finalize env examples"
```

---

## Task 14: Integration Smoke Test

**No new files. Manual verification.**

**Step 1: Start Postgres (local or Railway)**

Ensure `DATABASE_URL` is set in both `web/.env` and `server/.env`.

**Step 2: Run migrations**

```bash
cd web && bun run db:push
```

**Step 3: Start both servers**

```bash
# terminal 1
cd web && bun run dev

# terminal 2
cd server && bun run dev
```

**Step 4: Manual test flow**

1. Open `http://localhost:5173` -> redirects to `/login`
2. Sign up with email/password
3. Redirected to `/dashboard`
4. Go to `/dashboard/api-keys` -> create a key named "Test Device"
5. Copy the key
6. Go to `/dashboard/settings` -> set provider to "groq", enter API key, model
7. Go to `/dashboard/devices` -> shows "No devices connected"
8. Test Hono health: `curl http://localhost:8080/health` -> `{"status":"ok","connectedDevices":0}`
9. Test WebSocket auth (using wscat or similar):
   ```bash
   wscat -c ws://localhost:8080/ws/device
   # send: {"type":"auth","apiKey":"dc_your_key_here"}
   # expect: {"type":"auth_ok","deviceId":"..."}
   ```
10. Dashboard devices page should now show the connected device

**Step 5: Commit any fixes from smoke test**

```bash
git add -A && git commit -m "fix: address issues found during integration smoke test"
```

---

## Future: Android App (Task 15+)

Not building now. The server is ready for Android connections via:
- `ws://server:8080/ws/device` — WebSocket endpoint
- API key auth on handshake
- Full command protocol defined in `@droidclaw/shared`

When building the Android app, follow the structure in `OPTION1-IMPLEMENTATION.md` and the `android/` plan in the design doc. The Kotlin data classes should mirror `@droidclaw/shared` types.
