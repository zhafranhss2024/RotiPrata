#!/usr/bin/env bash
set -euo pipefail

echo "Installing media tools (ffmpeg, ffprobe, yt-dlp)..."

update_env_file() {
  local file="$1"
  local key="$2"
  local value="$3"

  [[ -f "$file" ]] || return 0

  local tmp="${file}.tmp.$$"
  local found=0
  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ ^[[:space:]]*${key}= ]]; then
      echo "${key}=${value}" >> "$tmp"
      found=1
    else
      echo "$line" >> "$tmp"
    fi
  done < "$file"

  if [[ $found -eq 0 ]]; then
    echo "" >> "$tmp"
    echo "${key}=${value}" >> "$tmp"
  fi

  mv "$tmp" "$file"
}

set_env_paths() {
  local repo_root
  repo_root="$(cd "$(dirname "$0")/.." && pwd)"
  local env_file="${repo_root}/.env"

  local ffmpeg_path
  local ffprobe_path
  local ytdlp_path
  ffmpeg_path="$(command -v ffmpeg || true)"
  ffprobe_path="$(command -v ffprobe || true)"
  ytdlp_path="$(command -v yt-dlp || true)"

  if [[ -z "$ffmpeg_path" || -z "$ffprobe_path" || -z "$ytdlp_path" ]]; then
    echo "Install completed, but some tools were not found on PATH."
    echo "ffmpeg: ${ffmpeg_path:-missing}"
    echo "ffprobe: ${ffprobe_path:-missing}"
    echo "yt-dlp: ${ytdlp_path:-missing}"
    echo "Please open a new terminal or adjust PATH manually."
    exit 1
  fi

  update_env_file "$env_file" "FFMPEG_PATH" "$ffmpeg_path"
  update_env_file "$env_file" "FFPROBE_PATH" "$ffprobe_path"
  update_env_file "$env_file" "YTDLP_PATH" "$ytdlp_path"

  echo "Updated .env with FFMPEG_PATH/FFPROBE_PATH/YTDLP_PATH."
  echo "If the server still cannot find tools, open a new terminal and re-run mvn."
}

update_ytdlp() {
  if ! command -v yt-dlp >/dev/null 2>&1; then
    return 0
  fi

  echo "Updating yt-dlp..."
  if yt-dlp -U >/dev/null 2>&1; then
    return 0
  fi

  if command -v python3 >/dev/null 2>&1; then
    echo "yt-dlp self-update failed; trying pip fallback..."
    python3 -m pip install -U yt-dlp
    return 0
  fi

  if command -v python >/dev/null 2>&1; then
    echo "yt-dlp self-update failed; trying pip fallback..."
    python -m pip install -U yt-dlp
    return 0
  fi
}

OS_NAME="$(uname -s | tr '[:upper:]' '[:lower:]')"

if [[ "$OS_NAME" == "darwin" ]]; then
  if ! command -v brew >/dev/null 2>&1; then
    echo "Homebrew is required. Install from https://brew.sh and re-run."
    exit 1
  fi
  brew update
  brew install ffmpeg yt-dlp
  brew upgrade yt-dlp || true
  update_ytdlp
  set_env_paths
  echo "Done."
  exit 0
fi

if [[ "$OS_NAME" == "linux" ]]; then
  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y ffmpeg python3 python3-pip
    python3 -m pip install -U yt-dlp
    update_ytdlp
    set_env_paths
    echo "Done."
    exit 0
  fi

  if command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y ffmpeg python3 python3-pip || true
    python3 -m pip install -U yt-dlp
    update_ytdlp
    set_env_paths
    echo "Done (ffmpeg may require RPM Fusion on some distros)."
    exit 0
  fi

  if command -v yum >/dev/null 2>&1; then
    sudo yum install -y ffmpeg python3 python3-pip || true
    python3 -m pip install -U yt-dlp
    update_ytdlp
    set_env_paths
    echo "Done (ffmpeg may require EPEL/RPM Fusion on some distros)."
    exit 0
  fi

  if command -v apk >/dev/null 2>&1; then
    sudo apk add --no-cache ffmpeg python3 py3-pip
    python3 -m pip install -U yt-dlp
    update_ytdlp
    set_env_paths
    echo "Done."
    exit 0
  fi

  if command -v pacman >/dev/null 2>&1; then
    sudo pacman -Sy --noconfirm ffmpeg python-pip
    python -m pip install -U yt-dlp
    update_ytdlp
    set_env_paths
    echo "Done."
    exit 0
  fi

  if command -v zypper >/dev/null 2>&1; then
    sudo zypper install -y ffmpeg python3 python3-pip || true
    python3 -m pip install -U yt-dlp
    update_ytdlp
    set_env_paths
    echo "Done (ffmpeg may require Packman repo on some distros)."
    exit 0
  fi

  echo "Unsupported Linux distro. Install ffmpeg/ffprobe and yt-dlp manually."
  exit 1
fi

echo "Unsupported OS: $OS_NAME"
exit 1
