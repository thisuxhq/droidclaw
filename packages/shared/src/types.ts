export interface UIElement {
  id: string;
  text: string;
  type: string;
  bounds: string;
  center: [number, number];
  size: [number, number];
  clickable: boolean;
  editable: boolean;
  enabled: boolean;
  checked: boolean;
  focused: boolean;
  selected: boolean;
  scrollable: boolean;
  longClickable: boolean;
  password: boolean;
  hint: string;
  action: "tap" | "type" | "longpress" | "scroll" | "read";
  parent: string;
  depth: number;
}

export interface ActionDecision {
  action: string;
  coordinates?: [number, number];
  text?: string;
  direction?: string;
  reason?: string;
  package?: string;
  activity?: string;
  uri?: string;
  extras?: Record<string, string>;
  command?: string;
  filename?: string;
  think?: string;
  plan?: string[];
  planProgress?: string;
  skill?: string;
  query?: string;
  url?: string;
  path?: string;
  source?: string;
  dest?: string;
  code?: number;
  setting?: string;
  /** Android intent action (e.g. "android.intent.action.VIEW") */
  intentAction?: string;
  /** MIME type for intent (e.g. "text/plain") */
  intentType?: string;
}

// ─── Pipeline Types ──────────────────────────────────────────

/** Result from Stage 1 (deterministic parser) or Stage 2 (LLM classifier) */
export type PipelineResult =
  | { stage: "parser" | "classifier"; type: "intent"; intent: IntentCommand }
  | { stage: "parser" | "classifier"; type: "launch"; packageName: string }
  | { stage: "parser"; type: "open_url"; url: string }
  | { stage: "parser"; type: "open_settings"; setting: string }
  | { stage: "classifier"; type: "ui"; app: string; subGoal: string }
  | { stage: "parser" | "classifier"; type: "done"; reason: string }
  | { stage: "parser" | "classifier"; type: "passthrough" };

export interface IntentCommand {
  intentAction: string;
  uri?: string;
  intentType?: string;
  extras?: Record<string, string>;
  packageName?: string;
}

/** Device capabilities extracted from installed apps + intents */
export interface DeviceCapabilities {
  apps: InstalledApp[];
  /** Map of URI scheme → list of package names that handle it */
  schemeHandlers: Record<string, string[]>;
  /** Set of special intent actions supported (e.g. "android.intent.action.SET_ALARM") */
  supportedActions: Set<string>;
}

export interface ActionResult {
  success: boolean;
  message: string;
  data?: string;
}

export interface DeviceInfo {
  model: string;
  manufacturer: string;
  androidVersion: string;
  screenWidth: number;
  screenHeight: number;
  batteryLevel: number;
  isCharging: boolean;
}

export interface InstalledApp {
  packageName: string;
  label: string;
  /** Supported intents, e.g. ["VIEW:whatsapp", "SEND:text/plain", "android.intent.action.SET_ALARM"] */
  intents?: string[];
}

export interface ScreenState {
  elements: UIElement[];
  screenshot?: string;
  packageName?: string;
  fallbackReason?: string;
}
