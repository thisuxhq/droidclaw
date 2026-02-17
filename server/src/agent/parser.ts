/**
 * Stage 1: Deterministic goal parser for DroidClaw pipeline.
 *
 * Converts natural-language goals into Android intents or app launches
 * using regex patterns. Zero LLM calls.
 *
 * Patterns are DATA-DRIVEN: only enabled when the device reports that
 * an app supports the corresponding intent scheme (via installed apps
 * with intent capabilities).
 */

import type {
  InstalledApp,
  PipelineResult,
  DeviceCapabilities,
} from "@droidclaw/shared";

// ─── Capability Extraction ───────────────────────────────────

/**
 * Build a DeviceCapabilities object from the device's installed apps.
 * Pre-indexes scheme handlers and supported actions for fast pattern matching.
 */
export function buildCapabilities(apps: InstalledApp[]): DeviceCapabilities {
  const schemeHandlers: Record<string, string[]> = {};
  const supportedActions = new Set<string>();

  for (const app of apps) {
    if (!app.intents) continue;
    for (const intent of app.intents) {
      if (intent.startsWith("VIEW:")) {
        const scheme = intent.slice(5);
        if (!schemeHandlers[scheme]) schemeHandlers[scheme] = [];
        schemeHandlers[scheme].push(app.packageName);
      } else if (intent.startsWith("SENDTO:")) {
        const scheme = intent.slice(7);
        if (!schemeHandlers[scheme]) schemeHandlers[scheme] = [];
        schemeHandlers[scheme].push(app.packageName);
      } else if (intent.startsWith("SEND:")) {
        supportedActions.add(intent);
      } else {
        supportedActions.add(intent);
      }
    }
  }

  return { apps, schemeHandlers, supportedActions };
}

// ─── App Name Resolution ─────────────────────────────────────

function resolveApp(
  name: string,
  apps: InstalledApp[]
): InstalledApp | undefined {
  const lower = name.toLowerCase();
  const exact = apps.find((a) => a.label.toLowerCase() === lower);
  if (exact) return exact;
  return apps.find((a) => a.label.toLowerCase().includes(lower));
}

// ─── Regex Patterns ──────────────────────────────────────────

type PatternMatcher = (
  goal: string,
  caps: DeviceCapabilities
) => PipelineResult | null;

const matchCall: PatternMatcher = (goal, caps) => {
  if (!caps.schemeHandlers["tel"]) return null;
  const m = goal.match(/^(?:call|phone|dial)\s+(.+)$/i);
  if (!m) return null;
  const target = m[1].trim();
  const isNumber = /^[\d\s\+\-\(\)]+$/.test(target);
  if (isNumber) {
    const cleaned = target.replace(/[\s\-\(\)]/g, "");
    return {
      stage: "parser",
      type: "intent",
      intent: {
        intentAction: "android.intent.action.DIAL",
        uri: `tel:${cleaned}`,
      },
    };
  }
  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.DIAL",
      uri: `tel:${encodeURIComponent(target)}`,
    },
  };
};

const matchSms: PatternMatcher = (goal, caps) => {
  if (!caps.schemeHandlers["sms"] && !caps.schemeHandlers["smsto"])
    return null;
  const m = goal.match(
    /^(?:text|sms|send\s+(?:a\s+)?(?:text|sms|message)\s+to)\s+([\d\+\-\s\(\)]+?)(?:\s+(?:saying|with|message|that says)\s+(.+))?$/i
  );
  if (!m) return null;
  const number = m[1].replace(/[\s\-\(\)]/g, "");
  const body = m[2]?.trim();
  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.SENDTO",
      uri: `smsto:${number}`,
      extras: body ? { sms_body: body } : undefined,
    },
  };
};

const matchWhatsApp: PatternMatcher = (goal, caps) => {
  const hasWa =
    caps.schemeHandlers["whatsapp"] ||
    caps.apps.some((a) => a.packageName === "com.whatsapp");
  if (!hasWa) return null;
  const m = goal.match(
    /^(?:whatsapp|send\s+(?:a\s+)?whatsapp\s+(?:message\s+)?to)\s+([\d\+\-\s\(\)]+?)(?:\s+(?:saying|with|message|that says)\s+(.+))?$/i
  );
  if (!m) return null;
  const number = m[1].replace(/[\s\-\(\)]/g, "");
  const text = m[2]?.trim() ?? "";
  const encoded = text ? `?text=${encodeURIComponent(text)}` : "";
  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.VIEW",
      uri: `https://wa.me/${number}${encoded}`,
    },
  };
};

const matchNavigation: PatternMatcher = (goal, caps) => {
  if (!caps.schemeHandlers["google.navigation"]) return null;
  const m = goal.match(
    /^(?:navigate|drive|directions?|take me)\s+to\s+(.+)$/i
  );
  if (!m) return null;
  const place = m[1].trim();
  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.VIEW",
      uri: `google.navigation:q=${encodeURIComponent(place)}&mode=d`,
    },
  };
};

const matchMapSearch: PatternMatcher = (goal, caps) => {
  if (!caps.schemeHandlers["geo"]) return null;
  const m = goal.match(
    /^(?:find|search|look for|show me)\s+(.+?)(?:\s+(?:nearby|near me|on maps|around here))$/i
  );
  if (!m) return null;
  const query = m[1].trim();
  if (query.length > 50) return null;
  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.VIEW",
      uri: `geo:0,0?q=${encodeURIComponent(query)}`,
    },
  };
};

const matchAlarm: PatternMatcher = (goal, caps) => {
  if (!caps.supportedActions.has("android.intent.action.SET_ALARM"))
    return null;
  const m = goal.match(
    /^set\s+(?:an?\s+)?alarm\s+(?:for|at)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?(?:\s+(?:called|named|labeled|saying)\s+(.+))?$/i
  );
  if (!m) return null;
  let hour = parseInt(m[1], 10);
  const minutes = m[2] ? parseInt(m[2], 10) : 0;
  const ampm = m[3]?.toLowerCase();
  const label = m[4]?.trim();

  if (ampm === "pm" && hour < 12) hour += 12;
  if (ampm === "am" && hour === 12) hour = 0;

  const extras: Record<string, string> = {
    "android.intent.extra.alarm.HOUR": String(hour),
    "android.intent.extra.alarm.MINUTES": String(minutes),
  };
  if (label) extras["android.intent.extra.alarm.MESSAGE"] = label;

  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.SET_ALARM",
      extras,
    },
  };
};

const matchTimer: PatternMatcher = (goal, caps) => {
  if (!caps.supportedActions.has("android.intent.action.SET_TIMER"))
    return null;
  const m = goal.match(
    /^set\s+(?:a\s+)?timer\s+(?:for\s+)?(\d+)\s*(seconds?|minutes?|mins?|hours?|hrs?)(?:\s+(?:called|named|labeled|saying)\s+(.+))?$/i
  );
  if (!m) return null;
  let seconds = parseInt(m[1], 10);
  const unit = m[2].toLowerCase();
  if (unit.startsWith("min")) seconds *= 60;
  if (unit.startsWith("hour") || unit.startsWith("hr")) seconds *= 3600;
  const label = m[3]?.trim();

  const extras: Record<string, string> = {
    "android.intent.extra.alarm.LENGTH": String(seconds),
  };
  if (label) extras["android.intent.extra.alarm.MESSAGE"] = label;

  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.SET_TIMER",
      extras,
    },
  };
};

const matchEmail: PatternMatcher = (goal, caps) => {
  if (!caps.schemeHandlers["mailto"]) return null;
  const m = goal.match(
    /^(?:email|mail|send\s+(?:an?\s+)?email\s+to)\s+([\w.\-+]+@[\w.\-]+)(?:\s+(?:about|subject|with subject|saying)\s+(.+))?$/i
  );
  if (!m) return null;
  const email = m[1];
  const subject = m[2]?.trim();
  return {
    stage: "parser",
    type: "intent",
    intent: {
      intentAction: "android.intent.action.SENDTO",
      uri: `mailto:${email}`,
      extras: subject
        ? { "android.intent.extra.SUBJECT": subject }
        : undefined,
    },
  };
};

const matchOpenApp: PatternMatcher = (goal, caps) => {
  const m = goal.match(
    /^(?:open|launch|start|go to)\s+(?:the\s+)?(.+?)(?:\s+app)?$/i
  );
  if (!m) return null;
  const name = m[1].trim();

  if (/^https?:\/\//i.test(name)) {
    return { stage: "parser", type: "open_url", url: name };
  }

  const app = resolveApp(name, caps.apps);
  if (app) {
    return { stage: "parser", type: "launch", packageName: app.packageName };
  }
  return null;
};

const matchSettings: PatternMatcher = (goal, _caps) => {
  const settingKeywords: Record<string, string> = {
    wifi: "wifi",
    "wi-fi": "wifi",
    bluetooth: "bluetooth",
    display: "display",
    brightness: "display",
    sound: "sound",
    volume: "sound",
    battery: "battery",
    location: "location",
    gps: "location",
    apps: "apps",
    applications: "apps",
    date: "date",
    time: "date",
    accessibility: "accessibility",
    developer: "developer",
    "do not disturb": "dnd",
    dnd: "dnd",
    network: "network",
    storage: "storage",
    security: "security",
  };

  const m = goal.match(/^(?:open\s+)?(.+?)\s+settings$/i);
  if (!m) return null;
  const key = m[1].trim().toLowerCase();
  const setting = settingKeywords[key];
  if (setting) {
    return { stage: "parser", type: "open_settings", setting };
  }
  return null;
};

const matchOpenUrl: PatternMatcher = (goal, _caps) => {
  const m = goal.match(
    /^(?:open|go to|visit|navigate to)\s+(https?:\/\/\S+)$/i
  );
  if (!m) return null;
  return { stage: "parser", type: "open_url", url: m[1] };
};

// ─── Pattern Registry ────────────────────────────────────────

const PATTERNS: PatternMatcher[] = [
  matchOpenUrl,
  matchCall,
  matchSms,
  matchWhatsApp,
  matchEmail,
  matchNavigation,
  matchMapSearch,
  matchAlarm,
  matchTimer,
  matchSettings,
  matchOpenApp,
];

// ─── Public API ──────────────────────────────────────────────

/**
 * Stage 1: Parse a goal deterministically.
 * Returns a PipelineResult if a pattern matches, or { type: "passthrough" }
 * if no pattern matches and the goal should proceed to Stage 2.
 */
export function parseGoal(
  goal: string,
  caps: DeviceCapabilities
): PipelineResult {
  const trimmed = goal.trim();
  for (const matcher of PATTERNS) {
    const result = matcher(trimmed, caps);
    if (result) return result;
  }
  return { stage: "parser", type: "passthrough" };
}
