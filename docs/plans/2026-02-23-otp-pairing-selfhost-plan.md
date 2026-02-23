# OTP Pairing + Self-Host Toggle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace API key copy-paste with 6-digit OTP pairing for consumer onboarding, and add a Cloud/Self-hosted toggle on Android.

**Architecture:** Web dashboard generates a 6-digit pairing code stored in a `pairing_code` DB table. Android app sends the code to the server's `/pairing/claim` endpoint, which validates it, auto-generates an API key, returns it with the WebSocket URL, and deletes the code. The phone then connects using the API key exactly as it does today.

**Tech Stack:** Hono (server routes), Drizzle ORM (Postgres), SvelteKit (web dashboard), Kotlin/Jetpack Compose (Android), better-auth (API key generation)

**Design doc:** `docs/plans/2026-02-23-otp-pairing-selfhost-design.md`

---

## Task 1: Add `pairing_code` table to both schemas

**Files:**
- Modify: `server/src/schema.ts` (after line 105, before `device` table)
- Modify: `web/src/lib/server/db/schema.ts` (after line 105, before `device` table)

**Step 1: Add table to server schema**

Add to `server/src/schema.ts` after the `llmConfig` table:

```typescript
export const pairingCode = pgTable("pairing_code", {
  id: text("id").primaryKey(),
  code: text("code").notNull().unique(),
  userId: text("user_id")
    .notNull()
    .references(() => user.id, { onDelete: "cascade" }),
  expiresAt: timestamp("expires_at").notNull(),
  createdAt: timestamp("created_at").defaultNow().notNull(),
});
```

**Step 2: Add identical table to web schema**

Add to `web/src/lib/server/db/schema.ts` after the `llmConfig` table (same definition, but using tab indentation to match the web schema style):

```typescript
export const pairingCode = pgTable('pairing_code', {
	id: text('id').primaryKey(),
	code: text('code').notNull().unique(),
	userId: text('user_id')
		.notNull()
		.references(() => user.id, { onDelete: 'cascade' }),
	expiresAt: timestamp('expires_at').notNull(),
	createdAt: timestamp('created_at').defaultNow().notNull()
});
```

**Step 3: Generate and run migration**

```bash
cd web && npx drizzle-kit generate
cd web && npx drizzle-kit push
```

Verify: Check the generated SQL in `web/drizzle/` contains `CREATE TABLE pairing_code`.

**Step 4: Verify typechecks pass**

```bash
cd server && bun run typecheck
cd web && bun run check
```

**Step 5: Commit**

```bash
git add server/src/schema.ts web/src/lib/server/db/schema.ts web/drizzle/
git commit -m "feat(db): add pairing_code table for OTP device pairing"
```

---

## Task 2: Server pairing routes (create, claim, status)

**Files:**
- Create: `server/src/routes/pairing.ts`
- Modify: `server/src/index.ts` (line 14, 38 — add import and route)

**Step 1: Create `server/src/routes/pairing.ts`**

```typescript
import { Hono } from "hono";
import { eq, and, gt } from "drizzle-orm";
import { sessionMiddleware, type AuthEnv } from "../middleware/auth.js";
import { db } from "../db.js";
import { pairingCode, apikey, user } from "../schema.js";
import { auth } from "../auth.js";

// ── Helpers ────────────────────────────────────────────

function generateCode(): string {
  return String(Math.floor(100000 + Math.random() * 900000));
}

const CODE_TTL_MS = 5 * 60 * 1000; // 5 minutes

// Rate limit: track claim attempts per IP
const claimAttempts = new Map<string, { count: number; resetAt: number }>();
const MAX_CLAIMS_PER_MIN = 5;

function checkRateLimit(ip: string): boolean {
  const now = Date.now();
  const entry = claimAttempts.get(ip);
  if (!entry || now > entry.resetAt) {
    claimAttempts.set(ip, { count: 1, resetAt: now + 60_000 });
    return true;
  }
  if (entry.count >= MAX_CLAIMS_PER_MIN) return false;
  entry.count++;
  return true;
}

// ── Authenticated routes (create + status) ─────────────

const authenticated = new Hono<AuthEnv>();
authenticated.use("*", sessionMiddleware);

// POST /pairing/create — generate a 6-digit code for the logged-in user
authenticated.post("/create", async (c) => {
  const currentUser = c.get("user");

  // Delete any existing code for this user (one active code per user)
  await db.delete(pairingCode).where(eq(pairingCode.userId, currentUser.id));

  const code = generateCode();
  const now = new Date();
  const expiresAt = new Date(now.getTime() + CODE_TTL_MS);

  await db.insert(pairingCode).values({
    id: crypto.randomUUID(),
    code,
    userId: currentUser.id,
    expiresAt,
    createdAt: now,
  });

  return c.json({ code, expiresAt: expiresAt.toISOString() });
});

// GET /pairing/status — check if the user's code has been claimed
authenticated.get("/status", async (c) => {
  const currentUser = c.get("user");

  // If no pairing code exists for the user, it was claimed (deleted)
  const codes = await db
    .select()
    .from(pairingCode)
    .where(eq(pairingCode.userId, currentUser.id))
    .limit(1);

  if (codes.length === 0) {
    // Code was claimed — check if a device came online recently
    return c.json({ paired: true });
  }

  // Code still active — not yet claimed
  const expired = new Date() > codes[0].expiresAt;
  return c.json({ paired: false, expired });
});

// ── Public route (claim) ───────────────────────────────

const publicRoutes = new Hono();

// POST /pairing/claim — phone sends the 6-digit code to get an API key
publicRoutes.post("/claim", async (c) => {
  const ip = c.req.header("x-forwarded-for") ?? c.req.header("x-real-ip") ?? "unknown";
  if (!checkRateLimit(ip)) {
    return c.json({ error: "Too many attempts. Try again in a minute." }, 429);
  }

  const body = await c.req.json<{ code: string; deviceInfo?: Record<string, unknown> }>();
  if (!body.code || body.code.length !== 6) {
    return c.json({ error: "Invalid code" }, 400);
  }

  // Look up the code
  const codes = await db
    .select()
    .from(pairingCode)
    .where(
      and(
        eq(pairingCode.code, body.code),
        gt(pairingCode.expiresAt, new Date())
      )
    )
    .limit(1);

  if (codes.length === 0) {
    return c.json({ error: "Invalid or expired code" }, 400);
  }

  const pairingEntry = codes[0];

  // Auto-generate an API key for this user via better-auth
  // We need to call the auth API internally
  const keyName = body.deviceInfo
    ? `Paired: ${(body.deviceInfo as any).model ?? "Device"}`
    : "Paired Device";

  let generatedKey: string;
  try {
    // Use better-auth's internal API to create an API key
    // We need to make an internal HTTP call since better-auth expects request context
    const createResponse = await auth.api.createApiKey({
      body: { name: keyName, prefix: "droidclaw_", userId: pairingEntry.userId },
    });
    generatedKey = (createResponse as any).key;
  } catch (err) {
    console.error("[Pairing] Failed to create API key:", err);
    return c.json({ error: "Failed to generate credentials" }, 500);
  }

  // Delete the used code
  await db.delete(pairingCode).where(eq(pairingCode.id, pairingEntry.id));

  // Return the API key and WebSocket URL
  const wsUrl = process.env.WS_URL ?? "wss://tunnel.droidclaw.ai";

  return c.json({
    apiKey: generatedKey,
    wsUrl,
  });
});

// ── Combine into one router ────────────────────────────

const pairing = new Hono();
pairing.route("/", authenticated);
pairing.route("/", publicRoutes);

export { pairing };
```

**Step 2: Register route in `server/src/index.ts`**

Add import at line 14:
```typescript
import { pairing } from "./routes/pairing.js";
```

Add route at line 38:
```typescript
app.route("/pairing", pairing);
```

**Step 3: Add `WS_URL` to `server/.env.example`**

```
# WebSocket URL returned to devices during OTP pairing
WS_URL="wss://tunnel.droidclaw.ai"
```

**Step 4: Verify typecheck**

```bash
cd server && bun run typecheck
```

**Step 5: Manual test**

Start the server and test with curl:

```bash
# Create a code (needs auth cookie — test via dashboard or mock)
curl -X POST http://localhost:8080/pairing/create \
  -H "Content-Type: application/json" \
  -H "x-internal-secret: <secret>" \
  -H "x-internal-user-id: <userId>"

# Claim a code (public, no auth)
curl -X POST http://localhost:8080/pairing/claim \
  -H "Content-Type: application/json" \
  -d '{"code": "738412", "deviceInfo": {"model": "Pixel 8"}}'

# Check status (needs auth)
curl http://localhost:8080/pairing/status \
  -H "x-internal-secret: <secret>" \
  -H "x-internal-user-id: <userId>"
```

**Step 6: Commit**

```bash
git add server/src/routes/pairing.ts server/src/index.ts server/.env.example
git commit -m "feat(server): add OTP pairing create/claim/status endpoints"
```

---

## Task 3: Web dashboard — pairing API client

**Files:**
- Create: `web/src/lib/api/pairing.remote.ts`

**Step 1: Create the API client**

Follow the pattern from `web/src/lib/api/devices.remote.ts` (lines 163-183):

```typescript
import { query, command, getRequestEvent } from '$app/server';
import { env } from '$env/dynamic/private';

const SERVER_URL = () => env.SERVER_URL || 'http://localhost:8080';
const INTERNAL_SECRET = () => env.INTERNAL_SECRET || '';

async function serverFetch(path: string, method: 'GET' | 'POST' = 'POST', body?: Record<string, unknown>) {
	const { locals } = getRequestEvent();
	if (!locals.user) throw new Error('unauthorized');

	const res = await fetch(`${SERVER_URL()}${path}`, {
		method,
		headers: {
			'Content-Type': 'application/json',
			'x-internal-secret': INTERNAL_SECRET(),
			'x-internal-user-id': locals.user.id
		},
		body: body ? JSON.stringify(body) : undefined
	});
	const data = await res.json().catch(() => ({ error: 'Unknown error' }));
	if (!res.ok) throw new Error(data.error ?? `Error ${res.status}`);
	return data;
}

/** Generate a 6-digit pairing code for the current user */
export const createPairingCode = command(async () => {
	return serverFetch('/pairing/create', 'POST');
});

/** Check if the pairing code has been claimed by a device */
export const getPairingStatus = query(async () => {
	return serverFetch('/pairing/status', 'GET');
});
```

**Step 2: Commit**

```bash
git add web/src/lib/api/pairing.remote.ts
git commit -m "feat(web): add pairing API client for OTP flow"
```

---

## Task 4: Web dashboard — "Pair Device" button + modal on Devices page

**Files:**
- Modify: `web/src/routes/dashboard/devices/+page.svelte`

**Step 1: Read the current devices page fully first**

Read the full file to understand the current layout, imports, and component structure before making changes.

**Step 2: Add pairing UI**

Add to the devices page:
- Import `createPairingCode` and `getPairingStatus` from `$lib/api/pairing.remote`
- "Pair Device" button (always visible, prominently placed)
- Modal/dialog showing the 6-digit code with large monospace digits
- Countdown timer (5 minutes)
- Polling `getPairingStatus` every 2 seconds while modal is open
- Success state when paired
- "Developer? Use API keys" link at bottom of modal

**Key UI states:**
1. Idle — "Pair Device" button visible
2. Code shown — 6 large digits + countdown + "Waiting for device..." spinner
3. Expired — "Code expired. Generate new code?" button
4. Paired — checkmark + device name + "Done" button

**Step 3: Verify**

```bash
cd web && bun run check
cd web && bun run dev
```

Open browser, go to Devices page, click "Pair Device", verify code displays.

**Step 4: Commit**

```bash
git add web/src/routes/dashboard/devices/+page.svelte
git commit -m "feat(web): add Pair Device button and OTP modal to devices page"
```

---

## Task 5: Android — pairing API client

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/api/PairingApi.kt`

**Step 1: Create the API client**

```kotlin
package com.thisux.droidclaw.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ClaimRequest(
    val code: String,
    val deviceInfo: DeviceInfoPayload? = null
)

@Serializable
data class DeviceInfoPayload(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val screenWidth: Int,
    val screenHeight: Int
)

@Serializable
data class ClaimResponse(
    val apiKey: String,
    val wsUrl: String
)

@Serializable
data class ClaimError(
    val error: String
)

object PairingApi {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private const val DEFAULT_BASE_URL = "https://tunnel.droidclaw.ai"

    /**
     * Claim a pairing code. Returns the API key and WebSocket URL on success.
     * Throws on network error or invalid/expired code.
     */
    suspend fun claim(
        code: String,
        deviceInfo: DeviceInfoPayload,
        baseUrl: String = DEFAULT_BASE_URL
    ): ClaimResponse {
        val response = client.post("$baseUrl/pairing/claim") {
            contentType(ContentType.Application.Json)
            setBody(ClaimRequest(code = code, deviceInfo = deviceInfo))
        }

        if (response.status == HttpStatusCode.OK) {
            return response.body<ClaimResponse>()
        }

        val error = try {
            response.body<ClaimError>().error
        } catch (_: Exception) {
            "Pairing failed (${response.status})"
        }
        throw PairingException(error)
    }
}

class PairingException(message: String) : Exception(message)
```

Note: Check the existing Android dependencies in `build.gradle.kts` — Ktor is already used by `ReliableWebSocket.kt` so the HTTP client should already be available. If not, add:

```kotlin
implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/api/PairingApi.kt
git commit -m "feat(android): add pairing API client for OTP claim"
```

---

## Task 6: Android — update SettingsStore with connection mode

**Files:**
- Modify: `android/app/src/main/java/com/thisux/droidclaw/data/SettingsStore.kt`

**Step 1: Add connection mode preference**

Add to `SettingsKeys` object:
```kotlin
val CONNECTION_MODE = stringPreferencesKey("connection_mode") // "cloud" | "selfhosted"
```

Add to `SettingsStore` class:
```kotlin
val connectionMode: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[SettingsKeys.CONNECTION_MODE] ?: "cloud"
}

suspend fun setConnectionMode(value: String) {
    context.dataStore.edit { it[SettingsKeys.CONNECTION_MODE] = value }
}
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/data/SettingsStore.kt
git commit -m "feat(android): add connectionMode preference to SettingsStore"
```

---

## Task 7: Android — update OnboardingScreen with OTP input

**Files:**
- Modify: `android/app/src/main/java/com/thisux/droidclaw/ui/screens/OnboardingScreen.kt`

**Step 1: Read the current OnboardingScreen fully**

Read the full file to understand all steps, state management, and the completion flow.

**Step 2: Replace Step 0 (API key input) with OTP input**

Replace `OnboardingStepOne` composable:
- Remove API key + server URL text fields
- Add instructions: "1. Open droidclaw.ai/dashboard  2. Go to Devices > Pair Device  3. Enter the 6-digit code below"
- Add 6-digit OTP input (6 individual `OutlinedTextField` boxes, each 1 char, auto-advance focus)
- "Connect" button calls `PairingApi.claim()` with the code + device info
- On success: store `apiKey` and `wsUrl` in SettingsStore, advance to next step
- On error: show error toast ("Invalid or expired code")
- Bottom link: "Self-hosting? Tap here for manual setup" — expands to show URL + API key fields (existing flow)

**Key UX details:**
- OTP fields should be `KeyboardType.Number` with `maxLength = 1`
- Auto-focus next field on input
- Loading state on "Connect" button while API call in progress
- Error state clears when user modifies any OTP field

**Step 3: Update completion flow**

After successful pairing claim:
```kotlin
scope.launch {
    app.settingsStore.setApiKey(claimResponse.apiKey)
    app.settingsStore.setServerUrl(claimResponse.wsUrl)
    app.settingsStore.setConnectionMode("cloud")
    // Continue to next onboarding step (permissions)
    currentStep = 1
}
```

**Step 4: Verify**

Build and run on device/emulator. Verify:
- OTP input shows on first onboarding step
- Entering a valid code (from web dashboard) pairs successfully
- "Self-hosting?" link shows manual URL + key fields
- After pairing, connection works normally

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/ui/screens/OnboardingScreen.kt
git commit -m "feat(android): replace API key input with OTP pairing on onboarding"
```

---

## Task 8: Android — update SettingsScreen with Cloud/Self-hosted toggle

**Files:**
- Modify: `android/app/src/main/java/com/thisux/droidclaw/ui/screens/SettingsScreen.kt`

**Step 1: Read the current SettingsScreen fully**

Read the full file to understand layout and state management.

**Step 2: Add connection mode radio toggle**

Replace the Server section (lines ~147-193) with:
- Radio button group: "DroidClaw Cloud" (default) / "Self-hosted"
- When "Cloud" selected: hide URL and API key fields, show "Re-pair device" button
- When "Self-hosted" selected: show WebSocket URL field + API key field + "Test Connection" button
- "Test Connection" attempts a WebSocket handshake to validate the URL

**Step 3: Handle mode switching**

When switching from Cloud to Self-hosted:
- Clear the stored API key (user needs to enter their own)
- Show URL field pre-filled with `wss://`

When switching from Self-hosted to Cloud:
- Clear URL and API key
- Prompt to re-pair via OTP

**Step 4: Verify**

Build and run. Verify:
- Toggle between Cloud and Self-hosted modes
- Self-hosted shows URL + key fields
- Cloud mode hides them
- Settings persist across app restarts

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/ui/screens/SettingsScreen.kt
git commit -m "feat(android): add Cloud/Self-hosted connection mode toggle in settings"
```

---

## Task 9: End-to-end testing and cleanup

**Step 1: Full flow test**

1. Open web dashboard → Devices → "Pair Device" → see 6-digit code
2. Open Android app (fresh install or cleared data) → Onboarding → type the code → "Connect"
3. Verify: phone connects, device appears on web dashboard
4. Verify: goal execution works (submit a goal from web or voice)
5. Verify: code is deleted from DB after pairing

**Step 2: Edge cases to test**

- Expired code (wait >5 min) → should show error
- Wrong code → should show "Invalid or expired code"
- Double-claim same code → second attempt should fail
- Generate new code while old one exists → old one should be deleted
- Self-hosted mode → enter custom URL + API key → connects
- Switch back to Cloud → prompts re-pair

**Step 3: Cleanup expired codes (optional)**

Add a periodic cleanup to `server/src/routes/pairing.ts`:

```typescript
// Clean up expired codes every 10 minutes
setInterval(async () => {
  try {
    await db.delete(pairingCode).where(
      gt(new Date(), pairingCode.expiresAt)
    );
  } catch (err) {
    console.error("[Pairing] Cleanup failed:", err);
  }
}, 10 * 60 * 1000);
```

Actually — use `lt(pairingCode.expiresAt, new Date())` for "expiresAt < now" (expired rows).

**Step 4: Final commit**

```bash
git add -A
git commit -m "feat: OTP device pairing + self-host toggle

- 6-digit pairing code flow (web → server → Android)
- Cloud/Self-hosted toggle on Android settings
- Consumer-friendly onboarding with OTP input
- Developer flow preserved via API Keys page"
```

---

## Execution Order

| Task | Depends On | Component |
|------|-----------|-----------|
| 1. DB schema + migration | — | Server + Web |
| 2. Server pairing routes | Task 1 | Server |
| 3. Web pairing API client | Task 2 | Web |
| 4. Web devices page modal | Task 3 | Web |
| 5. Android pairing API | Task 2 | Android |
| 6. Android SettingsStore | — | Android |
| 7. Android onboarding OTP | Tasks 5, 6 | Android |
| 8. Android settings toggle | Task 6 | Android |
| 9. E2E test + cleanup | All | All |

Tasks 1-4 (server + web) and 5-8 (Android) can be done in parallel after Task 1 and Task 2 are complete.
