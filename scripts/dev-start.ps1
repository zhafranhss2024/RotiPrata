$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$backendWorkingDir = $repoRoot.Path
$frontendWorkingDir = (Join-Path $repoRoot.Path "frontend")
$powershellExe = (Get-Command powershell.exe -ErrorAction Stop).Source

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
$backendArgs = @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-Command", "mvn spring-boot:run"
)
$frontendArgs = @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-Command", "npm install; npm run dev"
)

Start-Process -FilePath $powershellExe -WorkingDirectory $backendWorkingDir -ArgumentList $backendArgs -WindowStyle Normal
Start-Process -FilePath $powershellExe -WorkingDirectory $frontendWorkingDir -ArgumentList $frontendArgs -WindowStyle Normal

Write-Host "Done. Backend and frontend are starting in separate windows."
