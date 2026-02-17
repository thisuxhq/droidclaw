/**
 * Intent-First Pipeline Orchestrator for DroidClaw.
 *
 * Goal → Parser (0 LLM) → Classifier (1 LLM) → UI Agent (3-8 LLM)
 *         handles ~30%      handles ~20%          handles ~50%
 */

import type {
  InstalledApp,
  PipelineResult,
  ActionDecision,
} from "@droidclaw/shared";
import { sessions } from "../ws/sessions.js";
import { db } from "../db.js";
import {
  device as deviceTable,
  agentSession,
  agentStep,
} from "../schema.js";
import { eq } from "drizzle-orm";
import { parseGoal, buildCapabilities } from "./parser.js";
import { classifyGoal } from "./classifier.js";
import { runAgentLoop, type AgentLoopOptions, type AgentResult } from "./loop.js";
import type { LLMConfig } from "./llm.js";

// ─── Types ───────────────────────────────────────────────────

export interface PipelineOptions {
  deviceId: string;
  persistentDeviceId?: string;
  userId: string;
  goal: string;
  llmConfig: LLMConfig;
  maxSteps?: number;
  signal?: AbortSignal;
  onStep?: AgentLoopOptions["onStep"];
  onComplete?: AgentLoopOptions["onComplete"];
}

export interface PipelineResultFinal {
  success: boolean;
  stepsUsed: number;
  sessionId: string;
  resolvedBy: "parser" | "classifier" | "ui_agent";
}

// ─── Helpers ─────────────────────────────────────────────────

async function fetchInstalledApps(
  persistentDeviceId: string
): Promise<InstalledApp[]> {
  try {
    const rows = await db
      .select({ info: deviceTable.deviceInfo })
      .from(deviceTable)
      .where(eq(deviceTable.id, persistentDeviceId))
      .limit(1);
    const info = rows[0]?.info as Record<string, unknown> | null;
    return (info?.installedApps as InstalledApp[]) ?? [];
  } catch {
    return [];
  }
}

async function executeResult(
  deviceId: string,
  result: PipelineResult
): Promise<{ success: boolean; error?: string }> {
  try {
    switch (result.type) {
      case "intent": {
        const res = (await sessions.sendCommand(deviceId, {
          type: "intent",
          intentAction: result.intent.intentAction,
          intentUri: result.intent.uri,
          intentType: result.intent.intentType,
          intentExtras: result.intent.extras,
          packageName: result.intent.packageName,
        })) as { success?: boolean; error?: string };
        return { success: res.success !== false, error: res.error };
      }
      case "launch": {
        const res = (await sessions.sendCommand(deviceId, {
          type: "launch",
          packageName: result.packageName,
        })) as { success?: boolean; error?: string };
        return { success: res.success !== false, error: res.error };
      }
      case "open_url": {
        const res = (await sessions.sendCommand(deviceId, {
          type: "open_url",
          url: result.url,
        })) as { success?: boolean; error?: string };
        return { success: res.success !== false, error: res.error };
      }
      case "open_settings": {
        const res = (await sessions.sendCommand(deviceId, {
          type: "open_settings",
          setting: result.setting,
        })) as { success?: boolean; error?: string };
        return { success: res.success !== false, error: res.error };
      }
      default:
        return { success: false, error: "Unknown result type" };
    }
  } catch (err) {
    return { success: false, error: (err as Error).message };
  }
}

async function persistQuickSession(
  userId: string,
  persistentDeviceId: string,
  goal: string,
  stage: string,
  action: Record<string, unknown>
): Promise<string> {
  const sessionId = crypto.randomUUID();
  try {
    await db.insert(agentSession).values({
      id: sessionId,
      userId,
      deviceId: persistentDeviceId,
      goal,
      status: "completed",
      stepsUsed: 1,
      completedAt: new Date(),
    });
    await db.insert(agentStep).values({
      id: crypto.randomUUID(),
      sessionId,
      stepNumber: 1,
      action,
      reasoning: `${stage}: direct action`,
      result: "OK",
    });
  } catch (err) {
    console.error(`[Pipeline] Failed to persist session: ${err}`);
  }
  return sessionId;
}

// ─── Main Pipeline ───────────────────────────────────────────

export async function runPipeline(
  options: PipelineOptions
): Promise<PipelineResultFinal> {
  const {
    deviceId,
    persistentDeviceId,
    userId,
    goal,
    llmConfig,
    maxSteps,
    signal,
    onStep,
    onComplete,
  } = options;

  // ── Load device capabilities ─────────────────────────────
  const apps = persistentDeviceId
    ? await fetchInstalledApps(persistentDeviceId)
    : [];
  const caps = buildCapabilities(apps);

  // ── Stage 1: Deterministic Parser ────────────────────────
  const parseResult = parseGoal(goal, caps);
  console.log(`[Pipeline] Stage 1 (Parser): ${parseResult.type}`);

  if (parseResult.type !== "passthrough") {
    if (parseResult.type === "done") {
      const sessionId = persistentDeviceId
        ? await persistQuickSession(userId, persistentDeviceId, goal, "parser", { done: true, reason: parseResult.reason })
        : crypto.randomUUID();
      onComplete?.({ success: true, stepsUsed: 0, sessionId });
      return { success: true, stepsUsed: 0, sessionId, resolvedBy: "parser" };
    }

    const execResult = await executeResult(deviceId, parseResult);
    if (execResult.success) {
      await new Promise((r) => setTimeout(r, 1500));

      const sessionId = persistentDeviceId
        ? await persistQuickSession(userId, persistentDeviceId, goal, "parser", parseResult as unknown as Record<string, unknown>)
        : crypto.randomUUID();

      onStep?.({
        stepNumber: 1,
        action: { action: parseResult.type, reason: `Parser: ${parseResult.type}` } as unknown as ActionDecision,
        reasoning: `Parser: direct ${parseResult.type} action`,
        screenHash: "",
      });

      sessions.notifyDashboard(userId, {
        type: "goal_completed",
        sessionId,
        success: true,
        stepsUsed: 1,
      });

      onComplete?.({ success: true, stepsUsed: 1, sessionId });
      console.log(`[Pipeline] Goal resolved by parser: ${goal}`);
      return { success: true, stepsUsed: 1, sessionId, resolvedBy: "parser" };
    }

    console.warn(`[Pipeline] Parser action failed: ${execResult.error}. Falling through to classifier.`);
  }

  // ── Stage 2: LLM Classifier ──────────────────────────────
  const classResult = await classifyGoal(goal, caps, llmConfig);
  console.log(`[Pipeline] Stage 2 (Classifier): ${classResult.type}`);

  if (classResult.type === "done") {
    const sessionId = persistentDeviceId
      ? await persistQuickSession(userId, persistentDeviceId, goal, "classifier", { done: true, reason: classResult.reason })
      : crypto.randomUUID();
    onComplete?.({ success: true, stepsUsed: 1, sessionId });
    return { success: true, stepsUsed: 1, sessionId, resolvedBy: "classifier" };
  }

  if (classResult.type === "intent") {
    const execResult = await executeResult(deviceId, classResult);
    if (execResult.success) {
      await new Promise((r) => setTimeout(r, 1500));

      const sessionId = persistentDeviceId
        ? await persistQuickSession(userId, persistentDeviceId, goal, "classifier", classResult.intent as unknown as Record<string, unknown>)
        : crypto.randomUUID();

      onStep?.({
        stepNumber: 1,
        action: { action: "intent", intentAction: classResult.intent.intentAction, uri: classResult.intent.uri, reason: "Classifier: intent" } as unknown as ActionDecision,
        reasoning: `Classifier: ${classResult.intent.intentAction}`,
        screenHash: "",
      });

      sessions.notifyDashboard(userId, {
        type: "goal_completed",
        sessionId,
        success: true,
        stepsUsed: 1,
      });

      onComplete?.({ success: true, stepsUsed: 1, sessionId });
      console.log(`[Pipeline] Goal resolved by classifier (intent): ${goal}`);
      return { success: true, stepsUsed: 1, sessionId, resolvedBy: "classifier" };
    }

    console.warn(`[Pipeline] Classifier intent failed: ${execResult.error}. Falling through to UI agent.`);
  }

  // ── Stage 3: Lean UI Agent ───────────────────────────────
  let effectiveGoal = goal;
  let appToLaunch: string | undefined;

  if (classResult.type === "ui") {
    effectiveGoal = classResult.subGoal;
    appToLaunch = classResult.app;
  } else if (classResult.type === "launch") {
    appToLaunch = classResult.packageName;
  }

  if (appToLaunch) {
    try {
      await sessions.sendCommand(deviceId, {
        type: "launch",
        packageName: appToLaunch,
      });
      await new Promise((r) => setTimeout(r, 1500));
      console.log(`[Pipeline] Launched ${appToLaunch} for UI agent`);
    } catch (err) {
      console.warn(`[Pipeline] Failed to launch ${appToLaunch}: ${err}`);
    }
  }

  const loopResult = await runAgentLoop({
    deviceId,
    persistentDeviceId,
    userId,
    goal: effectiveGoal,
    originalGoal: effectiveGoal !== goal ? goal : undefined,
    llmConfig,
    maxSteps,
    signal,
    pipelineMode: true,
    onStep,
    onComplete,
  });

  return {
    ...loopResult,
    resolvedBy: "ui_agent",
  };
}
