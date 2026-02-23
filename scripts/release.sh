#!/bin/bash
set -euo pipefail

# Usage: ./scripts/release.sh v0.4.0

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "Usage: ./scripts/release.sh <version>"
  echo "Example: ./scripts/release.sh v0.4.0"
  exit 1
fi

# Get last release tag
LAST_TAG=$(gh release list --limit 1 --json tagName -q '.[0].tagName' 2>/dev/null || echo "")
if [ -z "$LAST_TAG" ]; then
  echo "No previous release found, using all commits"
  COMMIT_RANGE="HEAD"
else
  echo "Last release: $LAST_TAG"
  COMMIT_RANGE="$LAST_TAG..HEAD"
fi

# Generate release notes from commit messages
echo "Generating release notes..."
NOTES=$(git log "$COMMIT_RANGE" --oneline --no-merges \
  | grep -E "^[a-f0-9]+ (feat|fix|refactor|redesign)" \
  | sed 's/^[a-f0-9]* //' \
  | sed 's/^feat[^:]*: /- /' \
  | sed 's/^fix[^:]*: /- Fix: /' \
  | sed 's/^refactor[^:]*: /- Refactor: /' \
  | sed 's/^redesign[^:]*: /- Redesign: /')

if [ -z "$NOTES" ]; then
  echo "No notable commits found since $LAST_TAG"
  exit 1
fi

echo ""
echo "=== Release $VERSION ==="
echo "$NOTES"
echo ""

# Build APK
echo "Building APK..."
cd android && ./gradlew assembleDebug && cd ..
APK_PATH="android/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found at $APK_PATH"
  exit 1
fi

# Update APK download links in devices page and README
DEVICES_PAGE="web/src/routes/dashboard/devices/+page.svelte"
README="README.md"
CHANGED=false

for FILE in "$DEVICES_PAGE" "$README"; do
  if grep -q "releases/download/" "$FILE" 2>/dev/null; then
    sed -i '' "s|releases/download/[^/]*/app-debug.apk|releases/download/$VERSION/app-debug.apk|g" "$FILE"
    # Also update version label in README (e.g., "APK (v0.3.1)" → "APK (v0.4.0)")
    sed -i '' "s|APK (v[^)]*)|APK ($VERSION)|g" "$FILE"
    git add "$FILE"
    CHANGED=true
  fi
done

if [ "$CHANGED" = true ]; then
  git commit -m "chore: update APK download links to $VERSION" || true
  git push || true
fi

# Create GitHub release
echo "Creating GitHub release $VERSION..."
gh release create "$VERSION" "$APK_PATH" \
  --title "$VERSION" \
  --notes "$NOTES"

echo ""
echo "Release $VERSION created!"
echo "https://github.com/unitedbyai/droidclaw/releases/tag/$VERSION"
