# ADB Shell Commands Reference

Complete reference of all ADB shell commands available for Android device automation.

---

## Currently Implemented in DroidClaw

| Command | Action | Description |
|---|---|---|
| `input tap x y` | tap | Tap at coordinates |
| `input text <text>` | type | Type text (spaces as `%s`) |
| `input keyevent KEYCODE_ENTER` | enter | Press Enter key |
| `input swipe x1 y1 x2 y2 duration` | swipe | Swipe gesture in direction |
| `input keyevent KEYCODE_HOME` | home | Go to home screen |
| `input keyevent KEYCODE_BACK` | back | Go back |
| `input swipe x y x y 1000` | longpress | Long press via same-point swipe |
| `screencap -p <path>` | screenshot | Capture screen to PNG |
| `uiautomator dump` | (sanitizer) | Dump accessibility tree XML |
| `wm size` | (device intel) | Get screen resolution |
| `dumpsys activity activities` | (device intel) | Get foreground app |
| `am start` / `monkey -p` | launch | Launch app by package/activity/URI |
| `cmd clipboard get-text` | clipboard_get | Read clipboard |
| `cmd clipboard set-text` | clipboard_set | Write clipboard |
| `input keyevent MOVE_END + MOVE_HOME + DEL` | clear | Clear focused text field |
| `shell <command>` | shell | Arbitrary shell command |

---

## 1. Input & Gestures

### Taps & Touches

| Command | Description |
|---|---|
| `input tap <x> <y>` | Single tap at coordinates |
| `input motionevent DOWN <x> <y>` | Raw touch down event |
| `input motionevent UP <x> <y>` | Raw touch up event |
| `input roll <dx> <dy>` | Trackball roll event |

### Swipe & Drag

| Command | Description |
|---|---|
| `input swipe <x1> <y1> <x2> <y2> [duration_ms]` | Swipe gesture |
| `input swipe <x> <y> <x> <y> <long_duration>` | Long press (same point swipe) |
| `input draganddrop <x1> <y1> <x2> <y2> [duration_ms]` | Drag and drop (Android 12+) |

### Key Events - Navigation

| Command | Keycode | Description |
|---|---|---|
| `input keyevent 3` | KEYCODE_HOME | Home button |
| `input keyevent 4` | KEYCODE_BACK | Back button |
| `input keyevent 187` | KEYCODE_APP_SWITCH | Recent apps / multitask |
| `input keyevent 82` | KEYCODE_MENU | Open menu |
| `input keyevent 84` | KEYCODE_SEARCH | Open search |
| `input keyevent 111` | KEYCODE_ESCAPE | Dismiss dialog / escape |

### Key Events - Power & Screen

| Command | Keycode | Description |
|---|---|---|
| `input keyevent 26` | KEYCODE_POWER | Toggle screen on/off |
| `input keyevent 224` | KEYCODE_WAKEUP | Wake screen (no toggle) |
| `input keyevent 223` | KEYCODE_SLEEP | Sleep screen (no toggle) |
| `input keyevent --longpress 26` | KEYCODE_POWER (long) | Power menu / screenshot |

### Key Events - Volume & Media

| Command | Keycode | Description |
|---|---|---|
| `input keyevent 24` | KEYCODE_VOLUME_UP | Volume up |
| `input keyevent 25` | KEYCODE_VOLUME_DOWN | Volume down |
| `input keyevent 164` | KEYCODE_MUTE | Mute toggle |
| `input keyevent 85` | KEYCODE_MEDIA_PLAY_PAUSE | Play/pause media |
| `input keyevent 87` | KEYCODE_MEDIA_NEXT | Next track |
| `input keyevent 88` | KEYCODE_MEDIA_PREVIOUS | Previous track |
| `input keyevent 126` | KEYCODE_MEDIA_PLAY | Play |
| `input keyevent 127` | KEYCODE_MEDIA_PAUSE | Pause |
| `input keyevent 86` | KEYCODE_MEDIA_STOP | Stop media |

### Key Events - Text Editing

| Command | Keycode | Description |
|---|---|---|
| `input keyevent 66` | KEYCODE_ENTER | Enter / confirm |
| `input keyevent 67` | KEYCODE_DEL | Backspace / delete |
| `input keyevent 112` | KEYCODE_FORWARD_DEL | Forward delete |
| `input keyevent 61` | KEYCODE_TAB | Tab between fields |
| `input keyevent 122` | KEYCODE_MOVE_HOME | Move cursor to start |
| `input keyevent 123` | KEYCODE_MOVE_END | Move cursor to end |
| `input keyevent 19` | KEYCODE_DPAD_UP | D-pad up |
| `input keyevent 20` | KEYCODE_DPAD_DOWN | D-pad down |
| `input keyevent 21` | KEYCODE_DPAD_LEFT | D-pad left |
| `input keyevent 22` | KEYCODE_DPAD_RIGHT | D-pad right |
| `input keyevent 23` | KEYCODE_DPAD_CENTER | D-pad center / select |

### Key Events - Keyboard Shortcuts (Combo Keys)

| Command | Description |
|---|---|
| `input keyevent KEYCODE_CTRL_LEFT KEYCODE_A` | Select all |
| `input keyevent KEYCODE_CTRL_LEFT KEYCODE_C` | Copy |
| `input keyevent KEYCODE_CTRL_LEFT KEYCODE_V` | Paste |
| `input keyevent KEYCODE_CTRL_LEFT KEYCODE_X` | Cut |
| `input keyevent KEYCODE_CTRL_LEFT KEYCODE_Z` | Undo |
| `input keyevent KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_Z` | Redo |
| `input keyevent --longpress KEYCODE_MOVE_HOME` | Select all to start (Shift+Home) |
| `input keyevent --longpress KEYCODE_MOVE_END` | Select all to end (Shift+End) |

### Key Events - Brightness & System

| Command | Keycode | Description |
|---|---|---|
| `input keyevent 220` | KEYCODE_BRIGHTNESS_DOWN | Decrease brightness |
| `input keyevent 221` | KEYCODE_BRIGHTNESS_UP | Increase brightness |
| `input keyevent 83` | KEYCODE_NOTIFICATION | Pull down notification shade |
| `input keyevent 176` | KEYCODE_SETTINGS | Open settings |
| `input keyevent 27` | KEYCODE_CAMERA | Camera shutter |

### Text Input

| Command | Description |
|---|---|
| `input text <string>` | Type text (spaces as `%s`, escape shell chars) |

---

## 2. Activity Manager (`am`)

### Launch Activities & Intents

| Command | Description |
|---|---|
| `am start -n <package>/<activity>` | Launch specific activity |
| `am start -a android.intent.action.VIEW -d <url>` | Open URL in browser |
| `am start -a android.intent.action.VIEW -d "geo:0,0?q=pizza"` | Open maps search |
| `am start -a android.intent.action.DIAL -d tel:<number>` | Open dialer with number |
| `am start -a android.intent.action.SENDTO -d sms:<number> --es sms_body "<text>"` | Compose SMS |
| `am start -a android.intent.action.SEND --es android.intent.extra.TEXT "<msg>" -t text/plain` | Share text intent |
| `am start -a android.media.action.IMAGE_CAPTURE` | Open camera for capture |
| `am start -a android.intent.action.VIEW -d "market://details?id=<pkg>"` | Open app in Play Store |
| `am start -a android.intent.action.VIEW -d "mailto:<email>?subject=<subj>&body=<body>"` | Compose email |

### Launch Settings Screens

| Command | Description |
|---|---|
| `am start -a android.settings.SETTINGS` | Main settings |
| `am start -a android.settings.WIFI_SETTINGS` | WiFi settings |
| `am start -a android.settings.BLUETOOTH_SETTINGS` | Bluetooth settings |
| `am start -a android.settings.DISPLAY_SETTINGS` | Display settings |
| `am start -a android.settings.SOUND_SETTINGS` | Sound settings |
| `am start -a android.settings.LOCATION_SOURCE_SETTINGS` | Location settings |
| `am start -a android.settings.SECURITY_SETTINGS` | Security settings |
| `am start -a android.settings.APPLICATION_SETTINGS` | All apps list |
| `am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:<pkg>` | Specific app info |
| `am start -a android.settings.BATTERY_SAVER_SETTINGS` | Battery saver settings |
| `am start -a android.settings.DATE_SETTINGS` | Date & time settings |
| `am start -a android.settings.ACCESSIBILITY_SETTINGS` | Accessibility settings |
| `am start -a android.settings.INTERNAL_STORAGE_SETTINGS` | Storage settings |
| `am start -a android.settings.INPUT_METHOD_SETTINGS` | Keyboard settings |
| `am start -a android.settings.NOTIFICATION_SETTINGS` | Notification settings |
| `am start -a android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS` | Manage all apps |

### App & Process Control

| Command | Description |
|---|---|
| `am force-stop <package>` | Force kill an app |
| `am kill-all` | Kill all background processes |
| `am start-foreground-service -n <component>` | Start foreground service |
| `am stopservice -n <component>` | Stop a service |
| `am set-debug-app -w <package>` | Wait for debugger on app launch |
| `am clear-debug-app` | Clear debug app setting |

### Broadcasts

| Command | Description |
|---|---|
| `am broadcast -a <action>` | Send a broadcast intent |
| `am broadcast -a android.intent.action.BOOT_COMPLETED` | Simulate boot broadcast |
| `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://<path>` | Rescan media file |
| `am broadcast -a android.net.conn.CONNECTIVITY_CHANGE` | Simulate connectivity change |

### Task Management

| Command | Description |
|---|---|
| `am stack list` | List activity stacks |
| `am task lock <taskId>` | Pin app to screen (kiosk mode) |
| `am instrument -w <test-runner>` | Run instrumentation tests |

### Monkey (Random Event Generator)

| Command | Description |
|---|---|
| `monkey -p <package> -c android.intent.category.LAUNCHER 1` | Launch app's default activity |
| `monkey -p <package> --throttle 100 -v 500` | Stress-test with 500 random events |

---

## 3. Package Manager (`pm`)

### List Packages

| Command | Description |
|---|---|
| `pm list packages` | All installed packages |
| `pm list packages -3` | Third-party apps only |
| `pm list packages -s` | System apps only |
| `pm list packages -d` | Disabled packages |
| `pm list packages -e` | Enabled packages |
| `pm list packages <filter>` | Filter by name substring |

### Package Info

| Command | Description |
|---|---|
| `pm path <package>` | Get APK file path |
| `pm dump <package>` | Full package details |
| `pm list permissions -g` | All permissions (grouped) |
| `pm list features` | Device hardware features |
| `pm list libraries` | Available shared libraries |

### Install & Uninstall

| Command | Description |
|---|---|
| `pm install <path.apk>` | Install APK from device path |
| `pm install -r <path.apk>` | Reinstall keeping data |
| `pm install -t <path.apk>` | Allow test packages |
| `pm uninstall <package>` | Uninstall app |
| `pm uninstall -k <package>` | Uninstall but keep data/cache |

### App State

| Command | Description |
|---|---|
| `pm clear <package>` | Clear all app data + cache |
| `pm disable-user --user 0 <package>` | Disable app for current user |
| `pm enable <package>` | Re-enable app |
| `pm grant <package> <permission>` | Grant a runtime permission |
| `pm revoke <package> <permission>` | Revoke a runtime permission |
| `pm set-install-location [0/1/2]` | Set install location (auto/internal/external) |
| `pm get-install-location` | Get install location preference |

---

## 4. System Inspection (`dumpsys`)

### Activity & UI State

| Command | Description |
|---|---|
| `dumpsys activity activities` | Activity stack (foreground app, task history) |
| `dumpsys activity recents` | Recent apps list |
| `dumpsys activity top` | Top activity details |
| `dumpsys activity services` | Running services |
| `dumpsys activity broadcasts` | Pending broadcasts |
| `dumpsys activity providers` | Content providers |
| `dumpsys window` | Window manager state (focus, layers) |
| `dumpsys window displays` | Display dimensions & density |
| `dumpsys accessibility` | Accessibility services state |

### Hardware & Sensors

| Command | Description |
|---|---|
| `dumpsys battery` | Battery level, status, health, temperature |
| `dumpsys display` | Display state, brightness level |
| `dumpsys input` | Input devices, key maps |
| `dumpsys sensorservice` | Available sensors + recent readings |

### Networking

| Command | Description |
|---|---|
| `dumpsys wifi` | WiFi state, SSID, signal strength |
| `dumpsys connectivity` | Network connectivity, active network |
| `dumpsys telephony.registry` | Phone signal, carrier, data state |
| `dumpsys netpolicy` | Network policy info |
| `dumpsys netstats` | Network usage statistics |

### Notifications & Audio

| Command | Description |
|---|---|
| `dumpsys notification` | Active notifications (summary) |
| `dumpsys notification --noredact` | Full notification content (unredacted) |
| `dumpsys audio` | Volume levels, audio routing, active streams |
| `dumpsys media_session` | Current media session (now playing info) |

### Performance & Memory

| Command | Description |
|---|---|
| `dumpsys meminfo` | System-wide memory overview |
| `dumpsys meminfo <package>` | Memory usage of specific app |
| `dumpsys cpuinfo` | CPU usage by process |
| `dumpsys gfxinfo <package>` | GPU rendering performance |
| `dumpsys procstats` | Process statistics |

### Other Useful Dumps

| Command | Description |
|---|---|
| `dumpsys alarm` | Scheduled alarms |
| `dumpsys location` | Location providers, last known location |
| `dumpsys deviceidle` | Doze mode state |
| `dumpsys usagestats` | App usage statistics |
| `dumpsys statusbar` | Status bar state and icons |
| `dumpsys package <package>` | Detailed info for one package |
| `dumpsys power` | Power manager state (wake locks, screen state) |
| `dumpsys clipboard` | Clipboard service state |
| `dumpsys jobscheduler` | Scheduled jobs |
| `dumpsys content` | Content provider info |

---

## 5. Settings Database (`settings`)

### Read Settings

| Command | Description |
|---|---|
| `settings get system screen_brightness` | Current brightness (0-255) |
| `settings get system screen_off_timeout` | Screen timeout (ms) |
| `settings get system volume_ring` | Ring volume |
| `settings get system volume_music` | Media volume |
| `settings get system volume_alarm` | Alarm volume |
| `settings get system volume_notification` | Notification volume |
| `settings get system font_scale` | Font scale (1.0 = normal) |
| `settings get system accelerometer_rotation` | Auto-rotate (1=on, 0=off) |
| `settings get global airplane_mode_on` | Airplane mode (0/1) |
| `settings get global wifi_on` | WiFi state (0/1) |
| `settings get global bluetooth_on` | Bluetooth state (0/1) |
| `settings get global mobile_data` | Mobile data state (0/1) |
| `settings get global stay_on_while_plugged_in` | Stay awake while charging |
| `settings get secure location_mode` | Location mode |
| `settings get secure enabled_accessibility_services` | Active a11y services |
| `settings get secure default_input_method` | Current keyboard |
| `settings get secure android_id` | Device Android ID |
| `settings get secure location_providers_allowed` | Allowed location providers |

### Write Settings

| Command | Description |
|---|---|
| `settings put system screen_brightness <0-255>` | Set brightness |
| `settings put system screen_off_timeout <ms>` | Set screen timeout |
| `settings put system font_scale <float>` | Set font scale |
| `settings put system accelerometer_rotation <0/1>` | Toggle auto-rotate |
| `settings put global airplane_mode_on <0/1>` | Toggle airplane mode |
| `settings put global stay_on_while_plugged_in 3` | Keep screen on when charging |
| `settings put system volume_ring <0-15>` | Set ring volume |
| `settings put system volume_music <0-15>` | Set media volume |

---

## 6. Device Properties (`getprop`)

| Command | Description |
|---|---|
| `getprop ro.build.version.sdk` | Android API level (e.g. "34") |
| `getprop ro.build.version.release` | Android version (e.g. "14") |
| `getprop ro.build.version.codename` | Version codename |
| `getprop ro.product.model` | Device model name |
| `getprop ro.product.manufacturer` | Manufacturer |
| `getprop ro.product.brand` | Brand name |
| `getprop ro.product.device` | Device codename |
| `getprop ro.serialno` | Serial number |
| `getprop ro.build.display.id` | Build ID |
| `getprop ro.build.type` | Build type (user/userdebug/eng) |
| `getprop persist.sys.language` | System language |
| `getprop persist.sys.timezone` | Timezone |
| `getprop gsm.sim.operator.alpha` | Carrier name |
| `getprop gsm.sim.state` | SIM state |
| `getprop dhcp.wlan0.ipaddress` | WiFi IP address |
| `getprop net.dns1` | Primary DNS server |
| `getprop ro.hardware` | Hardware platform |
| `getprop dalvik.vm.heapsize` | Max heap size per app |

---

## 7. Display & Window Manager (`wm`)

| Command | Description |
|---|---|
| `wm size` | Get current screen resolution |
| `wm size <W>x<H>` | Override screen resolution |
| `wm size reset` | Reset to physical resolution |
| `wm density` | Get current DPI |
| `wm density <dpi>` | Override DPI |
| `wm density reset` | Reset DPI to default |
| `wm overscan <left>,<top>,<right>,<bottom>` | Set display overscan/margins |

---

## 8. Content Providers (`content`)

### Read Data

| Command | Description |
|---|---|
| `content query --uri content://contacts/phones` | Read contacts list |
| `content query --uri content://contacts/phones --projection display_name,number` | Contacts with specific columns |
| `content query --uri content://sms` | Read all SMS |
| `content query --uri content://sms --projection address,body,date` | SMS with specific columns |
| `content query --uri content://sms/inbox` | Inbox only |
| `content query --uri content://sms/sent` | Sent messages only |
| `content query --uri content://call_log/calls` | Call history |
| `content query --uri content://call_log/calls --projection number,type,date,duration` | Call log details |
| `content query --uri content://media/external/images/media` | Photos list |
| `content query --uri content://media/external/video/media` | Videos list |
| `content query --uri content://media/external/audio/media` | Audio files |
| `content query --uri content://calendar/events` | Calendar events |
| `content query --uri content://settings/system` | All system settings |
| `content query --uri content://settings/secure` | All secure settings |
| `content query --uri content://settings/global` | All global settings |

### Write Data

| Command | Description |
|---|---|
| `content insert --uri content://sms --bind address:s:<number> --bind body:s:<text>` | Insert SMS record |
| `content update --uri content://settings/system --bind name:s:<key> --bind value:s:<val>` | Update system setting |
| `content delete --uri content://sms --where "address='<number>'"` | Delete SMS by number |

### Filtering

| Command | Description |
|---|---|
| `content query --uri <uri> --where "<column>='<value>'"` | Filter by column value |
| `content query --uri <uri> --sort "<column> DESC"` | Sort results |

---

## 9. Networking

### WiFi

| Command | Description |
|---|---|
| `svc wifi enable` | Turn WiFi on |
| `svc wifi disable` | Turn WiFi off |
| `cmd wifi status` | WiFi connection status |
| `cmd wifi connect-network <ssid> wpa2 <password>` | Connect to network (Android 12+) |
| `cmd wifi disconnect-network` | Disconnect from WiFi |
| `cmd wifi list-scan-results` | List nearby networks |

### Mobile Data & Bluetooth

| Command | Description |
|---|---|
| `svc data enable` | Turn mobile data on |
| `svc data disable` | Turn mobile data off |
| `svc bluetooth enable` | Turn Bluetooth on |
| `svc bluetooth disable` | Turn Bluetooth off |
| `svc nfc enable` | Turn NFC on |
| `svc nfc disable` | Turn NFC off |

### Airplane Mode

| Command | Description |
|---|---|
| `settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE` | Enable airplane mode |
| `settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE` | Disable airplane mode |
| `cmd connectivity airplane-mode enable` | Airplane mode (Android 13+) |
| `cmd connectivity airplane-mode disable` | Airplane mode off (Android 13+) |

### Network Diagnostics

| Command | Description |
|---|---|
| `ping -c 1 google.com` | Check internet connectivity |
| `ping -c 3 <host>` | Ping host N times |
| `ifconfig wlan0` | WiFi interface info |
| `ip addr show` | All network interfaces |
| `ip route` | Routing table |
| `ndc resolver getaddrinfo <hostname>` | DNS lookup |
| `netstat -tlnp` | Active connections |

---

## 10. File System Operations

### Listing & Navigation

| Command | Description |
|---|---|
| `ls /sdcard/` | List files in storage root |
| `ls -la /sdcard/` | Detailed listing with permissions |
| `ls /sdcard/Download/` | List downloads |
| `ls /sdcard/DCIM/Camera/` | List camera photos |
| `ls /sdcard/Pictures/` | List pictures |
| `ls /sdcard/Music/` | List music |
| `ls /data/data/<package>/` | App private data (root) |

### File Operations

| Command | Description |
|---|---|
| `cat /sdcard/file.txt` | Read file contents |
| `cp <src> <dst>` | Copy file |
| `mv <src> <dst>` | Move / rename file |
| `rm <file>` | Delete file |
| `rm -r <dir>` | Delete directory recursively |
| `mkdir -p <path>` | Create directory (with parents) |
| `touch <file>` | Create empty file |
| `chmod <mode> <file>` | Change permissions |
| `stat <file>` | File metadata |

### Disk Usage

| Command | Description |
|---|---|
| `df -h` | Disk usage summary |
| `du -sh /sdcard/*` | Directory sizes |
| `du -sh /sdcard/Download/` | Download folder size |

### ADB File Transfer (not shell commands)

| Command | Description |
|---|---|
| `adb push <local> <remote>` | Upload file to device |
| `adb pull <remote> <local>` | Download file from device |

---

## 11. Notifications

| Command | Description |
|---|---|
| `dumpsys notification --noredact` | Full notification content (titles, text, package) |
| `cmd notification list` | List active notifications |
| `cmd notification cancel-all` | Dismiss all notifications |
| `cmd notification post -t "Title" "Body" tag` | Post a notification |
| `cmd statusbar expand-notifications` | Pull down notification shade |
| `cmd statusbar expand-settings` | Pull down quick settings panel |
| `cmd statusbar collapse` | Collapse notification shade |
| `service call notification 1` | Programmatically expand shade |

---

## 12. Process & Performance

### Process Management

| Command | Description |
|---|---|
| `ps -A` | List all processes |
| `ps -A \| grep <name>` | Find process by name |
| `kill <pid>` | Kill process by PID |
| `kill -9 <pid>` | Force kill process |

### CPU & Memory

| Command | Description |
|---|---|
| `top -n 1 -b` | CPU usage snapshot (one iteration) |
| `top -n 1 -b -o %CPU` | Sorted by CPU usage |
| `cat /proc/meminfo` | Detailed memory info |
| `cat /proc/cpuinfo` | CPU info (cores, speed) |
| `free -h` | Memory summary |
| `uptime` | Device uptime & load averages |
| `vmstat 1 5` | Virtual memory stats (5 samples) |

### Logging

| Command | Description |
|---|---|
| `logcat -d` | Dump full logcat |
| `logcat -d -t 50` | Last 50 log lines |
| `logcat -d -s "TAG:*"` | Filter by specific tag |
| `logcat -d -t 100 *:E` | Last 100 error-level entries |
| `logcat -d \| grep <pattern>` | Filter by pattern |
| `logcat -c` | Clear logcat buffer |
| `logcat -d -b crash` | Crash log buffer |
| `logcat -d -b events` | System events buffer |

---

## 13. Screen Recording & Media

### Screen Recording

| Command | Description |
|---|---|
| `screenrecord /sdcard/video.mp4` | Record screen (default 3 min) |
| `screenrecord --time-limit 10 /sdcard/video.mp4` | Record for N seconds |
| `screenrecord --size 720x1280 /sdcard/vid.mp4` | Record at specific resolution |
| `screenrecord --bit-rate 4000000 /sdcard/vid.mp4` | Record at specific bitrate |
| `screenrecord --bugreport /sdcard/vid.mp4` | Include timestamp overlay |

### Screenshots

| Command | Description |
|---|---|
| `screencap -p /sdcard/screen.png` | Capture PNG screenshot |
| `screencap /sdcard/screen.raw` | Capture raw screenshot |

### Media Control

| Command | Description |
|---|---|
| `media volume --set <0-15> --stream 3` | Set media volume (stream 3) |
| `media volume --set <0-15> --stream 2` | Set ring volume (stream 2) |
| `media volume --set <0-15> --stream 4` | Set alarm volume (stream 4) |
| `media volume --set <0-15> --stream 5` | Set notification volume (stream 5) |
| `media dispatch play` | Play media |
| `media dispatch pause` | Pause media |
| `media dispatch play-pause` | Toggle play/pause |
| `media dispatch next` | Next track |
| `media dispatch previous` | Previous track |

### Media Scanner

| Command | Description |
|---|---|
| `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://<path>` | Refresh single file in media database |

---

## 14. Input Method (Keyboard)

| Command | Description |
|---|---|
| `ime list -s` | List installed input methods (short) |
| `ime list -a` | List all input methods (detailed) |
| `ime set <input_method_id>` | Switch to specific keyboard |
| `ime enable <input_method_id>` | Enable an input method |
| `ime disable <input_method_id>` | Disable an input method |

---

## 15. App Shortcuts (`cmd shortcut`)

| Command | Description |
|---|---|
| `cmd shortcut get-shortcuts <package>` | Get app shortcuts (long-press menu) |
| `cmd shortcut reset-all-throttling` | Reset shortcut rate limiting |

---

## 16. Miscellaneous System Commands

### Power & Reboot

| Command | Description |
|---|---|
| `reboot` | Reboot device |
| `reboot recovery` | Reboot into recovery |
| `reboot bootloader` | Reboot into bootloader |
| `svc power shutdown` | Shut down device |
| `svc power reboot` | Reboot device (alt) |

### Time & Date

| Command | Description |
|---|---|
| `date` | Get current date/time |
| `date +%s` | Unix timestamp |

### Wallpaper

| Command | Description |
|---|---|
| `cmd wallpaper set-live-wallpaper <component>` | Set live wallpaper |

### Device Admin

| Command | Description |
|---|---|
| `cmd device_policy` | Device policy info |
| `cmd user list` | List device users |

### UI Automator (Testing)

| Command | Description |
|---|---|
| `uiautomator dump /dev/tty` | Dump accessibility XML to stdout |
| `uiautomator dump /sdcard/ui.xml` | Dump to file |

### Bug Report

| Command | Description |
|---|---|
| `bugreport` | Generate full bug report |
| `dumpstate` | Dump system state |

---

## Common Permission Strings

For use with `pm grant` / `pm revoke`:

| Permission | Description |
|---|---|
| `android.permission.CAMERA` | Camera access |
| `android.permission.RECORD_AUDIO` | Microphone access |
| `android.permission.READ_CONTACTS` | Read contacts |
| `android.permission.WRITE_CONTACTS` | Write contacts |
| `android.permission.READ_SMS` | Read SMS messages |
| `android.permission.SEND_SMS` | Send SMS |
| `android.permission.READ_CALL_LOG` | Read call history |
| `android.permission.READ_CALENDAR` | Read calendar |
| `android.permission.WRITE_CALENDAR` | Write calendar |
| `android.permission.ACCESS_FINE_LOCATION` | GPS location |
| `android.permission.ACCESS_COARSE_LOCATION` | Approximate location |
| `android.permission.READ_EXTERNAL_STORAGE` | Read storage |
| `android.permission.WRITE_EXTERNAL_STORAGE` | Write storage |
| `android.permission.READ_PHONE_STATE` | Phone state info |
| `android.permission.CALL_PHONE` | Make phone calls |
| `android.permission.READ_MEDIA_IMAGES` | Read images (Android 13+) |
| `android.permission.READ_MEDIA_VIDEO` | Read videos (Android 13+) |
| `android.permission.READ_MEDIA_AUDIO` | Read audio (Android 13+) |
| `android.permission.POST_NOTIFICATIONS` | Show notifications (Android 13+) |

---

## ADB Commands (Non-Shell)

These run directly via `adb` (not `adb shell`):

| Command | Description |
|---|---|
| `adb devices` | List connected devices |
| `adb devices -l` | List with device details |
| `adb connect <ip>:<port>` | Connect over WiFi |
| `adb disconnect` | Disconnect all WiFi devices |
| `adb push <local> <remote>` | Upload file to device |
| `adb pull <remote> <local>` | Download file from device |
| `adb install <path.apk>` | Install APK from computer |
| `adb install -r <path.apk>` | Reinstall keeping data |
| `adb uninstall <package>` | Uninstall app |
| `adb logcat` | Stream device logs |
| `adb forward tcp:<local> tcp:<remote>` | Port forward |
| `adb reverse tcp:<remote> tcp:<local>` | Reverse port forward |
| `adb tcpip 5555` | Switch to WiFi debug mode |
| `adb usb` | Switch back to USB mode |
| `adb reboot` | Reboot device |
| `adb reboot recovery` | Boot to recovery |
| `adb reboot bootloader` | Boot to bootloader |
| `adb get-state` | Device state (device/offline) |
| `adb get-serialno` | Get serial number |
| `adb bugreport <path>` | Capture bug report to file |
| `adb backup -all` | Full device backup |
| `adb restore <backup.ab>` | Restore from backup |
| `adb shell` | Interactive shell |
