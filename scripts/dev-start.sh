#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

ensure_cmd() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

ensure_brew() {
  if ! command -v brew >/dev/null 2>&1; then
    echo "Homebrew is required. Install from https://brew.sh and re-run."
    exit 1
  fi
}

install_if_missing() {
  local cmd="$1"
  local brew_pkg="$2"
  if ! ensure_cmd "$cmd"; then
    ensure_brew
    echo "Installing $cmd via brew..."
    brew install "$brew_pkg"
  fi
}

get_node_version() {
  node -v 2>/dev/null | sed 's/^v//'
}

node_version_supported() {
  local version="$1"
  local major minor patch
  IFS='.' read -r major minor patch <<<"$version"
  if [[ -z "${major:-}" || -z "${minor:-}" ]]; then
    return 1
  fi
  if (( major == 20 )); then
    (( minor >= 19 )) && return 0
    return 1
  fi
  if (( major == 22 )); then
    (( minor >= 12 )) && return 0
    return 1
  fi
  if (( major > 22 )); then
    return 0
  fi
  return 1
}

ensure_node_version() {
  local version
  version="$(get_node_version)"
  if [[ -z "$version" ]]; then
    echo "Node.js is required but was not found. Install Node 20.19+ or 22.12+ and re-run."
    exit 1
  fi
  if node_version_supported "$version"; then
    return 0
  fi

  echo "Node.js $version detected, but the frontend requires Node 20.19+ or 22.12+."

  if command -v brew >/dev/null 2>&1; then
    local brew_prefix
    brew_prefix="$(brew --prefix 2>/dev/null || true)"
    if [[ -n "$brew_prefix" && -w "$brew_prefix/Cellar" ]]; then
      echo "Attempting to install a supported Node version via Homebrew..."
      if ! brew install node@22; then
        if ! brew install node; then
          brew upgrade node || true
        fi
      fi

      if [[ -x "$brew_prefix/opt/node@22/bin/node" ]]; then
        export PATH="$brew_prefix/opt/node@22/bin:$PATH"
      elif [[ -x "$brew_prefix/opt/node/bin/node" ]]; then
        export PATH="$brew_prefix/opt/node/bin:$PATH"
      fi

      version="$(get_node_version)"
      if node_version_supported "$version"; then
        return 0
      fi
    elif [[ -n "$brew_prefix" ]]; then
      echo "Homebrew Cellar is not writable: $brew_prefix/Cellar"
      echo "Fix with: sudo chown -R $(whoami) \"$brew_prefix/Cellar\""
    fi
  fi

  echo "Please install Node 20.19+ or 22.12+ and re-run this script."
  exit 1
}

echo "Ensuring prerequisites (Java, Maven, Node.js)..."
install_if_missing "java" "openjdk@17"
install_if_missing "mvn" "maven"
install_if_missing "node" "node"
install_if_missing "npm" "node"
ensure_node_version

if ! /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
  ensure_brew
  echo "Installing OpenJDK 17..."
  brew install openjdk@17
fi
export JAVA_HOME="$("/usr/libexec/java_home" -v 17 2>/dev/null || true)"
if [[ -n "$JAVA_HOME" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "Installing media tools..."
xattr -dr com.apple.quarantine "$repo_root/scripts/install-media-tools.sh" 2>/dev/null || true
bash "$repo_root/scripts/install-media-tools.sh"

echo "Starting backend + frontend..."
if command -v osascript >/dev/null 2>&1; then
  if osascript <<EOF
tell application "Terminal"
  do script "cd \"$repo_root\"; mvn spring-boot:run"
  do script "cd \"$repo_root/frontend\"; npm install; npm run dev"
end tell
EOF
  then
    echo "Done. Backend and frontend are starting in Terminal windows."
    exit 0
  else
    echo "osascript failed. Falling back to background/foreground startup."
  fi
else
  echo "osascript not available. Starting backend in background and frontend in foreground."
fi

(cd "$repo_root" && mvn spring-boot:run) &
cd "$repo_root/frontend"
npm install
npm run dev
