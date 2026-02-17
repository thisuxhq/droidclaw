/**
 * Stuck-loop detection for the DroidClaw agent loop.
 *
 * Same algorithm as the CLI kernel.ts: sliding window of recent actions
 * and screen hashes to detect repetition, with context-aware recovery hints.
 */

export interface StuckDetector {
  recordAction(action: string, screenHash: string): void;
  isStuck(): boolean;
  getRecoveryHint(): string;
  getStuckCount(): number;
  reset(): void;
}

export function createStuckDetector(windowSize: number = 8): StuckDetector {
  const recentActions: string[] = [];
  const recentHashes: string[] = [];
  let unchangedCount = 0;

  return {
    recordAction(action: string, screenHash: string) {
      // Track screen-unchanged streaks
      if (recentHashes.length > 0 && recentHashes[recentHashes.length - 1] === screenHash) {
        unchangedCount++;
      } else {
        unchangedCount = 0;
      }

      recentActions.push(action);
      recentHashes.push(screenHash);
      if (recentActions.length > windowSize) recentActions.shift();
      if (recentHashes.length > windowSize) recentHashes.shift();
    },

    isStuck(): boolean {
      if (recentActions.length < 3) return false;

      // Check 1: All recent actions are identical
      const allSameAction = recentActions.slice(-3).every((a) => a === recentActions[recentActions.length - 1]);

      // Check 2: Screen hash hasn't changed for 3+ steps
      const allSameHash = unchangedCount >= 3;

      // Check 3: Repetition frequency (same action 3+ times in window)
      const freq = new Map<string, number>();
      for (const a of recentActions) freq.set(a, (freq.get(a) ?? 0) + 1);
      const maxFreq = Math.max(...freq.values());
      const highRepetition = maxFreq >= 3;

      return allSameAction || allSameHash || highRepetition;
    },

    getRecoveryHint(): string {
      // Context-aware recovery based on what actions are failing
      const failingTypes = new Set(
        recentActions.slice(-3).map((a) => a.split("(")[0])
      );

      let hint =
        "STUCK DETECTED: You have been repeating the same action or seeing the same screen. " +
        "Your current approach is NOT working.\n\n";

      if (failingTypes.has("tap") || failingTypes.has("longpress")) {
        hint +=
          "Your tap/press actions are having NO EFFECT. Likely causes:\n" +
          "- The action SUCCEEDED SILENTLY (copy/share/like buttons often work without screen changes). If so, MOVE ON.\n" +
          "- The element is not actually interactive at those coordinates.\n" +
          "- Use 'clipboard_set' to set clipboard text directly instead of UI copy buttons.\n" +
          "- Or just 'type' the text directly in the target app.\n\n";
      }

      if (failingTypes.has("swipe") || failingTypes.has("scroll")) {
        hint +=
          "Swiping is having no effect -- you may be at the end of scrollable content. " +
          "Try interacting with visible elements or navigate with 'back'/'home'.\n\n";
      }

      hint +=
        "Try a completely different approach: scroll to find new elements, go back, " +
        "use the home button, try a different app, or use programmatic actions " +
        "(clipboard_set, type, launch) instead of UI interactions.";

      return hint;
    },

    getStuckCount(): number {
      return unchangedCount;
    },

    reset() {
      recentActions.length = 0;
      recentHashes.length = 0;
      unchangedCount = 0;
    },
  };
}
