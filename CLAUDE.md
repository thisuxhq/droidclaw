# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DroidClaw — an AI agent that controls Android devices through the Accessibility API. It runs a Perception → Reasoning → Action loop: captures the screen state via `uiautomator dump`, sends it to an LLM for decision-making, and executes the chosen action via ADB.

**Runtime:** Bun (TypeScript, ES2022 modules). Bun natively loads `.env` files — no dotenv needed.

## Commands

All commands run from the project root:

```bash
bun install                    # Install dependencies
bun run src/kernel.ts          # Start the agent (interactive, prompts for goal)
bun run build                  # Compile to dist/ (bun build --target bun)
bun run typecheck              # Type-check only (tsc --noEmit)
```

There are no tests currently.

## Architecture

Seven source files in `src/`, no subdirectories:

- **kernel.ts** — Entry point and main agent loop. Reads goal from stdin, runs up to MAX_STEPS iterations of: capture screen → diff with previous → call LLM → execute action → track history. Handles stuck-loop detection and vision fallback when the accessibility tree is empty.
- **actions.ts** — 15 action implementations (tap, type, enter, swipe, home, back, wait, done, longpress, screenshot, launch, clear, clipboard_get, clipboard_set, shell). Each wraps ADB commands via `Bun.spawnSync()`. `runAdbCommand()` provides exponential backoff retry.
- **llm-providers.ts** — LLM abstraction with `LLMProvider` interface and factory (`getLlmProvider()`). Four providers: OpenAI, Groq (OpenAI-compatible endpoint), AWS Bedrock (Anthropic + Meta model formats), OpenRouter (Vercel AI SDK). Contains the full SYSTEM_PROMPT with all 15 action definitions and rules.
- **sanitizer.ts** — Parses Android Accessibility XML (via `fast-xml-parser`) into `UIElement[]`. Depth-first walk extracting bounds, center coordinates, state flags (enabled, checked, focused, etc.), and parent context. `computeScreenHash()` used for stuck-loop detection.
- **config.ts** — Singleton `Config` object reading from `process.env` with defaults from constants. `Config.validate()` checks required API keys at startup.
- **constants.ts** — All magic values: ADB keycodes, swipe coordinates (hardcoded for 1080px-wide screens), default models, file paths, agent defaults.

## Key Patterns

- **Provider factory:** `getLlmProvider()` returns the appropriate `LLMProvider` based on `Config.LLM_PROVIDER`. Groq reuses the `OpenAIProvider` class with a different base URL.
- **Screen state diffing:** Hash-based comparison (id + text + center + state). After STUCK_THRESHOLD unchanged steps, recovery hints are injected into the LLM prompt.
- **Vision fallback:** When `getInteractiveElements()` returns empty (custom UI, WebView, Flutter), a screenshot is captured and the LLM gets a fallback context suggesting coordinate-based taps.
- **LLM response parsing:** `parseJsonResponse()` handles both clean JSON and markdown-wrapped code blocks. Falls back to "wait" action on parse failure.
- **Long press via swipe:** Implemented as `input swipe x y x y 1000` (swipe from point to same point with long duration).
- **Text escaping for ADB:** Spaces become `%s`, shell metacharacters are backslash-escaped in `executeType()`.

## Adding a New LLM Provider

1. Implement `LLMProvider` interface in `llm-providers.ts`
2. Add case to `getLlmProvider()` factory
3. Add config fields to `config.ts` and env vars to `.env.example`

## Adding a New Action

1. Add fields to `ActionDecision` interface in `actions.ts`
2. Implement `executeNewAction()` function
3. Add case to `executeAction()` switch
4. Document the action JSON format in `SYSTEM_PROMPT` in `llm-providers.ts`

## Environment Setup

Requires: Bun 1.0+, ADB (Android SDK Platform Tools) in PATH, an Android device connected via USB/WiFi with accessibility enabled, and an API key for at least one LLM provider (Groq, OpenAI, Bedrock, or OpenRouter).

Copy `.env.example` to `.env` and configure `LLM_PROVIDER` + the corresponding API key.

## Device Assumptions

Swipe coordinates in `constants.ts` are hardcoded for 1080px-wide screens (center X=540, center Y=1200). Adjust `SWIPE_COORDS` and `SCREEN_CENTER_*` for different resolutions.
