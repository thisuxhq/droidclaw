import { Client } from "@upstash/qstash";
import { env } from "./env.js";

let _client: Client | null = null;

/** Returns QStash client, or null if not configured */
export function getQStashClient(): Client | null {
  if (!env.QSTASH_TOKEN) return null;
  if (!_client) {
    _client = new Client({
      token: env.QSTASH_TOKEN,
      ...(env.QSTASH_URL ? { baseUrl: env.QSTASH_URL } : {}),
    });
  }
  return _client;
}

/** Returns the public callback URL for scheduled goal execution */
export function getCallbackUrl(): string {
  const base = env.SERVER_PUBLIC_URL;
  if (!base) throw new Error("SERVER_PUBLIC_URL is required for scheduled goals");
  return `${base}/goals/execute`;
}
