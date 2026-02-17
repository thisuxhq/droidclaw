/**
 * App-specific hints for the DroidClaw UI agent.
 *
 * Injected into the agent prompt ONLY when the foreground app matches.
 * Keeps the prompt lean — no hints for apps that aren't on screen.
 */

const APP_HINTS: Record<string, string[]> = {
  "com.google.android.youtube": [
    "Mini-player at bottom expands with SWIPE UP from its coordinates, not tap (tap = play/pause).",
    "Search: tap magnifying glass icon at top, type query, tap search result.",
    "Fullscreen: the video player area. Tap once to show controls, double-tap sides to skip.",
  ],
  "com.whatsapp": [
    "To send a message, tap the green arrow/send button — do NOT use 'enter' key.",
    "New chat: tap the green floating button (bottom-right), then search for contact.",
    "Media: tap the + or paperclip icon to attach files/images.",
  ],
  "com.instagram.android": [
    "Search: tap magnifying glass in bottom nav bar.",
    "DMs: tap messenger icon (top-right on home screen).",
    "New post: tap + icon in bottom nav.",
  ],
  "com.google.android.gm": [
    "Compose: tap the floating pencil/write button (bottom-right).",
    "ALWAYS use compose_email action for filling email fields — never type into fields manually.",
  ],
  "com.android.chrome": [
    "Address bar is at the top — tap it to type a URL or search query.",
    "Tabs: tap the number icon (top-right) to switch tabs.",
  ],
  "com.google.android.apps.maps": [
    "Search: tap the search bar at top of screen.",
    "Directions: tap 'Directions' button after selecting a place.",
    "Prefer using intent with google.navigation URI for turn-by-turn navigation.",
  ],
  "com.spotify.music": [
    "Search: tap 'Search' in bottom nav.",
    "Mini-player at bottom expands with SWIPE UP, not tap.",
    "Prefer using intent with spotify: URI to play specific tracks/playlists.",
  ],
  "com.google.android.apps.messaging": [
    "New message: tap the floating button with + or pencil icon.",
    "To field: type the contact name or number, then select from suggestions.",
    "Send: tap the arrow/send icon, not Enter.",
  ],
  "com.google.android.dialer": [
    "Dial pad: tap the floating phone icon if dial pad isn't visible.",
    "Prefer using intent with tel: URI to call a number directly.",
  ],
};

/**
 * Get hints for a foreground app. Returns empty array if no hints registered.
 */
export function getAppHints(packageName: string): string[] {
  return APP_HINTS[packageName] ?? [];
}

/**
 * Format hints into a prompt section. Returns empty string if no hints.
 */
export function formatAppHints(packageName: string): string {
  const hints = getAppHints(packageName);
  if (hints.length === 0) return "";
  return (
    "\n\nAPP_HINTS (tips specific to this app):\n" +
    hints.map((h) => `- ${h}`).join("\n")
  );
}
