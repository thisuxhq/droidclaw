# QStash Scheduled Goals Design

## Problem

DroidClaw goals execute immediately — there's no way to schedule a goal for later. Users naturally say things like "send this email after 5 minutes" or "remind me to call John tomorrow at 9am", but the system can't handle time-delayed execution.

## Solution

Integrate Upstash QStash as a serverless scheduler. The existing LLM classifier detects time expressions in goals, schedules them via QStash with a delay, and QStash hits the server endpoint when it's time to execute.

## Architecture

```
User: "Send email to John after 5 minutes"
    |
    v
POST /goals (or device WS / voice)
    |
    v
Classifier LLM call -> detects time component
    -> { type: "scheduled", delay: 300, goal: "Send email to John" }
    |
    v
Server stores scheduled goal in DB (status: "scheduled", qstashMessageId, scheduledFor)
    |
    v
QStash publishJSON({ url: "<server>/goals/execute", body: { goalId }, delay: 300 })
    |
    v
Dashboard shows goal with clock icon + "Runs in 5m" tooltip
    |
    v
... 5 minutes pass ...
    |
    v
QStash hits POST /goals/execute
    |
    v
Server checks device online -> runs pipeline as normal
    |
    v
If device offline -> QStash retries (up to 5x with backoff) -> fails after exhaustion
```

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Time parsing | LLM classifier (Option B) | Natural language timing ("tomorrow at 9am", "in a couple hours") is too varied for regex. Classifier already exists in pipeline.ts. |
| Device connectivity | Strict, device must be online (Option A) | QStash retries handle brief disconnections. No new device selection logic needed. |
| UI placement | Inline in existing goals list (Option A) | Clock icon + tooltip, minimal UI work. Separate section is overkill for now. |
| Cancellation | Yes, via QStash messageId | Store messageId in DB, cancel button on scheduled card calls `client.messages.delete()`. |
| Device binding | Device-specific (Option A) | Matches current architecture. Goal scheduled from Pixel runs on Pixel. |

## Database Changes

New columns on `agentSession` table:

| Column | Type | Purpose |
|--------|------|---------|
| `status` | enum | Add `"scheduled"` value (existing: running, completed, failed) |
| `qstashMessageId` | text, nullable | For cancellation via QStash API |
| `scheduledFor` | timestamp, nullable | When the goal should fire |
| `scheduledDelay` | integer, nullable | Original delay in seconds (for UI countdown) |

## New Endpoints

### `POST /goals/execute` (QStash callback)

- **Auth:** QStash signature verification (not session auth)
- **Body:** `{ goalId: string }`
- **Flow:** Look up scheduled goal in DB -> fetch device + LLM config -> run pipeline
- **Failure:** If device offline after QStash exhausts retries, mark goal as failed

### `DELETE /goals/:id/schedule` (Cancel scheduled goal)

- **Auth:** Session auth (user must own the goal)
- **Flow:** Look up goal -> cancel via `client.messages.delete(qstashMessageId)` -> update DB status to "cancelled"

## Classifier Extension

Add Option D to the classifier prompt in `pipeline.ts`:

```
Option D -- SCHEDULED: The goal contains a time delay or future time reference.
Return: {"type":"scheduled","delay":<seconds>,"goal":"<cleaned goal without time reference>"}

Examples:
- "send email after 5 minutes" -> {"type":"scheduled","delay":300,"goal":"send email"}
- "remind me to call John tomorrow at 9am" -> {"type":"scheduled","delay":<seconds until 9am>,"goal":"remind me to call John"}
- "open YouTube in 2 hours" -> {"type":"scheduled","delay":7200,"goal":"open YouTube"}
```

## Web Dashboard UI

- Scheduled goals appear in the existing goals list with:
  - **Clock icon** next to the goal text
  - **"Scheduled" badge** (distinct from Running/Completed/Failed)
  - **Tooltip on hover:** "Scheduled for 3:45 PM" or "Runs in 4m 32s" (countdown)
  - **Cancel button (X):** Calls `DELETE /goals/:id/schedule`
- When QStash fires the goal, status transitions: `scheduled` -> `running` -> `completed`/`failed`

## Dependencies

- `@upstash/qstash` npm package (JS SDK)
- Environment variables in `server/.env`:
  - `QSTASH_TOKEN` — API token from Upstash console
  - `QSTASH_CURRENT_SIGNING_KEY` — for verifying QStash callbacks
  - `QSTASH_NEXT_SIGNING_KEY` — for key rotation

## QStash Pricing

- **Free tier:** 500 messages/day, max 7 day delay
- **Pay-as-you-go:** $1/100k messages, max 1 year delay
- Built-in retries with exponential backoff (configurable up to 5 retries)

## Scope

This design covers scheduling goals with a time delay. It does NOT cover:
- Recurring/cron schedules (future enhancement)
- Multi-device goal routing
- Offline goal queuing (hold until device reconnects)
