# DroidClaw — Capabilities & Limitations

## Built-in Actions (15)

| # | Action | What it does | ADB Command |
|---|--------|-------------|-------------|
| 1 | `tap` | Tap at x,y coordinates | `input tap x y` |
| 2 | `longpress` | Long press at x,y (context menus, drag) | `input swipe x y x y 1000` |
| 3 | `type` | Type text into focused field | `input text "..."` |
| 4 | `enter` | Press Enter/Submit key | `input keyevent 66` |
| 5 | `clear` | Select all + delete in focused field | `keyevent MOVE_END → MOVE_HOME → DEL` |
| 6 | `swipe` | Swipe up/down/left/right (scrolling) | `input swipe x1 y1 x2 y2 300` |
| 7 | `home` | Go to home screen | `input keyevent KEYCODE_HOME` |
| 8 | `back` | Press back button | `input keyevent KEYCODE_BACK` |
| 9 | `launch` | Open app by package, activity, or URI | `am start` / `monkey -p` |
| 10 | `clipboard_get` | Read clipboard contents | `cmd clipboard get-text` |
| 11 | `clipboard_set` | Write text to clipboard | `cmd clipboard set-text "..."` |
| 12 | `screenshot` | Capture screen and pull to local file | `screencap -p` + `adb pull` |
| 13 | `wait` | Sleep 2 seconds (wait for UI to load) | `Bun.sleepSync(2000)` |
| 14 | `shell` | Run any arbitrary ADB shell command | `adb shell <command>` |
| 15 | `done` | Signal task completion, stop the loop | (internal) |

---

## Cannot Do At All

| Limitation | Reason |
|-----------|--------|
| Read screen content directly | Must rely on `uiautomator dump` (accessibility XML) — if an app doesn't expose accessibility nodes, the agent is blind |
| Interact with secure/banking apps | Apps with `FLAG_SECURE` block screenshots and UI dumps — agent gets empty data |
| Handle biometrics | Cannot simulate fingerprint or face unlock (hardware-level security) |
| Bypass lock screen | Cannot enter PIN/pattern via ADB on encrypted devices (pre-boot state) |
| Access other app's private data | `/data/data/pkg/` requires root access |
| Install from Play Store | Can sideload APKs via `pm install`, but cannot interact with Play Store purchase/install flow |
| Control phone calls | Can open dialer (`am start tel:...`) but cannot control the call itself (answer, hang up, conference) |
| Read SMS content | Restricted since Android 10 without default SMS app permission |
| Access camera/microphone streams | Can trigger camera app but cannot capture or process the live feed |
| Modify system partitions | `/system` is read-only without root |
| Grant all permissions silently | Some runtime permissions require on-device user interaction |
| Multi-finger gestures | ADB `input` only supports single-touch — no pinch-to-zoom, two-finger swipe, or rotation gestures |

---

## Unreliable / Partially Working

| Limitation | Reason |
|-----------|--------|
| Custom UI frameworks (Flutter, React Native, games) | `uiautomator dump` returns empty or useless XML — falls back to vision-based coordinate tapping |
| WebViews | Accessibility tree is often incomplete inside embedded browsers |
| Precise timing gestures | Double-tap, quick swipe, fling — timing is inconsistent over ADB |
| Notification interaction | Can expand the shade, but reading/tapping individual notifications is flaky via accessibility tree |
| Drag and drop | `input draganddrop` exists but is unreliable across Android versions |
| Clipboard on Android 12+ | `cmd clipboard` increasingly restricted, apps get toast warnings on clipboard access |
| Fast typing | `input text` is slow for long strings, and some keyboards intercept or modify input |
| CAPTCHAs / bot detection | Some apps detect ADB-driven input patterns and block interaction |
| Screen state on custom launchers | Some launchers produce non-standard accessibility trees that confuse element parsing |

---

## Needs Root (Not Available by Default)

| Capability | What root unlocks |
|-----------|-------------------|
| Read/write `/data/data/` | Access any app's private storage, databases, shared preferences |
| Read/write `/system/` | Modify system files, replace system apps |
| Capture network traffic | Run `tcpdump` for packet capture |
| Change MAC address | Spoof network hardware identity |
| Modify hosts file | Block domains, redirect traffic |
| Access keystore/credentials | Read stored accounts and tokens |
| Disable SELinux | Remove Android's mandatory access control |
| Full logcat from all apps | Read logs from all processes without filtering |
| Install as system app | Survive factory resets, gain system-level permissions |

---

## Architecture-Level Gaps

| Gap | Impact |
|-----|--------|
| No OCR | Cannot read text from screenshots natively — relies entirely on accessibility XML text fields. If text isn't in the XML, it's invisible to the agent |
| No audio processing | Cannot hear, record, or process audio output from the device |
| No real-time streaming | Screen state is polled (dump → parse → act), not continuous — misses animations, transient toasts, loading states |
| Single device only | The kernel controls one device at a time, no multi-device orchestration |
| Resolution assumptions | Swipe coords use ratio-based calculation from `computeSwipeCoords()`, but LLM-suggested tap coordinates may be wrong on unusual resolutions or aspect ratios |
| No state persistence across runs | Each run starts fresh — no memory of previous sessions, learned app layouts, or cached element positions |
| Network latency to LLM | Each perception → reasoning → action cycle includes an LLM API round-trip (200ms–2s), making the agent slow for time-sensitive interactions |
| No parallel actions | Actions execute sequentially — cannot tap two things simultaneously or perform background monitoring while acting |

---

## What the `shell` Escape Hatch Unlocks

The `shell` action can run any `adb shell` command, extending capabilities beyond the 15 built-in actions. See [adb-commands.md](./adb-commands.md) for the full reference. Key categories:

- **Input simulation** — all key events, swipes, text input
- **App management** — launch, kill, install, uninstall, clear data
- **Package management** — list apps, grant/revoke permissions
- **System inspection** — battery, wifi, memory, CPU, notifications
- **Settings** — brightness, volume, airplane mode, rotation
- **File system** — list, read, copy, move, delete files on `/sdcard/`
- **Networking** — enable/disable wifi/bluetooth/data, ping, DNS lookup
- **Screen recording** — capture video, screenshots
- **Content providers** — query contacts, SMS, call log, media, calendar
- **Process management** — list, kill, monitor processes
- **Device info** — model, Android version, carrier, serial number
