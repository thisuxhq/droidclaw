import { Hono } from "hono";
import { cors } from "hono/cors";
import { auth } from "./auth.js";
import { env } from "./env.js";
import { handleDeviceMessage, handleDeviceClose } from "./ws/device.js";
import {
  handleDashboardMessage,
  handleDashboardClose,
} from "./ws/dashboard.js";
import type { WebSocketData } from "./ws/sessions.js";
import { devices } from "./routes/devices.js";
import { goals } from "./routes/goals.js";
import { health } from "./routes/health.js";

const app = new Hono();

// CORS for dashboard
app.use(
  "*",
  cors({
    origin: env.CORS_ORIGIN,
    allowHeaders: ["Content-Type", "Authorization"],
    allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    credentials: true,
  })
);

// Better Auth handler
app.on(["POST", "GET"], "/api/auth/*", (c) => {
  return auth.handler(c.req.raw);
});

// REST routes
app.route("/devices", devices);
app.route("/goals", goals);
app.route("/health", health);

// Start server with WebSocket support
const server = Bun.serve<WebSocketData>({
  port: env.PORT,
  fetch(req, server) {
    const url = new URL(req.url);

    // WebSocket upgrade for device connections
    if (url.pathname === "/ws/device") {
      const upgraded = server.upgrade(req, {
        data: { path: "/ws/device" as const, authenticated: false },
      });
      if (upgraded) return undefined;
      return new Response("WebSocket upgrade failed", { status: 400 });
    }

    // WebSocket upgrade for dashboard connections
    if (url.pathname === "/ws/dashboard") {
      const upgraded = server.upgrade(req, {
        data: { path: "/ws/dashboard" as const, authenticated: false },
      });
      if (upgraded) return undefined;
      return new Response("WebSocket upgrade failed", { status: 400 });
    }

    // Non-WebSocket requests go to Hono
    return app.fetch(req);
  },
  websocket: {
    idleTimeout: 120,
    sendPings: true,
    open(ws) {
      console.log(`WebSocket opened: ${ws.data.path}`);
    },
    message(ws, message) {
      const raw =
        typeof message === "string"
          ? message
          : new TextDecoder().decode(message);

      if (ws.data.path === "/ws/device") {
        handleDeviceMessage(ws, raw).catch((err) => {
          console.error(`Device message handler error: ${err}`);
        });
      } else if (ws.data.path === "/ws/dashboard") {
        handleDashboardMessage(ws, raw).catch((err) => {
          console.error(`Dashboard message handler error: ${err}`);
        });
      }
    },
    close(ws, code, reason) {
      console.log(`WebSocket closed: ${ws.data.path} device=${ws.data.deviceId ?? "unknown"} code=${code} reason=${reason}`);
      if (ws.data.path === "/ws/device") {
        handleDeviceClose(ws);
      } else if (ws.data.path === "/ws/dashboard") {
        handleDashboardClose(ws);
      }
    },
  },
});

console.log(`Server running on port ${server.port}`);
