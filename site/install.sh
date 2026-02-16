#!/bin/sh
set -e

# droidclaw installer
# curl -fsSL https://droidclaw.ai/install.sh | sh

REPO="https://github.com/unitedbyai/droidclaw.git"
INSTALL_DIR="droidclaw"
MIN_BUN_MAJOR=1

# colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
RESET='\033[0m'

info() { printf "${CYAN}>${RESET} %s\n" "$1"; }
success() { printf "${GREEN}>${RESET} %s\n" "$1"; }
warn() { printf "${YELLOW}>${RESET} %s\n" "$1"; }
error() { printf "${RED}error:${RESET} %s\n" "$1"; exit 1; }

printf "\n${BOLD}droidclaw${RESET} ${DIM}— ai agent for android${RESET}\n\n"

# ─── check curl ───
if ! command -v curl >/dev/null 2>&1; then
  error "curl is required but not found. install curl first."
fi

# ─── check git ───
if ! command -v git >/dev/null 2>&1; then
  error "git is required. install it first: https://git-scm.com"
fi

# ─── check/install bun ───
# droidclaw requires bun — it uses Bun.spawnSync() and native .env loading
# these APIs don't exist in node/npm, so node won't work
install_bun() {
  info "installing bun..."
  curl -fsSL https://bun.sh/install | bash
  # bun installs to ~/.bun/bin — add to PATH for this session
  export BUN_INSTALL="${BUN_INSTALL:-$HOME/.bun}"
  export PATH="$BUN_INSTALL/bin:$PATH"
}

if command -v bun >/dev/null 2>&1; then
  BUN_VERSION=$(bun --version 2>/dev/null || echo "0.0.0")
  BUN_MAJOR=$(echo "$BUN_VERSION" | cut -d. -f1)
  if [ "$BUN_MAJOR" -ge "$MIN_BUN_MAJOR" ] 2>/dev/null; then
    success "bun $BUN_VERSION found"
  else
    warn "bun $BUN_VERSION is too old (need $MIN_BUN_MAJOR.0+), upgrading..."
    install_bun
  fi
else
  if command -v node >/dev/null 2>&1; then
    warn "node found but droidclaw requires bun (uses bun-specific APIs)"
    warn "node/npm won't work — installing bun alongside node..."
  fi
  install_bun
fi

# verify bun is actually available
if ! command -v bun >/dev/null 2>&1; then
  printf "\n"
  error "bun installation failed. install manually:\n\n  curl -fsSL https://bun.sh/install | bash\n\n  then re-run this installer."
fi

# ─── check adb ───
if command -v adb >/dev/null 2>&1; then
  success "adb found"
else
  warn "adb not found — attempting install..."
  OS="$(uname -s)"
  case "$OS" in
    Darwin)
      if command -v brew >/dev/null 2>&1; then
        info "installing via homebrew..."
        brew install --cask android-platform-tools 2>/dev/null && success "adb installed via homebrew" || {
          warn "homebrew install failed. trying brew formula..."
          brew install android-platform-tools 2>/dev/null && success "adb installed via homebrew" || {
            warn "could not install adb automatically"
            warn "install manually: brew install android-platform-tools"
            warn "or download from: https://developer.android.com/tools/releases/platform-tools"
          }
        }
      else
        warn "homebrew not found. install adb manually:"
        warn "  /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        warn "  brew install android-platform-tools"
        warn "  or download from: https://developer.android.com/tools/releases/platform-tools"
      fi
      ;;
    Linux)
      if command -v apt-get >/dev/null 2>&1; then
        info "installing via apt..."
        sudo apt-get update -qq && sudo apt-get install -y -qq android-tools-adb && success "adb installed via apt" || {
          warn "apt install failed. install manually: sudo apt install android-tools-adb"
        }
      elif command -v dnf >/dev/null 2>&1; then
        info "installing via dnf..."
        sudo dnf install -y android-tools && success "adb installed via dnf" || {
          warn "dnf install failed. install manually: sudo dnf install android-tools"
        }
      elif command -v pacman >/dev/null 2>&1; then
        info "installing via pacman..."
        sudo pacman -S --noconfirm android-tools && success "adb installed via pacman" || {
          warn "pacman install failed. install manually: sudo pacman -S android-tools"
        }
      else
        warn "could not auto-install adb. install manually:"
        warn "  https://developer.android.com/tools/releases/platform-tools"
      fi
      ;;
    MINGW*|MSYS*|CYGWIN*)
      warn "windows detected. install adb manually:"
      warn "  https://developer.android.com/tools/releases/platform-tools"
      warn "  extract and add to PATH"
      ;;
    *)
      warn "unknown os ($OS). install adb manually:"
      warn "  https://developer.android.com/tools/releases/platform-tools"
      ;;
  esac
fi

# ─── clone or update ───
if [ -d "$INSTALL_DIR" ]; then
  if [ -d "$INSTALL_DIR/.git" ]; then
    info "droidclaw directory exists, pulling latest..."
    (cd "$INSTALL_DIR" && git pull --quiet)
  else
    error "directory '$INSTALL_DIR' exists but is not a git repo. remove it or install elsewhere."
  fi
else
  info "cloning droidclaw..."
  git clone --quiet --depth 1 "$REPO" "$INSTALL_DIR"
fi

# ─── install deps ───
cd "$INSTALL_DIR"
info "installing dependencies..."
bun install --silent 2>/dev/null || bun install

# ─── setup env ───
if [ ! -f .env ]; then
  cp .env.example .env
  success ".env created from .env.example"
else
  success ".env already exists, skipping"
fi

# ─── summary ───
printf "\n${GREEN}${BOLD}installed!${RESET}\n\n"

# check what's missing
MISSING=""
if ! command -v adb >/dev/null 2>&1; then
  MISSING="adb"
fi

if [ -n "$MISSING" ]; then
  warn "missing: $MISSING (install before running)"
  printf "\n"
fi

printf "next steps:\n\n"
printf "  ${BOLD}1.${RESET} configure an llm provider in ${CYAN}droidclaw/.env${RESET}\n\n"
printf "     ${DIM}# groq (free tier, fastest to start)${RESET}\n"
printf "     ${DIM}# set in .env:${RESET} LLM_PROVIDER=groq\n"
printf "     ${DIM}#             ${RESET} GROQ_API_KEY=gsk_...\n\n"
printf "     ${DIM}# or local with ollama (no api key needed)${RESET}\n"
printf "     ollama pull llama3.2\n"
printf "     ${DIM}# set in .env:${RESET} LLM_PROVIDER=ollama\n\n"
printf "  ${BOLD}2.${RESET} connect your android phone (usb debugging on)\n\n"
printf "     adb devices\n\n"
printf "  ${BOLD}3.${RESET} run it\n\n"
printf "     cd droidclaw && bun run src/kernel.ts\n\n"
printf "docs: ${CYAN}https://droidclaw.ai${RESET}\n"
printf "repo: ${CYAN}https://github.com/unitedbyai/droidclaw${RESET}\n\n"
