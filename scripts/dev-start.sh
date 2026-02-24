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

check_maven_repo_access() {
  local settings_file="$repo_root/.mvn/settings.xml"
  local maven_args=("-q" "-DforceStdout" "help:evaluate" "-Dexpression=settings.localRepository")

  if [[ -f "$settings_file" ]]; then
    maven_args=("-s" "$settings_file" "${maven_args[@]}")
  fi

  if mvn "${maven_args[@]}" >/dev/null 2>&1; then
    return 0
  fi

  cat <<'EOF'
Maven could not reach dependency repositories.
If you are behind a corporate/school proxy, add a project-level mirror in `.mvn/settings.xml`.
Example:

<settings>
  <mirrors>
    <mirror>
      <id>internal-repo</id>
      <name>Internal Maven Mirror</name>
      <url>https://YOUR_MIRROR/repository/maven-public/</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>

Then re-run `bash ./scripts/dev-start.sh`.
EOF
  exit 1
}

echo "Ensuring prerequisites (Java, Maven, Node.js)..."
install_if_missing "java" "openjdk@17"
install_if_missing "mvn" "maven"
install_if_missing "node" "node"
install_if_missing "npm" "node"

if ! /usr/libexec/java_home -v 17 >/dev/null 2>&1 && ! /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  ensure_brew
  echo "Installing OpenJDK 17..."
  brew install openjdk@17
fi

JAVA_HOME="$(resolve_java_home)"
if [[ -z "$JAVA_HOME" ]]; then
  echo "Unable to locate a compatible JDK (17 or 21)."
  echo "Install one and re-run: brew install openjdk@17"
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

echo "Installing media tools..."
xattr -dr com.apple.quarantine "$repo_root/scripts/install-media-tools.sh" 2>/dev/null || true
bash "$repo_root/scripts/install-media-tools.sh"

echo "Checking Maven repository access..."
check_maven_repo_access

echo "Starting backend + frontend..."
if command -v osascript >/dev/null 2>&1; then
  osascript <<EOF
tell application "Terminal"
  do script "cd \"$repo_root\"; export JAVA_HOME=\"$JAVA_HOME\"; export PATH=\"$JAVA_HOME/bin:\$PATH\"; mvn spring-boot:run"
  do script "cd \"$repo_root/frontend\"; npm install; npm run dev"
end tell
EOF
  echo "Done. Backend and frontend are starting in Terminal windows."
else
  echo "osascript not available. Starting backend in background and frontend in foreground."
  (cd "$repo_root" && mvn spring-boot:run) &
  cd "$repo_root/frontend"
  npm install
  npm run dev
fi
