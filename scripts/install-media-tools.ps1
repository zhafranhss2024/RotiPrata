$ErrorActionPreference = "Stop"

Write-Host "Installing media tools (ffmpeg, ffprobe, yt-dlp)..."

function Install-WithWinget {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) { return $false }
    winget install --id Gyan.FFmpeg -e --accept-source-agreements --accept-package-agreements
    winget install --id yt-dlp.yt-dlp -e --accept-source-agreements --accept-package-agreements
    return $true
}

function Install-WithChoco {
    if (-not (Get-Command choco -ErrorAction SilentlyContinue)) { return $false }
    choco install ffmpeg yt-dlp -y
    return $true
}

function Find-Executable([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $wingetRoot = Join-Path $env:LOCALAPPDATA "Microsoft\\WinGet\\Packages"
    if (Test-Path $wingetRoot) {
        $found = Get-ChildItem -Path $wingetRoot -Recurse -Filter "$name.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    return $null
}

function Set-UserEnv([string]$key, [string]$value) {
    [Environment]::SetEnvironmentVariable($key, $value, "User")
    Set-Item -Path "Env:$key" -Value $value
}

function Add-ToUserPath([string]$dir) {
    if (-not $dir) { return }
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if (-not $userPath) { $userPath = "" }
    $parts = $userPath.Split(";", [System.StringSplitOptions]::RemoveEmptyEntries)
    if ($parts -notcontains $dir) {
        $newPath = ($parts + $dir) -join ";"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
        $env:Path = ($env:Path.TrimEnd(";") + ";" + $dir).TrimStart(";")
    }
}

function Update-EnvFile([string]$path, [string]$key, [string]$value) {
    if (-not (Test-Path $path)) { return }
    $lines = Get-Content $path -ErrorAction SilentlyContinue
    $pattern = "^\s*$([Regex]::Escape($key))="
    $entry = "$key=$value"
    $updated = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $pattern) {
            $lines[$i] = $entry
            $updated = $true
        }
    }
    if (-not $updated) {
        $lines += ""
        $lines += $entry
    }
    Set-Content -Path $path -Value $lines
}

$installed = $false
if (Install-WithWinget) {
    $installed = $true
    Write-Host "Installed via winget."
} elseif (Install-WithChoco) {
    $installed = $true
    Write-Host "Installed via Chocolatey."
}

if (-not $installed) {
    Write-Host "Neither winget nor choco found. Install one and re-run."
    Write-Host "Winget: https://learn.microsoft.com/windows/package-manager/winget/"
    Write-Host "Chocolatey: https://chocolatey.org/install"
    exit 1
}

$ffmpegPath = Find-Executable "ffmpeg"
$ffprobePath = Find-Executable "ffprobe"
$ytdlpPath = Find-Executable "yt-dlp"

if (-not $ffmpegPath -or -not $ffprobePath -or -not $ytdlpPath) {
    Write-Host "Install completed, but some tools were not found on disk."
    Write-Host "ffmpeg: $ffmpegPath"
    Write-Host "ffprobe: $ffprobePath"
    Write-Host "yt-dlp: $ytdlpPath"
    Write-Host "You may need to re-open the terminal or adjust PATH manually."
    exit 1
}

Set-UserEnv "FFMPEG_PATH" $ffmpegPath
Set-UserEnv "FFPROBE_PATH" $ffprobePath
Set-UserEnv "YTDLP_PATH" $ytdlpPath

Add-ToUserPath (Split-Path $ffmpegPath)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $repoRoot ".env"
Update-EnvFile $envFile "FFMPEG_PATH" $ffmpegPath
Update-EnvFile $envFile "FFPROBE_PATH" $ffprobePath
Update-EnvFile $envFile "YTDLP_PATH" $ytdlpPath

Write-Host "Done. Environment variables set (User) and .env updated."
Write-Host "If the server still cannot find tools, open a new terminal and re-run mvn."
exit 0
