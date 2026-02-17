/**
 * Stage 2: LLM-based goal classifier for DroidClaw pipeline.
 *
 * Makes a single small LLM call to classify goals that didn't match
 * any regex pattern in Stage 1. Returns either an intent to fire
 * or {app, subGoal} for the UI agent.
 */

import type {
  DeviceCapabilities,
  PipelineResult,
  IntentCommand,
} from "@droidclaw/shared";
import {
  getLlmProvider,
  getClassifierPrompt,
  parseJsonResponse,
  type LLMConfig,
} from "./llm.js";

/**
 * Build a concise capability summary for the classifier prompt.
 * Lists apps with their intent capabilities, keeping it short.
 */
function buildCapabilitySummary(caps: DeviceCapabilities): string {
  const lines: string[] = [];
  for (const app of caps.apps) {
    const intents = app.intents?.length
      ? ` [${app.intents.join(", ")}]`
      : "";
    lines.push(`${app.label}: ${app.packageName}${intents}`);
  }
  return lines.join("\n");
}

/**
 * Stage 2: Classify a goal using a single LLM call.
 * Returns a PipelineResult with either an intent or UI handoff.
 */
export async function classifyGoal(
  goal: string,
  caps: DeviceCapabilities,
  llmConfig: LLMConfig
): Promise<PipelineResult> {
  const summary = buildCapabilitySummary(caps);
  const { system, user } = getClassifierPrompt(goal, summary);
  const llm = getLlmProvider(llmConfig);

  let raw: string;
  try {
    raw = await llm.getAction(system, user);
  } catch (err) {
    console.error(`[Classifier] LLM error: ${(err as Error).message}`);
    return { stage: "classifier", type: "passthrough" };
  }

  const parsed = parseJsonResponse(raw);
  if (!parsed || !parsed.type) {
    console.warn(`[Classifier] Failed to parse response: ${raw.slice(0, 200)}`);
    return { stage: "classifier", type: "passthrough" };
  }

  switch (parsed.type) {
    case "intent": {
      const rawExtras = parsed.extras;
      const validExtras = (rawExtras && typeof rawExtras === "object" && !Array.isArray(rawExtras))
        ? rawExtras as Record<string, string>
        : undefined;
      const intent: IntentCommand = {
        intentAction: typeof parsed.intentAction === "string" ? parsed.intentAction : "",
        uri: typeof parsed.uri === "string" ? parsed.uri : undefined,
        intentType: typeof parsed.intentType === "string" ? parsed.intentType : undefined,
        extras: validExtras,
        packageName: typeof parsed.packageName === "string" ? parsed.packageName : undefined,
      };
      if (!intent.intentAction) {
        console.warn("[Classifier] Intent missing intentAction, falling through");
        return { stage: "classifier", type: "passthrough" };
      }
      return { stage: "classifier", type: "intent", intent };
    }

    case "ui": {
      const app = (parsed.app as string) ?? "";
      const subGoal = (parsed.subGoal as string) ?? goal;
      if (!app) {
        console.warn("[Classifier] UI result missing app, falling through");
        return { stage: "classifier", type: "passthrough" };
      }
      return { stage: "classifier", type: "ui", app, subGoal };
    }

    case "done": {
      return {
        stage: "classifier",
        type: "done",
        reason: (parsed.reason as string) ?? "Goal cannot be achieved",
      };
    }

    default:
      console.warn(`[Classifier] Unknown type: ${parsed.type}`);
      return { stage: "classifier", type: "passthrough" };
  }
}
