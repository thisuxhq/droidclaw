CREATE TABLE IF NOT EXISTS "app_hint" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"package_name" text NOT NULL,
	"hint" text NOT NULL,
	"source_session_id" text,
	"created_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "agent_session" ADD COLUMN IF NOT EXISTS "qstash_message_id" text;--> statement-breakpoint
ALTER TABLE "agent_session" ADD COLUMN IF NOT EXISTS "scheduled_for" timestamp;--> statement-breakpoint
ALTER TABLE "agent_session" ADD COLUMN IF NOT EXISTS "scheduled_delay" integer;--> statement-breakpoint
ALTER TABLE "agent_step" ADD COLUMN IF NOT EXISTS "package_name" text;--> statement-breakpoint
DO $$ BEGIN
  ALTER TABLE "app_hint" ADD CONSTRAINT "app_hint_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;--> statement-breakpoint
DO $$ BEGIN
  ALTER TABLE "app_hint" ADD CONSTRAINT "app_hint_source_session_id_agent_session_id_fk" FOREIGN KEY ("source_session_id") REFERENCES "public"."agent_session"("id") ON DELETE set null ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
