/**
 * Flow runner for DroidClaw.
 * Executes deterministic YAML flows (Maestro-style) — no LLM needed.
 *
 * Usage:
 *   bun run src/kernel.ts --flow examples/flows/create-contact.yaml
 *
 * YAML format:
 *   appId: com.android.contacts   # optional frontmatter
 *   name: Create Contact          # optional flow name
 *   ---
 *   - launchApp
 *   - tap: "First Name"
 *   - type: "John"
 *   - back
 *   - done: "Contact created"
 */

import { existsSync, readFileSync } from "fs";
import { parseAllDocuments } from "yaml";
import { Config } from "./config.js";
import {
  executeAction,
  runAdbCommand,
  getScreenResolution,
  initDeviceContext,
  type ActionResult,
} from "./actions.js";
import { getInteractiveElements, type UIElement } from "./sanitizer.js";

// ===========================================
// Types
// ===========================================

interface FlowFrontmatter {
  appId?: string;
  name?: string;
}

type FlowStep =
  | string                             // "launchApp", "back", "home", "enter", "clear"
  | { [key: string]: string | number | [number, number] }; // "tap: text", "type: text", etc.

interface ParsedFlow {
  frontmatter: FlowFrontmatter;
  steps: FlowStep[];
}

// ===========================================
// YAML Parsing
// ===========================================

function parseFlowFile(filePath: string): ParsedFlow {
  if (!existsSync(filePath)) {
    throw new Error(`Flow file not found: ${filePath}`);
  }

  const raw = readFileSync(filePath, "utf-8");
  const docs = parseAllDocuments(raw);

  let frontmatter: FlowFrontmatter = {};
  let steps: FlowStep[] = [];

  if (docs.length === 1) {
    // Single document — could be just steps or frontmatter+steps
    const content = docs[0].toJSON();
    if (Array.isArray(content)) {
      steps = content;
    } else if (content && typeof content === "object") {
      // Entire doc is frontmatter with no steps? Shouldn't happen, but handle it.
      frontmatter = content as FlowFrontmatter;
    }
  } else if (docs.length >= 2) {
    // First doc = frontmatter, second doc = steps
    frontmatter = (docs[0].toJSON() ?? {}) as FlowFrontmatter;
    steps = (docs[1].toJSON() ?? []) as FlowStep[];
  }

  if (!Array.isArray(steps) || steps.length === 0) {
    throw new Error("Flow file contains no steps");
  }

  return { frontmatter, steps };
}

// ===========================================
// Element Finding
// ===========================================

function scanScreen(): UIElement[] {
  try {
    runAdbCommand(["shell", "uiautomator", "dump", Config.SCREEN_DUMP_PATH]);
    runAdbCommand(["pull", Config.SCREEN_DUMP_PATH, Config.LOCAL_DUMP_PATH]);
  } catch {
    console.log("Warning: Screen capture failed during flow step.");
    return [];
  }
  if (!existsSync(Config.LOCAL_DUMP_PATH)) return [];
  return getInteractiveElements(readFileSync(Config.LOCAL_DUMP_PATH, "utf-8"));
}

function findElementByText(elements: UIElement[], query: string): UIElement | null {
  const q = query.toLowerCase();

  // Exact match first
  const exact = elements.find(
    (el) => el.text && el.text.toLowerCase() === q
  );
  if (exact) return exact;

  // Substring match — prefer shorter text (more specific)
  const matches = elements
    .filter((el) => el.text && el.text.toLowerCase().includes(q))
    .sort((a, b) => a.text.length - b.text.length);
  if (matches.length > 0) return matches[0];

  // Hint match
  const hintMatch = elements.find(
    (el) => el.hint && el.hint.toLowerCase().includes(q)
  );
  if (hintMatch) return hintMatch;

  // Resource ID match
  const idMatch = elements.find(
    (el) => el.id && el.id.toLowerCase().includes(q)
  );
  if (idMatch) return idMatch;

  return null;
}

// ===========================================
// Step Execution
// ===========================================

function executeFlowStep(
  step: FlowStep,
  frontmatter: FlowFrontmatter,
  stepIndex: number
): ActionResult {
  // Simple string commands: "launchApp", "back", "home", "enter", "clear"
  if (typeof step === "string") {
    switch (step) {
      case "launchApp":
        if (!frontmatter.appId) {
          return { success: false, message: "launchApp requires appId in frontmatter" };
        }
        return executeAction({ action: "launch", package: frontmatter.appId });
      case "back":
        return executeAction({ action: "back" });
      case "home":
        return executeAction({ action: "home" });
      case "enter":
        return executeAction({ action: "enter" });
      case "clear":
        return executeAction({ action: "clear" });
      case "paste":
        return executeAction({ action: "paste" });
      case "done":
        return executeAction({ action: "done", reason: "Flow complete" });
      default:
        return { success: false, message: `Unknown step: ${step}` };
    }
  }

  // Object commands: { tap: "text", type: "hello", wait: 2, ... }
  if (typeof step === "object" && step !== null) {
    const [command, value] = Object.entries(step)[0];

    switch (command) {
      case "tap": {
        if (Array.isArray(value)) {
          // Coordinate tap: tap: [x, y]
          return executeAction({ action: "tap", coordinates: value as [number, number] });
        }
        // Text tap: find element and tap
        const elements = scanScreen();
        const el = findElementByText(elements, String(value));
        if (!el) {
          const available = elements.filter((e) => e.text).map((e) => e.text).slice(0, 10);
          return { success: false, message: `Element "${value}" not found. Available: ${available.join(", ")}` };
        }
        console.log(`  Found "${el.text}" at (${el.center[0]}, ${el.center[1]})`);
        return executeAction({ action: "tap", coordinates: el.center });
      }

      case "longpress": {
        if (Array.isArray(value)) {
          return executeAction({ action: "longpress", coordinates: value as [number, number] });
        }
        const elements = scanScreen();
        const el = findElementByText(elements, String(value));
        if (!el) {
          return { success: false, message: `Element "${value}" not found for longpress` };
        }
        console.log(`  Found "${el.text}" at (${el.center[0]}, ${el.center[1]})`);
        return executeAction({ action: "longpress", coordinates: el.center });
      }

      case "type":
        return executeAction({ action: "type", text: String(value) });

      case "swipe":
        return executeAction({ action: "swipe", direction: String(value) });

      case "scroll":
        return executeAction({ action: "scroll", direction: String(value) });

      case "wait": {
        const seconds = Number(value) || 2;
        console.log(`  Waiting ${seconds}s...`);
        Bun.sleepSync(seconds * 1000);
        return { success: true, message: `Waited ${seconds}s` };
      }

      case "launch":
        return executeAction({ action: "launch", package: String(value) });

      case "openUrl":
        return executeAction({ action: "open_url", url: String(value) });

      case "clipboard":
        return executeAction({ action: "clipboard_set", text: String(value) });

      case "paste": {
        if (Array.isArray(value)) {
          return executeAction({ action: "paste", coordinates: value as [number, number] });
        }
        return executeAction({ action: "paste" });
      }

      case "shell":
        return executeAction({ action: "shell", command: String(value) });

      case "keyevent":
        return executeAction({ action: "keyevent", code: Number(value) });

      case "settings":
        return executeAction({ action: "open_settings", setting: String(value) });

      case "done":
        return executeAction({ action: "done", reason: String(value) });

      default:
        return { success: false, message: `Unknown command: ${command}` };
    }
  }

  return { success: false, message: `Invalid step at index ${stepIndex}: ${JSON.stringify(step)}` };
}

// ===========================================
// Flow Runner
// ===========================================

export interface FlowResult {
  name: string;
  success: boolean;
  stepsCompleted: number;
  totalSteps: number;
  error?: string;
}

export async function runFlow(filePath: string): Promise<FlowResult> {
  const { frontmatter, steps } = parseFlowFile(filePath);
  const name = frontmatter.name ?? filePath.split("/").pop() ?? "flow";

  // Auto-detect screen resolution
  const resolution = getScreenResolution();
  if (resolution) {
    initDeviceContext(resolution);
    console.log(`Screen resolution: ${resolution[0]}x${resolution[1]}`);
  }

  console.log(`\nFlow: ${name}`);
  if (frontmatter.appId) console.log(`App: ${frontmatter.appId}`);
  console.log(`Steps: ${steps.length}\n`);

  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];
    const label = typeof step === "string" ? step : Object.entries(step)[0].join(": ");
    console.log(`[${i + 1}/${steps.length}] ${label}`);

    const result = executeFlowStep(step, frontmatter, i);

    if (!result.success) {
      console.log(`  FAILED: ${result.message}`);
      return {
        name,
        success: false,
        stepsCompleted: i,
        totalSteps: steps.length,
        error: result.message,
      };
    }

    console.log(`  OK: ${result.message}`);

    // Brief pause between steps for UI to settle
    if (i < steps.length - 1 && typeof step !== "string" || (typeof step === "object" && !("wait" in step))) {
      await Bun.sleep(Config.STEP_DELAY * 1000);
    }
  }

  console.log(`\nFlow "${name}" completed successfully.`);
  return { name, success: true, stepsCompleted: steps.length, totalSteps: steps.length };
}
