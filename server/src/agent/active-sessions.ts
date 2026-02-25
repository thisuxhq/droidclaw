/**
 * Shared active-session tracker for the agent pipeline.
 *
 * Both the device WebSocket handler (device.ts) and the HTTP goals
 * handler (goals.ts) register/look-up running sessions here so that
 * a stop request from *either* path can find and abort the session.
 */
const activeSessions = new Map<
  string,
  { sessionId?: string; goal: string; abort: AbortController }
>();

export { activeSessions };
