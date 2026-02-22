$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

function Refresh-Path {
    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($machinePath -and $userPath) {
        $env:Path = "$machinePath;$userPath"
    } elseif ($machinePath) {
        $env:Path = $machinePath
    } elseif ($userPath) {
        $env:Path = $userPath
    }
}

function Ensure-Command {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [string]$WingetId,
        [string]$ChocoId
    )

    if (Get-Command $Name -ErrorAction SilentlyContinue) { return }

    if ($WingetId -and (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Host "Installing $Name via winget..."
        winget install --id $WingetId -e --accept-source-agreements --accept-package-agreements
        Refresh-Path
        if (Get-Command $Name -ErrorAction SilentlyContinue) { return }
    }

    if ($ChocoId -and (Get-Command choco -ErrorAction SilentlyContinue)) {
        Write-Host "Installing $Name via Chocolatey..."
        choco install $ChocoId -y
        Refresh-Path
        if (Get-Command $Name -ErrorAction SilentlyContinue) { return }
    }

    throw "Required command '$Name' was not found. Install it and re-run this script."
}

function Test-NodeVersion {
    param([Parameter(Mandatory=$true)][string]$Version)
    try {
        $parsed = [version]$Version.TrimStart("v")
    } catch {
        return $false
    }

    if ($parsed.Major -eq 20) { return $parsed.Minor -ge 19 }
    if ($parsed.Major -eq 22) { return $parsed.Minor -ge 12 }
    if ($parsed.Major -gt 22) { return $true }
    return $false
}

function Ensure-NodeVersion {
    $version = (node -v).Trim()
    if (Test-NodeVersion -Version $version) { return }

    Write-Host "Node.js $version detected, but the frontend requires Node 20.19+ or 22.12+."

    if (Get-Command winget -ErrorAction SilentlyContinue) {
        try {
            Write-Host "Attempting to install Node LTS via winget..."
            winget install --id OpenJS.NodeJS.LTS -e --accept-source-agreements --accept-package-agreements --force | Out-Null
            Refresh-Path
            $version = (node -v).Trim()
            if (Test-NodeVersion -Version $version) { return }
        } catch {
            Write-Host "winget install failed. Continuing..."
        }
    }

    if (Get-Command choco -ErrorAction SilentlyContinue) {
        try {
            Write-Host "Attempting to install Node LTS via Chocolatey..."
            choco install nodejs-lts -y | Out-Null
            Refresh-Path
            $version = (node -v).Trim()
            if (Test-NodeVersion -Version $version) { return }
        } catch {
            Write-Host "Chocolatey install failed. Continuing..."
        }
    }

    throw "Please install Node 20.19+ or 22.12+ and re-run this script."
}

Write-Host "Ensuring prerequisites (Java, Maven, Node.js)..."
Ensure-Command -Name "java" -WingetId "Microsoft.OpenJDK.17" -ChocoId "openjdk17"
Ensure-Command -Name "mvn" -WingetId "Apache.Maven" -ChocoId "maven"
Ensure-Command -Name "node" -WingetId "OpenJS.NodeJS.LTS" -ChocoId "nodejs-lts"
Ensure-Command -Name "npm" -WingetId "" -ChocoId ""
Ensure-NodeVersion

Write-Host "Installing media tools..."
& (Join-Path $PSScriptRoot "install-media-tools.ps1")

Write-Host "Starting backend + frontend in separate shells..."
$backendCmd = "cd `"$repoRoot`"; mvn spring-boot:run"
$frontendCmd = "cd `"$repoRoot\\frontend`"; npm install; npm run dev"

Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCmd
Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendCmd

Write-Host "Done. Backend and frontend are starting in separate windows."
