# Android Action Kernel

AI agent that controls Android devices through the Accessibility API. Give it a goal in plain English and it autonomously navigates the device using a Perception → Reasoning → Action loop.

## How It Works

1. **Perceive** — Captures the screen's accessibility tree via `adb shell uiautomator dump`, parses it into interactive UI elements with coordinates and state
2. **Reason** — Sends the screen context, action history, and goal to an LLM which decides the next action as a JSON object
3. **Act** — Executes the action (tap, type, swipe, launch app, etc.) via ADB
4. **Repeat** — Diffs the screen state, detects stuck loops, and continues until the goal is done or max steps reached

Falls back to screenshot-based vision when the accessibility tree is empty (games, WebViews, Flutter).

## Prerequisites

- [Bun](https://bun.sh) 1.0+
- [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) (ADB in PATH)
- Android device connected via USB or WiFi ADB
- API key for one of: Groq, OpenAI, AWS Bedrock, or OpenRouter

## Quick Start

```bash
cd android-action-kernel
bun install
cp .env.example .env
# Edit .env — set LLM_PROVIDER and the corresponding API key
bun run src/kernel.ts
```

The agent will prompt you for a goal, then start controlling the device.

## Configuration

Copy `.env.example` to `.env`. Key settings:

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `groq` | `groq`, `openai`, `bedrock`, or `openrouter` |
| `MAX_STEPS` | `30` | Maximum actions before stopping |
| `STEP_DELAY` | `2` | Seconds between actions (lets UI settle) |
| `STUCK_THRESHOLD` | `3` | Unchanged screens before recovery kicks in |
| `VISION_ENABLED` | `true` | Screenshot fallback when accessibility tree is empty |

### LLM Providers

| Provider | Key Variable | Default Model |
|---|---|---|
| Groq (free tier) | `GROQ_API_KEY` | `llama-3.3-70b-versatile` |
| OpenAI | `OPENAI_API_KEY` | `gpt-4o` |
| AWS Bedrock | AWS credential chain | `us.meta.llama3-3-70b-instruct-v1:0` |
| OpenRouter | `OPENROUTER_API_KEY` | `anthropic/claude-3.5-sonnet` |

## Available Actions

The agent can perform 15 actions:

| Category | Actions |
|---|---|
| Navigation | `tap`, `longpress`, `swipe`, `enter`, `back`, `home` |
| Text | `type`, `clear` |
| App Control | `launch` (by package, activity, or URI with extras) |
| Data | `screenshot`, `clipboard_get`, `clipboard_set` |
| System | `shell`, `wait`, `done` |

## Project Structure

```
src/
  kernel.ts          # Main agent loop (entry point)
  actions.ts         # ADB action implementations with retry
  llm-providers.ts   # LLM abstraction (OpenAI, Groq, Bedrock, OpenRouter)
  sanitizer.ts       # Accessibility XML parser
  config.ts          # Environment config loader
  constants.ts       # ADB keycodes, coordinates, defaults
```

## Notes

- Swipe coordinates in `constants.ts` are calibrated for 1080px-wide screens. Adjust `SWIPE_COORDS` for different resolutions.
- The agent automatically detects stuck loops and injects recovery hints after `STUCK_THRESHOLD` steps without screen changes.
- ADB commands retry with exponential backoff (up to `MAX_RETRIES` attempts).
