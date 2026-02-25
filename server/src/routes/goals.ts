import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { Receiver } from "@upstash/qstash";
import { sessionMiddleware, type AuthEnv } from "../middleware/auth.js";
import { sessions } from "../ws/sessions.js";
import { runPipeline, type PipelineOptions } from "../agent/pipeline.js";
import type { LLMConfig } from "../agent/llm.js";
import { db } from "../db.js";
import { env } from "../env.js";
import { agentSession, llmConfig as llmConfigTable } from "../schema.js";

const goals = new Hono<AuthEnv>();

/** Track running agent sessions so we can prevent duplicates and cancel them */
const activeSessions = new Map<string, { sessionId: string; goal: string; abort: AbortController }>();

goals.post("/", sessionMiddleware, async (c) => {
  const user = c.get("user");
  const body = await c.req.json<{
    deviceId: string;
    goal: string;
    llmProvider?: string;
    llmApiKey?: string;
    llmModel?: string;
    maxSteps?: number;
  }>();

  if (!body.deviceId || !body.goal) {
    return c.json({ error: "deviceId and goal are required" }, 400);
  }

  // Look up by connection ID first, then by persistent DB ID
  const device = sessions.getDevice(body.deviceId)
    ?? sessions.getDeviceByPersistentId(body.deviceId);
  if (!device) {
    return c.json({ error: "device not connected" }, 404);
  }

  if (device.userId !== user.id) {
    return c.json({ error: "device does not belong to you" }, 403);
  }

  // Prevent multiple agent loops on the same device
  const trackingKey = device.persistentDeviceId ?? device.deviceId;
  if (activeSessions.has(trackingKey)) {
    const existing = activeSessions.get(trackingKey)!;
    return c.json(
      { error: "agent already running on this device", sessionId: existing.sessionId, goal: existing.goal },
      409
    );
  }

  // Build LLM config: request body → user's DB config → env defaults
  let llmCfg: LLMConfig;

  if (body.llmApiKey) {
    llmCfg = {
      provider: body.llmProvider ?? process.env.LLM_PROVIDER ?? "openai",
      apiKey: body.llmApiKey,
      model: body.llmModel,
    };
  } else {
    // Fetch user's saved LLM config from DB (same as device WS handler)
    const configs = await db
      .select()
      .from(llmConfigTable)
      .where(eq(llmConfigTable.userId, user.id))
      .limit(1);

    if (configs.length > 0) {
      const cfg = configs[0];
      llmCfg = {
        provider: cfg.provider,
        apiKey: cfg.apiKey,
        model: body.llmModel ?? cfg.model ?? undefined,
      };
    } else if (process.env.LLM_API_KEY) {
      llmCfg = {
        provider: process.env.LLM_PROVIDER ?? "openai",
        apiKey: process.env.LLM_API_KEY,
        model: body.llmModel,
      };
    } else {
      return c.json({ error: "No LLM provider configured. Set it up in the web dashboard Settings." }, 400);
    }
  }

  const abort = new AbortController();

  /** Send a JSON message to the device WebSocket (if still connected) */
  const sendToDevice = (msg: Record<string, unknown>) => {
    const d = sessions.getDevice(device.deviceId) ?? sessions.getDeviceByPersistentId(device.persistentDeviceId ?? "");
    if (!d) return;
    try { d.ws.send(JSON.stringify(msg)); } catch { /* disconnected */ }
  };

  const pipelineOpts: PipelineOptions = {
    deviceId: device.deviceId,
    persistentDeviceId: device.persistentDeviceId,
    userId: user.id,
    goal: body.goal,
    llmConfig: llmCfg,
    maxSteps: body.maxSteps,
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
  };

  const sessionPlaceholder = { sessionId: "pending", goal: body.goal, abort };
  activeSessions.set(trackingKey, sessionPlaceholder);

  // Notify device that goal has started
  sendToDevice({ type: "goal_started", goal: body.goal });

  const loopPromise = runPipeline(pipelineOpts);

  loopPromise
    .then((result) => {
      activeSessions.delete(trackingKey);
      console.log(
        `[Pipeline] Completed on ${device.deviceId}: ${result.success ? "success" : "incomplete"} in ${result.stepsUsed} steps (resolved by ${result.resolvedBy})`
      );
    })
    .catch((err) => {
      activeSessions.delete(trackingKey);
      sendToDevice({ type: "goal_failed", message: String(err) });
      console.error(`[Pipeline] Error on ${device.deviceId}: ${err}`);
    });

  return c.json({
    deviceId: body.deviceId,
    goal: body.goal,
    status: "started",
  });
});

goals.post("/stop", sessionMiddleware, async (c) => {
  const user = c.get("user");
  const body = await c.req.json<{ deviceId: string }>();

  if (!body.deviceId) {
    return c.json({ error: "deviceId is required" }, 400);
  }

  // Look up device to verify ownership
  const device = sessions.getDevice(body.deviceId)
    ?? sessions.getDeviceByPersistentId(body.deviceId);
  if (!device) {
    return c.json({ error: "device not connected" }, 404);
  }
  if (device.userId !== user.id) {
    return c.json({ error: "device does not belong to you" }, 403);
  }

  const trackingKey = device.persistentDeviceId ?? device.deviceId;
  const active = activeSessions.get(trackingKey);
  if (!active) {
    return c.json({ error: "no agent running on this device" }, 404);
  }

  active.abort.abort();
  console.log(`[Agent] Stop requested for device ${body.deviceId}`);
  return c.json({ status: "stopping" });
});

/**
 * QStash callback — executes a scheduled goal.
 * Auth: QStash signature verification (not session auth).
 */
goals.post("/execute", async (c) => {
  const body = await c.req.text();

  // Verify QStash signature if signing keys configured
  if (env.QSTASH_CURRENT_SIGNING_KEY) {
    const receiver = new Receiver({
      currentSigningKey: env.QSTASH_CURRENT_SIGNING_KEY,
      nextSigningKey: env.QSTASH_NEXT_SIGNING_KEY,
    });
    const signature = c.req.header("upstash-signature") ?? "";
    try {
      await receiver.verify({ signature, body });
    } catch {
      return c.json({ error: "Invalid QStash signature" }, 401);
    }
  }

  const payload = JSON.parse(body) as {
    sessionId: string;
    deviceId: string;
    userId: string;
    goal: string;
  };

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
    return c.json({ error: "Device busy" }, 500);
  }

  const sendToDevice = (msg: Record<string, unknown>) => {
    const d = sessions.getDevice(device.deviceId) ?? sessions.getDeviceByPersistentId(device.persistentDeviceId ?? "");
    if (!d) return;
    try { d.ws.send(JSON.stringify(msg)); } catch { /* disconnected */ }
  };

  activeSessions.set(trackingKey, { sessionId, goal, abort });
  sendToDevice({ type: "goal_started", goal });

  sessions.notifyDashboard(userId, {
    type: "goal_started",
    deviceId: device.persistentDeviceId ?? device.deviceId,
    goal,
    sessionId,
  });

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
});

/**
 * Cancel a scheduled goal.
 */
goals.delete("/:id/schedule", sessionMiddleware, async (c) => {
  const user = c.get("user");
  const goalId = c.req.param("id");

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

  await db
    .update(agentSession)
    .set({ status: "cancelled", completedAt: new Date() })
    .where(eq(agentSession.id, goalId));

  sessions.notifyDashboard(user.id, {
    type: "goal_cancelled",
    sessionId: goalId,
  });

  return c.json({ status: "cancelled" });
});

export { goals };
