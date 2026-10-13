# One-click start: database, API and web app, then opens the browser.
. "$PSScriptRoot\common.ps1"

Write-Host 'Bachelor Chef - start' -ForegroundColor Yellow
Import-DotEnv
Use-Tools

$jar = Join-Path $Root 'backend\target\bachelor-chef-api.jar'
$frontend = Join-Path $Root 'frontend'
if (-not (Test-Path $jar) -or -not (Test-Path (Join-Path $frontend '.next\BUILD_ID'))) {
    Fail 'The app is not built yet. Run install.bat first.'
}

$apiPort = [int](Get-EnvValue 'SERVER_PORT' '8080')
$webPort = 3000
$apiUrl = "http://localhost:$apiPort/actuator/health"
$webUrl = "http://localhost:$webPort"

# The API only accepts browser requests from listed origins. Allow every address this PC answers on
# (localhost, 127.0.0.1, LAN IPs for phones on the same Wi-Fi) so login works however the site is opened.
$origins = @((Get-EnvValue 'APP_FRONTEND_ORIGINS' $webUrl) -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$hostNames = @('localhost', '127.0.0.1') + @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' } | ForEach-Object { $_.IPAddress })
$origins += $hostNames | ForEach-Object { "http://${_}:$webPort" }
$env:APP_FRONTEND_ORIGINS = ($origins | Select-Object -Unique) -join ','

Step 'Database'
if (-not (Start-LocalPostgres)) { Info 'No private database in .devtools - using SPRING_DATASOURCE_URL from .env' }

Step 'API'
if (Test-PortInUse $apiPort) {
    if (Wait-Http $apiUrl 3) { Ok "Already running on port $apiPort" }
    else { Fail "Port $apiPort is used by another program. Close it or change SERVER_PORT in .env." }
} else {
    Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList '-jar', "`"$jar`"" `
        -WorkingDirectory (Join-Path $Root 'backend') | Out-Null
    Info 'Starting (a new window shows the API log) ...'
    if (-not (Wait-Http $apiUrl 180)) { Fail 'The API did not become healthy within 3 minutes. Check the API window for errors.' }
    Ok "Running: http://localhost:$apiPort  (API docs: /swagger-ui.html)"
}

Step 'Web app'
if (Test-PortInUse $webPort) {
    if (Wait-Http $webUrl 3) { Ok "Already running on port $webPort" }
    else { Fail "Port $webPort is used by another program. Close it first." }
} else {
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', "title Bachelor Chef web && npm.cmd run start -- -p $webPort" `
        -WorkingDirectory $frontend | Out-Null
    if (-not (Wait-Http $webUrl 90)) { Fail 'The web app did not start. Check the web window for errors.' }
    Ok "Running: $webUrl"
}

Start-Process $webUrl
Write-Host "`nBachelor Chef is running at $webUrl" -ForegroundColor Green
Write-Host "Admin login: $(Get-EnvValue 'BOOTSTRAP_ADMIN_EMAIL') (password in .env: BOOTSTRAP_ADMIN_PASSWORD)"
Write-Host 'To stop everything, run stop.bat.'
