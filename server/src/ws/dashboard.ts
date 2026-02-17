import type { ServerWebSocket } from "bun";
import { auth } from "../auth.js";
import { sessions, type WebSocketData } from "./sessions.js";

interface DashboardAuthMessage {
  type: "auth";
  token: string;
}

type DashboardIncomingMessage = DashboardAuthMessage;

/**
 * Handle an incoming message from a dashboard WebSocket.
 */
export async function handleDashboardMessage(
  ws: ServerWebSocket<WebSocketData>,
  raw: string
): Promise<void> {
  let msg: DashboardIncomingMessage;
  try {
    msg = JSON.parse(raw) as DashboardIncomingMessage;
  } catch {
    ws.send(JSON.stringify({ type: "error", message: "Invalid JSON" }));
    return;
  }

  // ── Authentication ─────────────────────────────────────

  if (msg.type === "auth") {
    try {
      // Verify the session token by constructing a request with the cookie header
      const sessionResult = await auth.api.getSession({
        headers: new Headers({
          cookie: `better-auth.session_token=${msg.token}`,
        }),
      });

      if (!sessionResult) {
        ws.send(
          JSON.stringify({ type: "auth_error", message: "Invalid session" })
        );
        return;
      }

      const userId = sessionResult.user.id;

      // Mark connection as authenticated
      ws.data.authenticated = true;
      ws.data.userId = userId;

      // Register as dashboard subscriber
      sessions.addDashboardSubscriber({ userId, ws });

      // Send auth confirmation
      ws.send(JSON.stringify({ type: "auth_ok" }));

      // Send current device list for this user
      const devices = sessions.getDevicesForUser(userId);
      for (const device of devices) {
        const name = device.deviceInfo
          ? `${device.deviceInfo.model} (Android ${device.deviceInfo.androidVersion})`
          : device.deviceId;

        ws.send(
          JSON.stringify({
            type: "device_online",
            deviceId: device.deviceId,
            name,
          })
        );
      }

      console.log(`Dashboard subscriber authenticated: user ${userId}`);
    } catch (err) {
      ws.send(
        JSON.stringify({
          type: "auth_error",
          message: "Authentication failed",
        })
      );
      console.error("Dashboard auth error:", err);
    }
    return;
  }

  // ── All other messages require authentication ─────────

  if (!ws.data.authenticated) {
    ws.send(
      JSON.stringify({ type: "error", message: "Not authenticated" })
    );
    return;
  }

  // Future: handle dashboard commands (e.g., send goal to device)
  console.warn(
    `Unknown message type from dashboard:`,
    (msg as unknown as Record<string, unknown>).type
  );
}

/**
 * Handle a dashboard WebSocket disconnection.
 */
export function handleDashboardClose(
  ws: ServerWebSocket<WebSocketData>
): void {
  sessions.removeDashboardSubscriber(ws);
  console.log(`Dashboard subscriber disconnected: user ${ws.data.userId ?? "unknown"}`);
}
