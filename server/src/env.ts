export const env = {
  DATABASE_URL: process.env.DATABASE_URL!,
  PORT: parseInt(process.env.PORT || "8080"),
  CORS_ORIGIN: process.env.CORS_ORIGIN || "http://localhost:5173",
  INTERNAL_SECRET: process.env.INTERNAL_SECRET || "",
  POLAR_ACCESS_TOKEN: process.env.POLAR_ACCESS_TOKEN || "",
  POLAR_ORGANIZATION_ID: process.env.POLAR_ORGANIZATION_ID || "",
  POLAR_SANDBOX: process.env.POLAR_SANDBOX || "false",
  QSTASH_TOKEN: process.env.QSTASH_TOKEN || "",
  QSTASH_CURRENT_SIGNING_KEY: process.env.QSTASH_CURRENT_SIGNING_KEY || "",
  QSTASH_NEXT_SIGNING_KEY: process.env.QSTASH_NEXT_SIGNING_KEY || "",
  SERVER_PUBLIC_URL: process.env.SERVER_PUBLIC_URL || "",
};

if (!env.DATABASE_URL) {
  throw new Error("DATABASE_URL is not set");
}
