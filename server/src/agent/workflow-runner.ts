import { sessions } from "../ws/sessions.js";
import { db } from "../db.js";
import { workflowRun } from "../schema.js";
import { eq } from "drizzle-orm";
import { runPipeline, type PipelineOptions } from "./pipeline.js";
import type { LLMConfig } from "./llm.js";
import { activeSessions } from "./active-sessions.js";

export interface WorkflowStep {
  goal: string;
  app?: string;
  maxSteps?: number;
  formData?: Record<string, string>;
  retries?: number; // max retry attempts on failure (default: 0 = no retry)
}

export interface RunWorkflowOptions {
  runId: string;
  deviceId: string;
  persistentDeviceId?: string;
  userId: string;
  name: string;
  steps: WorkflowStep[];
  llmConfig: LLMConfig;
  signal: AbortSignal;
}

function buildGoal(step: WorkflowStep): string {
  let goal = step.goal;
  if (step.formData && Object.keys(step.formData).length > 0) {
    const lines = Object.entries(step.formData)
      .map(([key, value]) => `- ${key}: ${value}`)
      .join("\n");
    goal += `\n\nFORM DATA TO FILL:\n${lines}\n\nFind each field on screen and enter the corresponding value.`;
  }
  return goal;
}

export async function runWorkflowServer(options: RunWorkflowOptions): Promise<void> {
  const { runId, deviceId, persistentDeviceId, userId, name, steps, llmConfig, signal } = options;
  const stepResults: Array<{ goal: string; success: boolean; stepsUsed: number; error?: string }> = [];

  sessions.notifyDashboard(userId, {
    type: "workflow_started",
    runId,
    name,
    wfType: "workflow",
    totalSteps: steps.length,
  } as any);

  for (let i = 0; i < steps.length; i++) {
    if (signal.aborted) {
      await db.update(workflowRun).set({ status: "stopped", completedAt: new Date() }).where(eq(workflowRun.id, runId));
      sessions.notifyDashboard(userId, { type: "workflow_stopped", runId } as any);
      return;
    }

    const step = steps[i];
    const effectiveGoal = buildGoal(step);

    sessions.notifyDashboard(userId, {
      type: "workflow_step_start",
      runId,
      stepIndex: i,
      goal: step.goal,
    } as any);

    const maxRetries = step.retries ?? 0;
    let stepSuccess = false;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      if (signal.aborted) break;

      // Launch app on each attempt (fresh state for retries)
      if (step.app) {
        try {
          await sessions.sendCommand(deviceId, { type: "launch", packageName: step.app });
          await new Promise((r) => setTimeout(r, 2000));
        } catch (err) {
          console.warn(`[Workflow] Failed to launch ${step.app}: ${err}`);
        }
      }

      try {
        const result = await runPipeline({
          deviceId,
          persistentDeviceId,
          userId,
          goal: effectiveGoal,
          llmConfig,
          maxSteps: step.maxSteps,
          signal,
        });

        if (result.success) {
          stepResults.push({ goal: step.goal, success: true, stepsUsed: result.stepsUsed });
          stepSuccess = true;
          break; // Success — move to next step
        }

        // Failed — log attempt and retry if attempts remain
        if (attempt < maxRetries) {
          console.log(`[Workflow] Step ${i} attempt ${attempt + 1}/${maxRetries + 1} failed, retrying...`);
          sessions.notifyDashboard(userId, {
            type: "workflow_step_retry",
            runId, stepIndex: i,
            attempt: attempt + 1,
            maxRetries: maxRetries + 1,
            stepsUsed: result.stepsUsed,
          } as any);
        } else {
          // All retries exhausted
          stepResults.push({ goal: step.goal, success: false, stepsUsed: result.stepsUsed });
        }
      } catch (err) {
        if (attempt < maxRetries) {
          console.log(`[Workflow] Step ${i} attempt ${attempt + 1}/${maxRetries + 1} threw error, retrying...`);
          sessions.notifyDashboard(userId, {
            type: "workflow_step_retry",
            runId, stepIndex: i,
            attempt: attempt + 1,
            maxRetries: maxRetries + 1,
            stepsUsed: 0,
          } as any);
        } else {
          stepResults.push({ goal: step.goal, success: false, stepsUsed: 0, error: String(err) });
        }
      }
    }

    // Update DB and check result
    await db.update(workflowRun).set({
      currentStep: i + 1,
      stepResults: stepResults,
    }).where(eq(workflowRun.id, runId));

    sessions.notifyDashboard(userId, {
      type: "workflow_step_done",
      runId,
      stepIndex: i,
      success: stepSuccess,
      stepsUsed: stepResults[stepResults.length - 1]?.stepsUsed ?? 0,
    } as any);

    if (!stepSuccess) {
      await db.update(workflowRun).set({
        status: "failed",
        stepResults,
        completedAt: new Date(),
      }).where(eq(workflowRun.id, runId));

      sessions.notifyDashboard(userId, {
        type: "workflow_completed",
        runId,
        success: false,
        stepResults,
      } as any);
      return;
    }
  }

  // All steps completed successfully
  await db.update(workflowRun).set({
    status: "completed",
    stepResults,
    completedAt: new Date(),
  }).where(eq(workflowRun.id, runId));

  sessions.notifyDashboard(userId, {
    type: "workflow_completed",
    runId,
    success: true,
    stepResults,
  } as any);
}
