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

Write-Host "Ensuring prerequisites (Java, Maven, Node.js)..."
Ensure-Command -Name "java" -WingetId "Microsoft.OpenJDK.17" -ChocoId "openjdk17"
Ensure-Command -Name "mvn" -WingetId "Apache.Maven" -ChocoId "maven"
Ensure-Command -Name "node" -WingetId "OpenJS.NodeJS.LTS" -ChocoId "nodejs-lts"
Ensure-Command -Name "npm" -WingetId "" -ChocoId ""

Write-Host "Installing media tools..."
& (Join-Path $PSScriptRoot "install-media-tools.ps1")

Write-Host "Starting backend + frontend in separate shells..."
$backendCmd = "cd `"$repoRoot`"; mvn spring-boot:run"
$frontendCmd = "cd `"$repoRoot\\frontend`"; npm install; npm run dev"

Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCmd
Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendCmd

Write-Host "Done. Backend and frontend are starting in separate windows."
