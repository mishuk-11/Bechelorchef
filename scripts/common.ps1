# Shared helpers for install.ps1 / run.ps1 / stop.ps1 (Windows PowerShell 5.1+).
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster

$Root     = Split-Path -Parent $PSScriptRoot
$Tools    = Join-Path $Root '.devtools'
$PgData   = Join-Path $Tools 'pgdata'
$PgLog    = Join-Path $Tools 'postgres.log'
$PgPwFile = Join-Path $Tools 'pgpw.txt'
$EnvFile  = Join-Path $Root '.env'

# Pinned portable downloads (no installer, no admin rights)
$NodeVersion = '24.21.0'
$NodeUrl     = "https://nodejs.org/dist/v$NodeVersion/node-v$NodeVersion-win-x64.zip"
$PgUrl       = 'https://get.enterprisedb.com/postgresql/postgresql-18.1-1-windows-x64-binaries.zip'
$JdkUrl      = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'

function Step($text)  { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Ok($text)    { Write-Host "    $text" -ForegroundColor Green }
function Info($text)  { Write-Host "    $text" }
function Fail($text)  { Write-Host "`nERROR: $text" -ForegroundColor Red; exit 1 }

# Random letters/digits (cryptographically secure).
function New-Secret([int]$length = 32) {
    $buf = New-Object byte[] 64
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buf)
    $text = [Convert]::ToBase64String($buf) -replace '[+/=]', ''
    return $text.Substring(0, $length)
}

# Reads KEY=VALUE lines from .env into the current process (inline "  # comments" removed).
function Import-DotEnv {
    if (-not (Test-Path $EnvFile)) { Fail ".env not found. Run install.bat first." }
    foreach ($line in [IO.File]::ReadAllLines($EnvFile)) {
        $l = $line.TrimStart([char]0xFEFF).Trim()
        if ($l -eq '' -or $l.StartsWith('#') -or -not $l.Contains('=')) { continue }
        $key, $value = $l -split '=', 2
        $value = ($value -replace '\s+#.*$', '').Trim()
        [Environment]::SetEnvironmentVariable($key.Trim(), $value, 'Process')
    }
}

function Get-EnvValue([string]$key, [string]$default = '') {
    $v = [Environment]::GetEnvironmentVariable($key, 'Process')
    if ([string]::IsNullOrWhiteSpace($v)) { return $default } else { return $v }
}

function Find-Dir([string]$filter) {
    if (-not (Test-Path $Tools)) { return $null }
    return Get-ChildItem $Tools -Directory -Filter $filter -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
}

# Java 21+: portable JDK in .devtools, else a system JDK (JAVA_HOME or java on PATH).
function Get-JavaHome {
    $portable = Find-Dir 'jdk-21*'
    if ($portable) { return $portable.FullName }
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += (Split-Path -Parent (Split-Path -Parent $cmd.Source)) }
    foreach ($jh in $candidates) {
        $java = Join-Path $jh 'bin\java.exe'
        $javac = Join-Path $jh 'bin\javac.exe'
        if (-not (Test-Path $java) -or -not (Test-Path $javac)) { continue }
        $out = cmd /c "`"$java`" -version 2>&1" | Out-String
        if ($out -match 'version "(\d+)') { if ([int]$Matches[1] -ge 21) { return $jh } }
    }
    return $null
}

function Get-NodeDir { $d = Find-Dir 'node-v*-win-x64'; if ($d) { return $d.FullName } return $null }
function Get-PgBin   { $p = Join-Path $Tools 'pgsql\bin'; if (Test-Path (Join-Path $p 'pg_ctl.exe')) { return $p } return $null }

# Puts the tools first on PATH for this process (and child windows).
function Use-Tools {
    $javaHome = Get-JavaHome
    if (-not $javaHome) { Fail 'Java 21+ not found. Run install.bat first.' }
    $env:JAVA_HOME = $javaHome
    $paths = @("$javaHome\bin")
    $node = Get-NodeDir
    if ($node) { $paths += $node }
    $pg = Get-PgBin
    if ($pg) { $paths += $pg }
    $env:Path = ($paths -join ';') + ';' + $env:Path
    $env:npm_config_cache = Join-Path $Tools 'npm-cache'
    $env:NEXT_TELEMETRY_DISABLED = '1'
}

function Download-Zip([string]$url, [string]$name) {
    New-Item -ItemType Directory -Force $Tools | Out-Null
    $zip = Join-Path $Tools "$name.zip"
    Info "Downloading $name ..."
    & curl.exe -fSL --retry 3 -o $zip $url
    if ($LASTEXITCODE -ne 0) { Fail "Download failed: $url" }
    Info "Extracting $name ..."
    & tar.exe -xf $zip -C $Tools
    if ($LASTEXITCODE -ne 0) { Fail "Could not extract $zip" }
    Remove-Item $zip -Force
}

function Test-PortInUse([int]$port) {
    return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Http([string]$url, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 $url
            if ($r.StatusCode -lt 500) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Get-PgPort { return [int](Get-EnvValue 'LOCAL_PG_PORT' '5433') }

function Test-PostgresRunning {
    $pgBin = Get-PgBin
    if (-not $pgBin -or -not (Test-Path $PgData)) { return $false }
    cmd /c "`"$pgBin\pg_ctl.exe`" status -D `"$PgData`" >nul 2>&1"
    return ($LASTEXITCODE -eq 0)
}

function Start-LocalPostgres {
    $pgBin = Get-PgBin
    if (-not $pgBin -or -not (Test-Path $PgData)) { return $false }   # using an external PostgreSQL
    if (Test-PostgresRunning) { Ok 'PostgreSQL already running'; return $true }
    $port = Get-PgPort
    if (Test-PortInUse $port) { Fail "Port $port is used by another program. Change LOCAL_PG_PORT and the port in SPRING_DATASOURCE_URL in .env." }
    # Start-Process (no pipes) and wait for pg_ctl only: postgres keeps running and would otherwise
    # hold any inherited PowerShell pipe open, so the call would never return.
    $proc = Start-Process -FilePath "$pgBin\pg_ctl.exe" -NoNewWindow -PassThru `
        -ArgumentList 'start', '-w', '-D', "`"$PgData`"", '-l', "`"$PgLog`"", '-o', "`"-p $port`""
    $null = $proc.Handle   # required so ExitCode is available after WaitForExit
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) { Fail "PostgreSQL did not start. See $PgLog" }
    Ok "PostgreSQL running on port $port"
    return $true
}
