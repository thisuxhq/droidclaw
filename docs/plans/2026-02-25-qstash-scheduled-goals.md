# QStash Scheduled Goals Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add time-delayed goal scheduling via Upstash QStash so users can say "send email after 5 minutes" and the system schedules + executes it automatically.

**Architecture:** The LLM classifier detects time expressions and returns `{type:"scheduled", delay, goal}`. Server stores the scheduled goal in DB, publishes to QStash with a delay, and QStash hits a callback endpoint when it's time. The web dashboard shows scheduled goals inline with a clock icon + tooltip.

**Tech Stack:** `@upstash/qstash` SDK, Drizzle ORM (PostgreSQL), Hono routes, SvelteKit (Svelte 5)

**Design doc:** `docs/plans/2026-02-25-qstash-scheduled-goals-design.md`

---

### Task 1: Install QStash SDK and add env vars

**Files:**
- Modify: `server/package.json`
- Modify: `server/src/env.ts`
- Modify: `server/.env.example`

**Step 1: Install @upstash/qstash**

```bash
cd server && bun add @upstash/qstash
```

**Step 2: Add QStash env vars to `server/src/env.ts`**

Add three new fields to the env object:

```typescript
// In server/src/env.ts, add to the env object:
QSTASH_TOKEN: process.env.QSTASH_TOKEN || "",
QSTASH_CURRENT_SIGNING_KEY: process.env.QSTASH_CURRENT_SIGNING_KEY || "",
QSTASH_NEXT_SIGNING_KEY: process.env.QSTASH_NEXT_SIGNING_KEY || "",
SERVER_PUBLIC_URL: process.env.SERVER_PUBLIC_URL || "",
```

No validation throw — QStash is optional (graceful degradation if not configured).

**Step 3: Update `server/.env.example`**

Add the new vars with comments:

```
# QStash (optional, for scheduled goals)
QSTASH_TOKEN=""
QSTASH_CURRENT_SIGNING_KEY=""
QSTASH_NEXT_SIGNING_KEY=""
SERVER_PUBLIC_URL=""          # Public URL of this server (e.g. https://api.droidclaw.ai) — QStash needs to reach it
```

**Step 4: Commit**

```bash
git add server/package.json server/bun.lock server/src/env.ts server/.env.example
git commit -m "chore(server): add @upstash/qstash dependency and env vars"
```

---

### Task 2: Add scheduled columns to agentSession schema

**Files:**
- Modify: `server/src/schema.ts` (lines 129-142)
- Modify: `packages/shared/src/types.ts` (line 55-62) — add `scheduled` to PipelineResult
- Modify: `web/src/lib/server/db/schema.ts` — mirror the same columns

**Step 1: Add columns to server schema**

In `server/src/schema.ts`, add three nullable columns to `agentSession` (after line 141, before the closing `}`):

```typescript
export const agentSession = pgTable("agent_session", {
  id: text("id").primaryKey(),
  userId: text("user_id")
    .notNull()
    .references(() => user.id, { onDelete: "cascade" }),
  deviceId: text("device_id")
    .notNull()
    .references(() => device.id, { onDelete: "cascade" }),
  goal: text("goal").notNull(),
  status: text("status").notNull().default("running"),
  stepsUsed: integer("steps_used").default(0),
  startedAt: timestamp("started_at").defaultNow().notNull(),
  completedAt: timestamp("completed_at"),
  // Scheduling (QStash)
  qstashMessageId: text("qstash_message_id"),
  scheduledFor: timestamp("scheduled_for"),
  scheduledDelay: integer("scheduled_delay"),
});
```

**Step 2: Mirror in web schema**

Check `web/src/lib/server/db/schema.ts` for the `agentSession` table and add the same three columns there.

**Step 3: Add `scheduled` type to PipelineResult**

In `packages/shared/src/types.ts`, add to the `PipelineResult` union (after the `done` variant):

```typescript
export type PipelineResult =
  | { stage: "parser" | "classifier"; type: "intent"; intent: IntentCommand }
  | { stage: "parser" | "classifier"; type: "launch"; packageName: string }
  | { stage: "parser"; type: "open_url"; url: string }
  | { stage: "parser"; type: "open_settings"; setting: string }
  | { stage: "classifier"; type: "ui"; app: string; subGoal: string }
  | { stage: "parser" | "classifier"; type: "done"; reason: string }
  | { stage: "classifier"; type: "scheduled"; delay: number; goal: string }
  | { stage: "parser" | "classifier"; type: "passthrough" };
```

**Step 4: Run the DB migration**

Generate and run a Drizzle migration to add the new columns:

```bash
cd server && bun drizzle-kit generate && bun drizzle-kit migrate
```

If DroidClaw doesn't use Drizzle migrations (check for a `drizzle/` folder or `push` workflow), use:

```bash
cd server && bun drizzle-kit push
```

**Step 5: Commit**

```bash
git add server/src/schema.ts packages/shared/src/types.ts web/src/lib/server/db/schema.ts
git commit -m "feat(server): add scheduled goal columns to agentSession schema"
```

---

### Task 3: Create QStash client module

**Files:**
- Create: `server/src/qstash.ts`

**Step 1: Create the QStash client wrapper**

```typescript
import { Client } from "@upstash/qstash";
import { env } from "./env.js";

let _client: Client | null = null;

/** Returns QStash client, or null if not configured */
export function getQStashClient(): Client | null {
  if (!env.QSTASH_TOKEN) return null;
  if (!_client) {
    _client = new Client({ token: env.QSTASH_TOKEN });
  }
  return _client;
}

/** Returns the public callback URL for scheduled goal execution */
export function getCallbackUrl(): string {
  const base = env.SERVER_PUBLIC_URL;
  if (!base) throw new Error("SERVER_PUBLIC_URL is required for scheduled goals");
  return `${base}/goals/execute`;
}
```

**Step 2: Commit**

```bash
git add server/src/qstash.ts
git commit -m "feat(server): add QStash client module"
```

---

### Task 4: Extend classifier to detect scheduled goals

**Files:**
- Modify: `server/src/agent/llm.ts` (lines 221-247) — `getClassifierPrompt()`
- Modify: `server/src/agent/classifier.ts` (lines 63-104) — handle `scheduled` type

**Step 1: Add Option D to classifier prompt**

In `server/src/agent/llm.ts`, in the `getClassifierPrompt()` function, add Option D to the `system` string (after Option C):

```typescript
Option D — SCHEDULED: The goal contains a time delay or future time reference (e.g. "after 5 minutes", "tomorrow at 9am", "in 2 hours").
Return: {"type":"scheduled","delay":<seconds from now>,"goal":"<the goal with the time reference removed>"}
Examples:
- "send email after 5 minutes" → {"type":"scheduled","delay":300,"goal":"send email"}
- "open YouTube in 2 hours" → {"type":"scheduled","delay":7200,"goal":"open YouTube"}
- "remind me to call at 3pm" → {"type":"scheduled","delay":<seconds until 3pm>,"goal":"remind me to call"}
If the goal has NO time reference, do NOT return scheduled — use Option A, B, or C instead.
```

**Step 2: Handle `scheduled` type in classifier.ts**

In `server/src/agent/classifier.ts`, add a new case in the switch statement (after the `done` case, around line 99):

```typescript
case "scheduled": {
  const delay = typeof parsed.delay === "number" ? parsed.delay : parseInt(String(parsed.delay));
  const cleanGoal = typeof parsed.goal === "string" ? parsed.goal : goal;
  if (!delay || delay <= 0) {
    console.warn("[Classifier] Scheduled result with invalid delay, falling through");
    return { stage: "classifier", type: "passthrough" };
  }
  return { stage: "classifier", type: "scheduled", delay, goal: cleanGoal };
}
```

**Step 3: Commit**

```bash
git add server/src/agent/llm.ts server/src/agent/classifier.ts
git commit -m "feat(server): extend classifier to detect scheduled goals"
```

---

### Task 5: Handle scheduled goals in the pipeline

**Files:**
- Modify: `server/src/agent/pipeline.ts` (lines 209-250) — after classifier, before UI agent

**Step 1: Add QStash scheduling to pipeline**

In `server/src/agent/pipeline.ts`, after the classifier stage (line 211 — `console.log(Stage 2...)`), add a new block that handles the `scheduled` type. Insert this BEFORE the existing `if (classResult.type === "done")` check:

```typescript
// After: console.log(`[Pipeline] Stage 2 (Classifier): ${classResult.type}`);

if (classResult.type === "scheduled") {
  const { getQStashClient, getCallbackUrl } = await import("../qstash.js");
  const qstash = getQStashClient();

  if (!qstash) {
    console.warn("[Pipeline] QStash not configured, executing goal immediately");
    // Fall through to normal pipeline by treating as passthrough
  } else {
    // Persist the scheduled session in DB
    const sessionId = crypto.randomUUID();
    const scheduledFor = new Date(Date.now() + classResult.delay * 1000);

    if (persistentDeviceId) {
      await db.insert(agentSession).values({
        id: sessionId,
        userId,
        deviceId: persistentDeviceId,
        goal: classResult.goal,
        status: "scheduled",
        stepsUsed: 0,
        qstashMessageId: null, // updated after publish
        scheduledFor,
        scheduledDelay: classResult.delay,
      });
    }

    // Publish to QStash with delay
    const result = await qstash.publishJSON({
      url: getCallbackUrl(),
      body: {
        sessionId,
        deviceId: persistentDeviceId ?? deviceId,
        userId,
        goal: classResult.goal,
      },
      delay: classResult.delay,
    });

    // Store the QStash message ID for cancellation
    if (persistentDeviceId && result.messageId) {
      await db
        .update(agentSession)
        .set({ qstashMessageId: result.messageId })
        .where(eq(agentSession.id, sessionId));
    }

    // Notify dashboard
    sessions.notifyDashboard(userId, {
      type: "goal_scheduled",
      sessionId,
      goal: classResult.goal,
      scheduledFor: scheduledFor.toISOString(),
      delay: classResult.delay,
    });

    onComplete?.({ success: true, stepsUsed: 0, sessionId });
    console.log(`[Pipeline] Goal scheduled via QStash: "${classResult.goal}" in ${classResult.delay}s`);
    return { success: true, stepsUsed: 0, sessionId, resolvedBy: "classifier" };
  }
}
```

Make sure to add the `agentSession` import at the top if not already imported (it's already imported at line 18).

**Step 2: Commit**

```bash
git add server/src/agent/pipeline.ts
git commit -m "feat(server): schedule goals via QStash in pipeline"
```

---

### Task 6: Add QStash callback endpoint and cancel endpoint

**Files:**
- Modify: `server/src/routes/goals.ts` — add `POST /execute` and `DELETE /:id/schedule`
- Modify: `server/src/index.ts` — no change needed (goals route already mounted)

**Step 1: Add the QStash callback endpoint**

In `server/src/routes/goals.ts`, add the `/execute` route. This route does NOT use sessionMiddleware — it uses QStash signature verification instead.

Add at the bottom (before `export { goals }`):

```typescript
import { Receiver } from "@upstash/qstash";
import { env } from "../env.js";
import { db } from "../db.js";
import { agentSession, llmConfig as llmConfigTable } from "../schema.js";
import { eq } from "drizzle-orm";

/**
 * QStash callback — executes a scheduled goal.
 * Auth: QStash signature verification (not session auth).
 */
goals.post("/execute", async (c) => {
  // Verify QStash signature
  if (env.QSTASH_CURRENT_SIGNING_KEY) {
    const receiver = new Receiver({
      currentSigningKey: env.QSTASH_CURRENT_SIGNING_KEY,
      nextSigningKey: env.QSTASH_NEXT_SIGNING_KEY,
    });

    const signature = c.req.header("upstash-signature") ?? "";
    const body = await c.req.text();

    try {
      await receiver.verify({ signature, body });
    } catch {
      return c.json({ error: "Invalid QStash signature" }, 401);
    }

    // Parse the verified body
    const payload = JSON.parse(body) as {
      sessionId: string;
      deviceId: string;
      userId: string;
      goal: string;
    };

    return executeScheduledGoal(c, payload);
  }

  // No signing keys configured — accept as-is (dev mode)
  const payload = await c.req.json<{
    sessionId: string;
    deviceId: string;
    userId: string;
    goal: string;
  }>();

  return executeScheduledGoal(c, payload);
});

async function executeScheduledGoal(
  c: any,
  payload: { sessionId: string; deviceId: string; userId: string; goal: string }
) {
  const { sessionId, deviceId, userId, goal } = payload;

  // Update session status to running
  await db
    .update(agentSession)
    .set({ status: "running", startedAt: new Date() })
    .where(eq(agentSession.id, sessionId));

  // Check device is online
  const device = sessions.getDeviceByPersistentId(deviceId);
  if (!device) {
    await db
      .update(agentSession)
      .set({ status: "failed", completedAt: new Date() })
      .where(eq(agentSession.id, sessionId));

    sessions.notifyDashboard(userId, {
      type: "goal_failed",
      sessionId,
      message: "Device offline when scheduled goal fired",
    });

    // Return 500 so QStash retries
    return c.json({ error: "Device not connected" }, 500);
  }

  // Fetch LLM config
  const configs = await db
    .select()
    .from(llmConfigTable)
    .where(eq(llmConfigTable.userId, userId))
    .limit(1);

  if (configs.length === 0) {
    await db
      .update(agentSession)
      .set({ status: "failed", completedAt: new Date() })
      .where(eq(agentSession.id, sessionId));
    return c.json({ error: "No LLM config" }, 400);
  }

  const cfg = configs[0];
  const llmCfg: LLMConfig = {
    provider: cfg.provider,
    apiKey: cfg.apiKey,
    model: cfg.model ?? undefined,
  };

  const abort = new AbortController();
  const trackingKey = device.persistentDeviceId ?? device.deviceId;

  if (activeSessions.has(trackingKey)) {
    // Device busy — return 500 so QStash retries later
    return c.json({ error: "Device busy" }, 500);
  }

  const sendToDevice = (msg: Record<string, unknown>) => {
    const d = sessions.getDevice(device.deviceId) ?? sessions.getDeviceByPersistentId(device.persistentDeviceId ?? "");
    if (!d) return;
    try { d.ws.send(JSON.stringify(msg)); } catch { /* disconnected */ }
  };

  activeSessions.set(trackingKey, { sessionId, goal, abort });
  sendToDevice({ type: "goal_started", goal });

  // Notify dashboard
  sessions.notifyDashboard(userId, {
    type: "goal_started",
    deviceId: device.persistentDeviceId ?? device.deviceId,
    goal,
    sessionId,
  });

  // Run pipeline async
  runPipeline({
    deviceId: device.deviceId,
    persistentDeviceId: device.persistentDeviceId,
    userId,
    goal,
    llmConfig: llmCfg,
    signal: abort.signal,
    onStep(step) {
      sendToDevice({
        type: "step",
        step: step.stepNumber,
        action: step.action,
        reasoning: step.reasoning,
      });
    },
    onComplete(result) {
      sendToDevice({
        type: "goal_completed",
        success: result.success,
        stepsUsed: result.stepsUsed,
      });
    },
  })
    .then((result) => {
      activeSessions.delete(trackingKey);
      console.log(`[Scheduled] Completed: ${result.success ? "success" : "incomplete"} in ${result.stepsUsed} steps`);
    })
    .catch((err) => {
      activeSessions.delete(trackingKey);
      sendToDevice({ type: "goal_failed", message: String(err) });
      console.error(`[Scheduled] Error: ${err}`);
    });

  return c.json({ status: "started", sessionId });
}
```

**Step 2: Add the cancel endpoint**

Add before `export { goals }`:

```typescript
/**
 * Cancel a scheduled goal.
 * Auth: Session auth (user must own the goal).
 */
goals.delete("/:id/schedule", sessionMiddleware, async (c) => {
  const user = c.get("user");
  const goalId = c.req.param("id");

  // Find the scheduled session
  const rows = await db
    .select()
    .from(agentSession)
    .where(eq(agentSession.id, goalId))
    .limit(1);

  if (rows.length === 0) return c.json({ error: "Session not found" }, 404);
  const sess = rows[0];
  if (sess.userId !== user.id) return c.json({ error: "Not your session" }, 403);
  if (sess.status !== "scheduled") return c.json({ error: "Session is not scheduled" }, 400);

  // Cancel in QStash
  if (sess.qstashMessageId) {
    const { getQStashClient } = await import("../qstash.js");
    const qstash = getQStashClient();
    if (qstash) {
      try {
        await qstash.messages.delete(sess.qstashMessageId);
      } catch (err) {
        console.warn(`[Goals] QStash cancel failed: ${err}`);
      }
    }
  }

  // Update DB
  await db
    .update(agentSession)
    .set({ status: "cancelled", completedAt: new Date() })
    .where(eq(agentSession.id, goalId));

  // Notify dashboard
  sessions.notifyDashboard(user.id, {
    type: "goal_cancelled",
    sessionId: goalId,
  });

  return c.json({ status: "cancelled" });
});
```

Note: The `/execute` route must NOT be behind `sessionMiddleware`. Currently `goals.ts` has `goals.use("*", sessionMiddleware)` at line 11. This will block `/execute`. Change the middleware setup:

Remove `goals.use("*", sessionMiddleware)` at line 11. Instead, add `sessionMiddleware` inline on the routes that need it:

- `goals.post("/", sessionMiddleware, async (c) => {` (line 16)
- `goals.post("/stop", sessionMiddleware, async (c) => {` (line 149)

This matches the project pattern documented in CLAUDE.md: "Don't use wildcard `use("*", sessionMiddleware)` on sub-routers."

**Step 3: Commit**

```bash
git add server/src/routes/goals.ts
git commit -m "feat(server): add QStash callback and cancel endpoints"
```

---

### Task 7: Add cancel command to web API layer

**Files:**
- Modify: `web/src/lib/api/devices.remote.ts` — add `cancelScheduledGoal` command

**Step 1: Add the cancel command**

After `stopGoal` (line 197), add:

```typescript
export const cancelScheduledGoal = command(
  v.object({ sessionId: v.string() }),
  async ({ sessionId }) => {
    const { locals } = getRequestEvent();
    if (!locals.user) throw new Error('unauthorized');

    const res = await fetch(`${SERVER_URL()}/goals/${sessionId}/schedule`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
        'x-internal-secret': INTERNAL_SECRET(),
        'x-internal-user-id': locals.user.id
      }
    });
    const data = await res.json().catch(() => ({ error: 'Unknown error' }));
    if (!res.ok) throw new Error(data.error ?? `Error ${res.status}`);
    return data;
  }
);
```

**Step 2: Commit**

```bash
git add web/src/lib/api/devices.remote.ts
git commit -m "feat(web): add cancelScheduledGoal API command"
```

---

### Task 8: Update dashboard UI for scheduled goals

**Files:**
- Modify: `web/src/routes/dashboard/devices/[deviceId]/+page.svelte`
- Modify: `web/src/lib/components/DeviceCard.svelte`

**Step 1: Handle `goal_scheduled` and `goal_cancelled` WebSocket events**

In `+page.svelte`, in the `onMount` subscription (lines 180-226), add new cases:

```typescript
case 'goal_scheduled': {
  if (msg.deviceId === deviceId || true) {
    // Refresh sessions to show the new scheduled goal
    listDeviceSessions(deviceId).then((s) => {
      sessions = s as Session[];
    });
  }
  break;
}
case 'goal_cancelled': {
  // Refresh sessions
  listDeviceSessions(deviceId).then((s) => {
    sessions = s as Session[];
  });
  break;
}
```

**Step 2: Update Session interface to include scheduling fields**

Update the `Session` interface (line 60-67) to include:

```typescript
interface Session {
  id: string;
  goal: string;
  status: string;
  stepsUsed: number | null;
  startedAt: Date;
  completedAt: Date | null;
  scheduledFor: Date | null;
  scheduledDelay: number | null;
}
```

**Step 3: Add scheduled status badge in sessions list**

In the sessions tab (around lines 469-490), update the status badge to handle `scheduled` and `cancelled`:

Where the current code checks `sess.status === 'completed'` / `'running'` / `'failed'`, add:

```svelte
<!-- Status badge in session list -->
<span
  class="ml-3 flex shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium
    {sess.status === 'completed' ? 'bg-emerald-50 text-emerald-700'
    : sess.status === 'running' ? 'bg-amber-50 text-amber-700'
    : sess.status === 'scheduled' ? 'bg-blue-50 text-blue-700'
    : sess.status === 'cancelled' ? 'bg-stone-50 text-stone-500'
    : 'bg-red-50 text-red-700'}"
>
  <Icon
    icon={sess.status === 'completed' ? 'solar:check-circle-bold-duotone'
      : sess.status === 'running' ? 'solar:refresh-circle-bold-duotone'
      : sess.status === 'scheduled' ? 'solar:clock-circle-bold-duotone'
      : sess.status === 'cancelled' ? 'solar:close-circle-bold-duotone'
      : 'solar:close-circle-bold-duotone'}
    class="h-3.5 w-3.5"
  />
  {sess.status === 'completed' ? 'Success'
    : sess.status === 'running' ? 'Running'
    : sess.status === 'scheduled' ? 'Scheduled'
    : sess.status === 'cancelled' ? 'Cancelled'
    : 'Failed'}
</span>
```

**Step 4: Add tooltip with countdown for scheduled goals**

For scheduled sessions, replace the timestamp line to show countdown. Below the goal text (line 464-467):

```svelte
<p class="mt-0.5 flex items-center gap-1.5 text-xs text-stone-400">
  <Icon icon="solar:clock-circle-bold-duotone" class="h-3.5 w-3.5" />
  {#if sess.status === 'scheduled' && sess.scheduledFor}
    Scheduled for {new Date(sess.scheduledFor).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
  {:else}
    {formatTime(sess.startedAt)} &middot; {sess.stepsUsed} steps
  {/if}
</p>
```

**Step 5: Add cancel button for scheduled goals**

When a session is expanded and its status is `scheduled`, show a cancel button instead of the step list:

```svelte
{#if sess.status === 'scheduled'}
  <div class="flex items-center gap-3 py-2">
    <Icon icon="solar:clock-circle-bold-duotone" class="h-5 w-5 text-blue-500" />
    <div class="flex-1">
      <p class="text-sm font-medium text-stone-700">
        Fires at {sess.scheduledFor ? new Date(sess.scheduledFor).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'unknown'}
      </p>
      <p class="text-xs text-stone-400">
        {sess.scheduledDelay ? `${Math.ceil(sess.scheduledDelay / 60)} min delay` : ''}
      </p>
    </div>
    <button
      onclick={async () => {
        await cancelScheduledGoal({ sessionId: sess.id });
        sessions = (await listDeviceSessions(deviceId)) as Session[];
      }}
      class="rounded-lg bg-red-50 px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-100"
    >
      Cancel
    </button>
  </div>
{:else}
  <!-- existing step list code -->
{/if}
```

Import `cancelScheduledGoal` at the top of the file from `$lib/api/devices.remote`.

**Step 6: Update DeviceCard.svelte for scheduled status**

In `DeviceCard.svelte` (lines 117-137), add the `scheduled` status handling to the lastGoal badge:

```svelte
<span
  class="flex items-center gap-1 text-xs font-medium
    {lastGoal.status === 'completed' ? 'text-emerald-600'
    : lastGoal.status === 'running' ? 'text-amber-600'
    : lastGoal.status === 'scheduled' ? 'text-blue-600'
    : 'text-red-500'}"
>
  <Icon
    icon={lastGoal.status === 'completed' ? 'solar:check-circle-bold-duotone'
      : lastGoal.status === 'running' ? 'solar:refresh-circle-bold-duotone'
      : lastGoal.status === 'scheduled' ? 'solar:clock-circle-bold-duotone'
      : 'solar:close-circle-bold-duotone'}
    class="h-3.5 w-3.5"
  />
  {lastGoal.status === 'completed' ? 'Success'
    : lastGoal.status === 'running' ? 'Running'
    : lastGoal.status === 'scheduled' ? 'Scheduled'
    : 'Failed'}
</span>
```

**Step 7: Commit**

```bash
git add web/src/routes/dashboard/devices/[deviceId]/+page.svelte web/src/lib/components/DeviceCard.svelte
git commit -m "feat(web): add scheduled goal UI with clock icon, tooltip, and cancel"
```

---

### Task 9: Handle scheduled goals from device WebSocket and voice

**Files:**
- Modify: `server/src/ws/device.ts` — handle `goal_scheduled` notification to device

The device WS goal handler already calls `runPipeline()`, which now handles scheduling automatically (Task 5). No changes needed to the entry point — the pipeline returns the scheduled result.

However, we should notify the device when a goal is scheduled (not just started):

**Step 1: Update the pipeline callbacks in device.ts**

In the goal handler in `device.ts`, after calling `runPipeline()`, the `onComplete` callback already sends `goal_completed`. For scheduled goals, the pipeline returns immediately with `stepsUsed: 0` and `resolvedBy: "classifier"`. The device gets the `goal_completed` message which is technically correct.

But for a better UX, add a `goal_scheduled` message type to the device overlay. In `device.ts`, after the pipeline resolves, check if it was scheduled:

This is already handled by the `sessions.notifyDashboard()` call in Task 5. The device gets notified via the existing `onComplete` callback. No additional changes needed for v1.

**Step 2: Commit (if changes were made)**

Skip if no changes needed.

---

### Task 10: Verify end-to-end and handle edge cases

**Files:**
- Modify: `server/src/routes/goals.ts` — ensure `/execute` isn't blocked by session middleware

**Step 1: Verify middleware setup**

This is critical: the `/execute` endpoint must NOT require session auth. As described in Task 6, ensure `goals.use("*", sessionMiddleware)` is removed and middleware is applied inline per-route.

**Step 2: Test locally**

1. Set up QStash env vars (get from Upstash console)
2. Start the server: `bun run --cwd server src/index.ts`
3. Start the web: `bun run --cwd web dev`
4. Submit a goal: "Open YouTube after 2 minutes"
5. Verify:
   - Session appears in DB with `status: "scheduled"`
   - Dashboard shows clock icon + "Scheduled" badge
   - After 2 minutes, QStash hits `/goals/execute`
   - Goal executes normally
   - Session status transitions to `running` → `completed`
6. Test cancel: submit a scheduled goal, click Cancel, verify QStash message deleted

**Step 3: Test edge cases**

- Device offline when QStash fires → should return 500, QStash retries
- QStash not configured → goal should fall through and execute immediately
- Cancel a goal that already fired → should return error "Session is not scheduled"
- Goal with no time reference → should NOT be classified as scheduled

**Step 4: Final commit**

```bash
git add -A
git commit -m "feat: QStash scheduled goals — end-to-end integration"
```

---

## File Summary

| File | Action | Task |
|------|--------|------|
| `server/package.json` | Modify | 1 |
| `server/src/env.ts` | Modify | 1 |
| `server/.env.example` | Modify | 1 |
| `server/src/schema.ts` | Modify | 2 |
| `web/src/lib/server/db/schema.ts` | Modify | 2 |
| `packages/shared/src/types.ts` | Modify | 2 |
| `server/src/qstash.ts` | Create | 3 |
| `server/src/agent/llm.ts` | Modify | 4 |
| `server/src/agent/classifier.ts` | Modify | 4 |
| `server/src/agent/pipeline.ts` | Modify | 5 |
| `server/src/routes/goals.ts` | Modify | 6 |
| `web/src/lib/api/devices.remote.ts` | Modify | 7 |
| `web/src/routes/dashboard/devices/[deviceId]/+page.svelte` | Modify | 8 |
| `web/src/lib/components/DeviceCard.svelte` | Modify | 8 |
