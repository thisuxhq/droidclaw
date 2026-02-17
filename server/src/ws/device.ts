import type { ServerWebSocket } from "bun";
import type { DeviceMessage } from "@droidclaw/shared";
import { auth } from "../auth.js";
import { sessions, type WebSocketData } from "./sessions.js";

/**
 * Handle an incoming message from an Android device WebSocket.
 */
export async function handleDeviceMessage(
  ws: ServerWebSocket<WebSocketData>,
  raw: string
): Promise<void> {
  let msg: DeviceMessage;
  try {
    msg = JSON.parse(raw) as DeviceMessage;
  } catch {
    ws.send(JSON.stringify({ type: "error", message: "Invalid JSON" }));
    return;
  }

  // ── Authentication ─────────────────────────────────────

  if (msg.type === "auth") {
    try {
      const result = await auth.api.verifyApiKey({
        body: { key: msg.apiKey },
      });

      if (!result.valid || !result.key) {
        ws.send(
          JSON.stringify({
            type: "auth_error",
            message: result.error?.message ?? "Invalid API key",
          })
        );
        return;
      }

      const deviceId = crypto.randomUUID();
      const userId = result.key.userId;

      // Mark connection as authenticated
      ws.data.authenticated = true;
      ws.data.userId = userId;
      ws.data.deviceId = deviceId;

      // Register device in session manager
      sessions.addDevice({
        deviceId,
        userId,
        ws,
        deviceInfo: msg.deviceInfo,
        connectedAt: new Date(),
      });

      // Confirm auth to the device
      ws.send(JSON.stringify({ type: "auth_ok", deviceId }));

      // Notify dashboard subscribers
      const name = msg.deviceInfo
        ? `${msg.deviceInfo.model} (Android ${msg.deviceInfo.androidVersion})`
        : deviceId;

      sessions.notifyDashboard(userId, {
        type: "device_online",
        deviceId,
        name,
      });

      console.log(`Device authenticated: ${deviceId} for user ${userId}`);
    } catch (err) {
      ws.send(
        JSON.stringify({
          type: "auth_error",
          message: "Authentication failed",
        })
      );
      console.error("Device auth error:", err);
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

  switch (msg.type) {
    case "screen": {
      // Device is reporting its screen state in response to a get_screen command
      sessions.resolveRequest(msg.requestId, {
        type: "screen",
        elements: msg.elements,
        screenshot: msg.screenshot,
        packageName: msg.packageName,
      });
      break;
    }

    case "result": {
      // Device is reporting the result of an action command
      sessions.resolveRequest(msg.requestId, {
        type: "result",
        success: msg.success,
        error: msg.error,
        data: msg.data,
      });
      break;
    }

    case "goal": {
      // Device is requesting a goal to be executed
      // Task 6 wires up the agent loop here
      console.log(
        `Goal request from device ${ws.data.deviceId}: ${msg.text}`
      );
      break;
    }

    case "pong": {
      // Heartbeat response — no-op
      break;
    }

    default: {
      console.warn(
        `Unknown message type from device ${ws.data.deviceId}:`,
        (msg as Record<string, unknown>).type
      );
    }
  }
}

/**
 * Handle a device WebSocket disconnection.
 */
export function handleDeviceClose(
  ws: ServerWebSocket<WebSocketData>
): void {
  const { deviceId, userId } = ws.data;
  if (!deviceId) return;

  sessions.removeDevice(deviceId);

  if (userId) {
    sessions.notifyDashboard(userId, {
      type: "device_offline",
      deviceId,
    });
  }

  console.log(`Device disconnected: ${deviceId}`);
}
