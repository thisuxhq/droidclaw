import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { sessionMiddleware, type AuthEnv } from "../middleware/auth.js";
import { sessions } from "../ws/sessions.js";
import { runPipeline, type PipelineOptions } from "../agent/pipeline.js";
import type { LLMConfig } from "../agent/llm.js";
import { db } from "../db.js";
import { llmConfig as llmConfigTable } from "../schema.js";

const goals = new Hono<AuthEnv>();
goals.use("*", sessionMiddleware);

/** Track running agent sessions so we can prevent duplicates and cancel them */
const activeSessions = new Map<string, { sessionId: string; goal: string; abort: AbortController }>();

goals.post("/", async (c) => {
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

goals.post("/stop", async (c) => {
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

export { goals };
