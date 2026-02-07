/**
 * Skills module for DroidClaw.
 * Multi-step smart actions that reduce LLM decision points and eliminate
 * entire categories of errors (coordinate guessing, wrong submit buttons, etc.)
 *
 * Skills:
 *   submit_message  — Find and tap the Send/Submit button in chat apps
 *   copy_visible_text — Read text from screen elements and set clipboard programmatically
 *   wait_for_content — Wait for new content to appear (AI responses, page loads)
 *   find_and_tap — Find an element by text label and tap it
 *   compose_email — Fill email fields in correct order (To, Subject, Body)
 */

import { existsSync, readFileSync } from "fs";
import { Config } from "./config.js";
import { runAdbCommand, getSwipeCoords, type ActionDecision, type ActionResult } from "./actions.js";
import { getInteractiveElements, type UIElement } from "./sanitizer.js";
import { SWIPE_DURATION_MS } from "./constants.js";

/**
 * Routes a skill action to the appropriate skill function.
 */
export function executeSkill(
  decision: ActionDecision,
  elements: UIElement[]
): ActionResult {
  const skill = decision.skill ?? decision.action;
  console.log(`Executing multi-step action: ${skill}`);

  switch (skill) {
    case "read_screen":
      return readScreen(elements);
    case "submit_message":
      return submitMessage(elements);
    case "copy_visible_text":
      return copyVisibleText(decision, elements);
    case "wait_for_content":
      return waitForContent(elements);
    case "find_and_tap":
      return findAndTap(decision, elements);
    case "compose_email":
      return composeEmail(decision, elements);
    default:
      return { success: false, message: `Unknown skill: ${skill}` };
  }
}

// ===========================================
// Helper: re-scan screen
// ===========================================

/**
 * Sets clipboard text via ADB with proper shell escaping.
 * ADB shell joins args into a single string, so parentheses/quotes break it.
 * Wrapping in single quotes and escaping internal quotes fixes this.
 */
function safeClipboardSet(text: string): void {
  const escaped = text.replaceAll("'", "'\\''");
  runAdbCommand(["shell", `cmd clipboard set-text '${escaped}'`]);
}

function rescanScreen(): UIElement[] {
  try {
    runAdbCommand(["shell", "uiautomator", "dump", Config.SCREEN_DUMP_PATH]);
    runAdbCommand(["pull", Config.SCREEN_DUMP_PATH, Config.LOCAL_DUMP_PATH]);
  } catch {
    console.log("Warning: ADB screen capture failed during skill re-scan.");
    return [];
  }
  if (!existsSync(Config.LOCAL_DUMP_PATH)) return [];
  const xmlContent = readFileSync(Config.LOCAL_DUMP_PATH, "utf-8");
  return getInteractiveElements(xmlContent);
}

// ===========================================
// Skill 0: read_screen (scroll + collect all text)
// ===========================================

function readScreen(elements: UIElement[]): ActionResult {
  const allTexts: string[] = [];
  const seenTexts = new Set<string>();

  function collectTexts(els: UIElement[]): number {
    let added = 0;
    for (const el of els) {
      if (el.text && !seenTexts.has(el.text)) {
        seenTexts.add(el.text);
        allTexts.push(el.text);
        added++;
      }
    }
    return added;
  }

  // 1. Collect from initial screen
  collectTexts(elements);

  // 2. Scroll down and collect until no new content
  const swipeCoords = getSwipeCoords();
  const upCoords = swipeCoords["up"]; // swipe up = scroll down = see more below
  const maxScrolls = 5;
  let scrollsDone = 0;

  for (let i = 0; i < maxScrolls; i++) {
    runAdbCommand([
      "shell", "input", "swipe",
      String(upCoords[0]), String(upCoords[1]),
      String(upCoords[2]), String(upCoords[3]),
      SWIPE_DURATION_MS,
    ]);
    Bun.sleepSync(1500);
    scrollsDone++;

    const newElements = rescanScreen();
    const added = collectTexts(newElements);
    console.log(`read_screen: Scroll ${scrollsDone} — found ${added} new text elements`);

    if (added === 0) break;
  }

  const combinedText = allTexts.join("\n");

  // 3. Copy to clipboard for easy access
  if (combinedText.length > 0) {
    safeClipboardSet(combinedText);
  }

  return {
    success: true,
    message: `Read ${allTexts.length} text elements across ${scrollsDone} scrolls (${combinedText.length} chars), copied to clipboard`,
    data: combinedText,
  };
}

// ===========================================
// Skill 1: submit_message
// ===========================================

const SEND_BUTTON_PATTERN = /send|submit|post|arrow|paper.?plane/i;

function submitMessage(elements: UIElement[]): ActionResult {
  // 1. Search for Send/Submit button by text
  let candidates = elements.filter(
    (el) =>
      el.enabled &&
      (el.clickable || el.action === "tap") &&
      (SEND_BUTTON_PATTERN.test(el.text) || SEND_BUTTON_PATTERN.test(el.id))
  );

  // 2. If no text match, look for clickable elements in the bottom 20% of screen
  //    near the right side (common Send button position)
  if (candidates.length === 0) {
    const screenBottom = elements
      .filter((el) => el.enabled && el.clickable)
      .sort((a, b) => b.center[1] - a.center[1]);

    // Take elements in the bottom 20% by Y coordinate
    if (screenBottom.length > 0) {
      const maxY = screenBottom[0].center[1];
      const threshold = maxY * 0.8;
      candidates = screenBottom.filter((el) => el.center[1] >= threshold);
      // Prefer rightmost element (Send buttons are usually on the right)
      candidates.sort((a, b) => b.center[0] - a.center[0]);
    }
  }

  if (candidates.length === 0) {
    return {
      success: false,
      message: "Could not find a Send/Submit button on screen",
    };
  }

  // 3. Tap the best match
  const target = candidates[0];
  const [x, y] = target.center;
  console.log(
    `submit_message: Tapping "${target.text}" at (${x}, ${y})`
  );
  runAdbCommand(["shell", "input", "tap", String(x), String(y)]);

  // 4. Wait for response to generate
  console.log("submit_message: Waiting 6s for response...");
  Bun.sleepSync(6000);

  // 5. Re-scan screen and check for new content
  const newElements = rescanScreen();
  const originalTexts = new Set(elements.map((el) => el.text).filter(Boolean));
  const newTexts = newElements
    .map((el) => el.text)
    .filter((t) => t && !originalTexts.has(t));

  if (newTexts.length > 0) {
    const summary = newTexts.slice(0, 3).join("; ");
    return {
      success: true,
      message: `Tapped "${target.text}" and new content appeared: ${summary}`,
      data: summary,
    };
  }

  return {
    success: true,
    message: `Tapped "${target.text}" at (${x}, ${y}). No new content yet — may still be loading.`,
  };
}

// ===========================================
// Skill 2: copy_visible_text
// ===========================================

function copyVisibleText(
  decision: ActionDecision,
  elements: UIElement[]
): ActionResult {
  // 1. Filter for readable text elements
  let textElements = elements.filter(
    (el) => el.text && el.action === "read"
  );

  // 2. If query provided, filter to matching elements
  if (decision.query) {
    const query = decision.query.toLowerCase();
    textElements = textElements.filter((el) =>
      el.text.toLowerCase().includes(query)
    );
  }

  // If no read-only text, include all elements with text
  if (textElements.length === 0) {
    textElements = elements.filter((el) => el.text);
    if (decision.query) {
      const query = decision.query.toLowerCase();
      textElements = textElements.filter((el) =>
        el.text.toLowerCase().includes(query)
      );
    }
  }

  if (textElements.length === 0) {
    return {
      success: false,
      message: decision.query
        ? `No text matching "${decision.query}" found on screen`
        : "No readable text found on screen",
    };
  }

  // 3. Sort by vertical position (top to bottom)
  textElements.sort((a, b) => a.center[1] - b.center[1]);

  // 4. Concatenate text
  const combinedText = textElements.map((el) => el.text).join("\n");

  // 5. Set clipboard programmatically
  console.log(
    `copy_visible_text: Copying ${textElements.length} text elements (${combinedText.length} chars)`
  );
  safeClipboardSet(combinedText);

  return {
    success: true,
    message: `Copied ${textElements.length} text elements to clipboard (${combinedText.length} chars)`,
    data: combinedText,
  };
}

// ===========================================
// Skill 3: wait_for_content
// ===========================================

function waitForContent(elements: UIElement[]): ActionResult {
  // 1. Record current element texts
  const originalTexts = new Set(elements.map((el) => el.text).filter(Boolean));

  // 2. Poll up to 5 times (3s intervals = 15s max)
  for (let i = 0; i < 5; i++) {
    console.log(
      `wait_for_content: Waiting 3s... (attempt ${i + 1}/5)`
    );
    Bun.sleepSync(3000);

    // Re-scan screen
    const newElements = rescanScreen();
    const newTexts = newElements
      .map((el) => el.text)
      .filter((t) => t && !originalTexts.has(t));

    // Check if meaningful new content appeared (>20 chars total)
    const totalNewChars = newTexts.reduce((sum, t) => sum + t.length, 0);
    if (totalNewChars > 20) {
      const summary = newTexts.slice(0, 5).join("; ");
      console.log(
        `wait_for_content: Found ${newTexts.length} new text elements (${totalNewChars} chars)`
      );
      return {
        success: true,
        message: `New content appeared after ${(i + 1) * 3}s: ${summary}`,
        data: summary,
      };
    }
  }

  return {
    success: false,
    message: "No new content appeared after 15s",
  };
}

// ===========================================
// Skill 4: find_and_tap
// ===========================================

function findAndTap(
  decision: ActionDecision,
  elements: UIElement[]
): ActionResult {
  const query = decision.query;
  if (!query) {
    return { success: false, message: "find_and_tap requires a query" };
  }

  const queryLower = query.toLowerCase();

  // 1. Search elements for text matching query
  const matches = elements.filter(
    (el) => el.text && el.text.toLowerCase().includes(queryLower)
  );

  if (matches.length === 0) {
    // Return available element texts to help the LLM
    const available = elements
      .filter((el) => el.text)
      .map((el) => el.text)
      .slice(0, 15);
    return {
      success: false,
      message: `No element matching "${query}" found. Available: ${available.join(", ")}`,
    };
  }

  // 2. Score matches
  const scored = matches.map((el) => {
    let score = 0;
    if (el.enabled) score += 10;
    if (el.clickable || el.longClickable) score += 5;
    if (el.text.toLowerCase() === queryLower) score += 20; // exact match
    else score += 5; // partial match
    return { el, score };
  });

  // 3. Pick highest-scoring match
  scored.sort((a, b) => b.score - a.score);
  const best = scored[0].el;
  const [x, y] = best.center;

  // 4. Tap it
  console.log(
    `find_and_tap: Tapping "${best.text}" at (${x}, ${y}) [score: ${scored[0].score}]`
  );
  runAdbCommand(["shell", "input", "tap", String(x), String(y)]);

  return {
    success: true,
    message: `Found and tapped "${best.text}" at (${x}, ${y})`,
    data: best.text,
  };
}

// ===========================================
// Skill 5: compose_email
// ===========================================

/** Patterns to identify email compose fields by resource ID */
const TO_FIELD_PATTERN = /to|recipient/i;
const SUBJECT_FIELD_PATTERN = /subject/i;
const BODY_FIELD_PATTERN = /body|compose_area|compose_edit|message_content/i;

/** Patterns to identify fields by hint text */
const TO_HINT_PATTERN = /^to$|recipient|email.?address/i;
const SUBJECT_HINT_PATTERN = /subject/i;
const BODY_HINT_PATTERN = /compose|body|message|write/i;

/**
 * Finds an editable field matching the given ID and hint patterns.
 * Falls back to positional matching if patterns don't match.
 */
function findEmailField(
  editables: UIElement[],
  idPattern: RegExp,
  hintPattern: RegExp
): UIElement | undefined {
  // Try resource ID first (most reliable)
  const byId = editables.find((el) => idPattern.test(el.id));
  if (byId) return byId;
  // Try hint text
  const byHint = editables.find((el) => el.hint && hintPattern.test(el.hint));
  if (byHint) return byHint;
  // Try visible label/text
  const byText = editables.find((el) => idPattern.test(el.text));
  if (byText) return byText;
  return undefined;
}

/** Try to extract an email address from a string */
function extractEmail(text: string): string | null {
  const match = text.match(/[\w.+-]+@[\w.-]+\.\w{2,}/);
  return match ? match[0] : null;
}

function composeEmail(
  decision: ActionDecision,
  elements: UIElement[]
): ActionResult {
  // Resolve email address: try query first, then extract from text
  let emailAddress = decision.query;
  const bodyContent = decision.text;

  if (!emailAddress && bodyContent) {
    const extracted = extractEmail(bodyContent);
    if (extracted) {
      emailAddress = extracted;
      console.log(`compose_email: Extracted email "${emailAddress}" from text field`);
    }
  }

  if (!emailAddress) {
    return {
      success: false,
      message: "compose_email requires query (email address). Example: {\"action\": \"compose_email\", \"query\": \"user@example.com\"}",
    };
  }

  // Always use mailto: intent — this is the most reliable path.
  // It opens the default email app with To pre-filled, regardless of current screen.
  console.log(`compose_email: Launching mailto:${emailAddress}`);
  runAdbCommand([
    "shell", "am", "start", "-a", "android.intent.action.SENDTO",
    "-d", `mailto:${emailAddress}`,
  ]);
  Bun.sleepSync(2500);

  // Re-scan to find the compose screen
  const freshElements = rescanScreen();
  const editables = freshElements
    .filter((el) => el.editable && el.enabled)
    .sort((a, b) => a.center[1] - b.center[1]);

  if (editables.length === 0) {
    return { success: false, message: "Launched email compose but no editable fields appeared" };
  }

  // Find the body field — mailto: already handled the To field
  let bodyField = findEmailField(editables, BODY_FIELD_PATTERN, BODY_HINT_PATTERN);
  if (!bodyField) {
    // Positional fallback: body is the last/largest editable field
    bodyField = editables[editables.length - 1];
  }

  const [bx, by] = bodyField.center;
  console.log(`compose_email: Tapping Body field at (${bx}, ${by})`);
  runAdbCommand(["shell", "input", "tap", String(bx), String(by)]);
  Bun.sleepSync(300);

  // Paste body content — use explicit text if provided, otherwise paste clipboard
  if (bodyContent) {
    safeClipboardSet(bodyContent);
    Bun.sleepSync(200);
  }
  runAdbCommand(["shell", "input", "keyevent", "279"]); // KEYCODE_PASTE

  return {
    success: true,
    message: `Email compose opened to ${emailAddress}, body pasted`,
  };
}
