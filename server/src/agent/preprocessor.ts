/**
 * Goal preprocessor for DroidClaw agent loop.
 *
 * Intercepts simple goals (like "open youtube") and executes direct
 * actions before the LLM loop starts. This avoids wasting 20 steps
 * on what should be a 2-step task, especially with weaker LLMs that
 * navigate via UI instead of using programmatic launch commands.
 */

import { sessions } from "../ws/sessions.js";

// ─── App Name → Package Name Map ────────────────────────────

const APP_PACKAGES: Record<string, string> = {
  youtube: "com.google.android.youtube",
  gmail: "com.google.android.gm",
  chrome: "com.android.chrome",
  maps: "com.google.android.apps.maps",
  photos: "com.google.android.apps.photos",
  drive: "com.google.android.apps.docs",
  calendar: "com.google.android.calendar",
  contacts: "com.google.android.contacts",
  messages: "com.google.android.apps.messaging",
  phone: "com.google.android.dialer",
  clock: "com.google.android.deskclock",
  calculator: "com.google.android.calculator",
  camera: "com.android.camera",
  settings: "com.android.settings",
  files: "com.google.android.apps.nbu.files",
  play: "com.android.vending",
  "play store": "com.android.vending",
  "google play": "com.android.vending",
  whatsapp: "com.whatsapp",
  telegram: "org.telegram.messenger",
  instagram: "com.instagram.android",
  facebook: "com.facebook.katana",
  twitter: "com.twitter.android",
  x: "com.twitter.android",
  spotify: "com.spotify.music",
  netflix: "com.netflix.mediaclient",
  tiktok: "com.zhiliaoapp.musically",
  snapchat: "com.snapchat.android",
  reddit: "com.reddit.frontpage",
  discord: "com.discord",
  slack: "com.Slack",
  zoom: "us.zoom.videomeetings",
  teams: "com.microsoft.teams",
  outlook: "com.microsoft.office.outlook",
  "google meet": "com.google.android.apps.tachyon",
  meet: "com.google.android.apps.tachyon",
  keep: "com.google.android.keep",
  notes: "com.google.android.keep",
  sheets: "com.google.android.apps.docs.editors.sheets",
  docs: "com.google.android.apps.docs.editors.docs",
  slides: "com.google.android.apps.docs.editors.slides",
  translate: "com.google.android.apps.translate",
  weather: "com.google.android.apps.weather",
  news: "com.google.android.apps.magazines",
  podcasts: "com.google.android.apps.podcasts",
  fitbit: "com.fitbit.FitbitMobile",
  uber: "com.ubercab",
  lyft: "me.lyft.android",
  amazon: "com.amazon.mShop.android.shopping",
  ebay: "com.ebay.mobile",
  linkedin: "com.linkedin.android",
  pinterest: "com.pinterest",
  twitch: "tv.twitch.android.app",
};

// ─── Goal Pattern Matching ───────────────────────────────────

interface PreprocessResult {
  /** Whether the preprocessor handled the goal */
  handled: boolean;
  /** Command sent to device (if any) */
  command?: Record<string, unknown>;
  /** Updated goal text for the LLM (optional) */
  refinedGoal?: string;
}

/**
 * Try to find a known app name at the start of a goal string.
 * Returns the package name and remaining text, or null.
 */
function matchAppName(lower: string): { pkg: string; appName: string; rest: string } | null {
  // Try longest app names first (e.g. "google meet" before "meet")
  const sorted = Object.keys(APP_PACKAGES).sort((a, b) => b.length - a.length);

  for (const name of sorted) {
    // Match: "open <app> [app] and <rest>" or "open <app> [app]"
    const pattern = new RegExp(
      `^(?:open|launch|start|go to)\\s+(?:the\\s+)?${escapeRegex(name)}(?:\\s+app)?(?:\\s+(?:and|then)\\s+(.+))?$`
    );
    const m = lower.match(pattern);
    if (m) {
      return { pkg: APP_PACKAGES[name], appName: name, rest: m[1]?.trim() ?? "" };
    }
  }
  return null;
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Attempt to preprocess a goal before the LLM loop.
 *
 * Three outcomes:
 *  1. { handled: true, refinedGoal: undefined } — goal fully handled (pure "open X")
 *  2. { handled: true, refinedGoal: "..." }     — app launched, LLM continues with refined goal
 *  3. { handled: false }                        — preprocessor can't help, LLM gets full goal
 */
export async function preprocessGoal(
  deviceId: string,
  goal: string
): Promise<PreprocessResult> {
  const lower = goal.toLowerCase().trim();

  // ── Pattern: "open <app> [and <remaining>]" ───────────────
  const appMatch = matchAppName(lower);

  if (appMatch) {
    try {
      await sessions.sendCommand(deviceId, {
        type: "launch",
        packageName: appMatch.pkg,
      });

      if (appMatch.rest) {
        // Compound goal: app launched, pass remaining instructions to LLM
        console.log(`[Preprocessor] Launched ${appMatch.pkg}, refined goal: ${appMatch.rest}`);
        return {
          handled: true,
          command: { type: "launch", packageName: appMatch.pkg },
          refinedGoal: appMatch.rest,
        };
      }

      // Pure "open X" — fully handled
      console.log(`[Preprocessor] Launched ${appMatch.pkg} for goal: ${goal}`);
      return { handled: true, command: { type: "launch", packageName: appMatch.pkg } };
    } catch (err) {
      console.warn(`[Preprocessor] Failed to launch ${appMatch.pkg}: ${err}`);
      // Fall through to LLM
    }
  }

  // ── Pattern: "open <url>" or "go to <url>" ────────────────
  const urlMatch = lower.match(
    /^(?:open|go to|visit|navigate to)\s+(https?:\/\/\S+)$/
  );

  if (urlMatch) {
    const url = urlMatch[1];
    try {
      await sessions.sendCommand(deviceId, { type: "open_url", url });
      console.log(`[Preprocessor] Opened URL: ${url}`);
      return { handled: true, command: { type: "open_url", url } };
    } catch (err) {
      console.warn(`[Preprocessor] Failed to open URL: ${err}`);
    }
  }

  return { handled: false };
}
