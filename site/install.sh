#!/bin/sh
set -e

# droidclaw installer
# curl -fsSL https://droidclaw.ai/install.sh | sh

REPO="https://github.com/unitedbyai/droidclaw.git"
INSTALL_DIR="droidclaw"

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
error() { printf "${RED}>${RESET} %s\n" "$1"; exit 1; }

printf "\n${BOLD}droidclaw${RESET} ${DIM}— ai agent for android${RESET}\n\n"

# ─── check git ───
if ! command -v git >/dev/null 2>&1; then
  error "git is required. install it first: https://git-scm.com"
fi

# ─── check/install bun ───
if command -v bun >/dev/null 2>&1; then
  success "bun $(bun --version) found"
else
  info "installing bun..."
  curl -fsSL https://bun.sh/install | bash
  export BUN_INSTALL="$HOME/.bun"
  export PATH="$BUN_INSTALL/bin:$PATH"
  if command -v bun >/dev/null 2>&1; then
    success "bun installed"
  else
    error "bun install failed. install manually: https://bun.sh"
  fi
fi

# ─── check adb ───
if command -v adb >/dev/null 2>&1; then
  success "adb found"
else
  warn "adb not found — installing..."
  OS="$(uname -s)"
  case "$OS" in
    Darwin)
      if command -v brew >/dev/null 2>&1; then
        brew install --cask android-platform-tools
        success "adb installed via homebrew"
      else
        warn "homebrew not found. install adb manually:"
        warn "  brew install --cask android-platform-tools"
        warn "  or download from: https://developer.android.com/tools/releases/platform-tools"
      fi
      ;;
    Linux)
      if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update -qq && sudo apt-get install -y -qq android-tools-adb
        success "adb installed via apt"
      elif command -v dnf >/dev/null 2>&1; then
        sudo dnf install -y android-tools
        success "adb installed via dnf"
      elif command -v pacman >/dev/null 2>&1; then
        sudo pacman -S --noconfirm android-tools
        success "adb installed via pacman"
      else
        warn "could not auto-install adb. install manually:"
        warn "  https://developer.android.com/tools/releases/platform-tools"
      fi
      ;;
    *)
      warn "unknown os. install adb manually:"
      warn "  https://developer.android.com/tools/releases/platform-tools"
      ;;
  esac
fi

# ─── clone ───
if [ -d "$INSTALL_DIR" ]; then
  info "droidclaw directory exists, pulling latest..."
  cd "$INSTALL_DIR" && git pull --quiet && cd ..
else
  info "cloning droidclaw..."
  git clone --quiet "$REPO" "$INSTALL_DIR"
fi

# ─── install deps ───
cd "$INSTALL_DIR"
info "installing dependencies..."
bun install --silent

# ─── setup env ───
if [ ! -f .env ]; then
  cp .env.example .env
  success ".env created from .env.example"
else
  success ".env already exists, skipping"
fi

# ─── done ───
printf "\n${GREEN}${BOLD}done!${RESET}\n\n"
printf "next steps:\n\n"
printf "  ${BOLD}1.${RESET} configure an llm provider in ${CYAN}.env${RESET}\n\n"
printf "     ${DIM}# local with ollama (no api key needed)${RESET}\n"
printf "     ollama pull llama3.2\n"
printf "     ${DIM}# set in .env:${RESET} LLM_PROVIDER=ollama\n\n"
printf "     ${DIM}# or cloud with groq (free tier)${RESET}\n"
printf "     ${DIM}# set in .env:${RESET} LLM_PROVIDER=groq\n"
printf "     ${DIM}#             ${RESET} GROQ_API_KEY=gsk_...\n\n"
printf "  ${BOLD}2.${RESET} connect your android phone (usb debugging on)\n\n"
printf "     adb devices\n\n"
printf "  ${BOLD}3.${RESET} run it\n\n"
printf "     cd droidclaw && bun run src/kernel.ts\n\n"
printf "docs: ${CYAN}https://droidclaw.ai${RESET}\n"
printf "repo: ${CYAN}https://github.com/unitedbyai/droidclaw${RESET}\n\n"
