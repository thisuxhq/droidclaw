CREATE TABLE IF NOT EXISTS "pairing_code" (
	"id" text PRIMARY KEY NOT NULL,
	"code" text NOT NULL,
	"user_id" text NOT NULL,
	"expires_at" timestamp NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "pairing_code_code_unique" UNIQUE("code")
);
--> statement-breakpoint
DO $$ BEGIN
  ALTER TABLE "pairing_code" ADD CONSTRAINT "pairing_code_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
