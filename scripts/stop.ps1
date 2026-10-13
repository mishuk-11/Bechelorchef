# Stops the web app, the API and the private database started by run.bat (only this project's processes).
. "$PSScriptRoot\common.ps1"

Write-Host 'Bachelor Chef - stop' -ForegroundColor Yellow
$frontend = Join-Path $Root 'frontend'
$processes = Get-CimInstance Win32_Process

# Web app: the "Bachelor Chef web" console window and everything it started (npm -> next).
foreach ($p in $processes | Where-Object { $_.Name -eq 'cmd.exe' -and $_.CommandLine -like '*title Bachelor Chef web*' }) {
    cmd /c "taskkill /T /F /PID $($p.ProcessId) >nul 2>&1"
    Ok 'Stopped the web app'
}
foreach ($p in $processes | Where-Object { $_.Name -eq 'node.exe' -and $_.CommandLine -like "*$frontend*next*start*" }) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}

# API: the java process running this project's jar.
foreach ($p in $processes | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*bachelor-chef-api.jar*' }) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    Ok "Stopped the API (pid $($p.ProcessId))"
}

# Private database.
if (Test-PostgresRunning) {
    & "$(Get-PgBin)\pg_ctl.exe" stop -D $PgData -m fast | Out-Null
    Ok 'Stopped PostgreSQL'
}
Write-Host 'Done.' -ForegroundColor Green
