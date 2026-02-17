import { Hono } from "hono";
import { cors } from "hono/cors";
import { auth } from "./auth.js";
import { env } from "./env.js";

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

// Health check
app.get("/health", (c) => c.json({ status: "ok" }));

// Start server with WebSocket support
const server = Bun.serve({
  port: env.PORT,
  fetch: app.fetch,
  websocket: {
    open(ws) {
      console.log("WebSocket connected");
    },
    message(ws, message) {
      // placeholder — Task 4 implements device/dashboard handlers
    },
    close(ws) {
      console.log("WebSocket disconnected");
    },
  },
});

console.log(`Server running on port ${server.port}`);
