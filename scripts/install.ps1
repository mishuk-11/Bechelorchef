# One-click setup for a new Windows machine. Safe to run again (re-uses what is already there).
# - Downloads portable JDK 21, Node.js 24 and PostgreSQL 18 into .devtools\ only if missing (no admin rights needed)
# - Creates .env with random secrets and a random admin password (kept if .env already exists)
# - Creates a private PostgreSQL database in .devtools\pgdata (port 5433 by default)
# - Builds the API and the web app
. "$PSScriptRoot\common.ps1"

Write-Host 'Bachelor Chef - install' -ForegroundColor Yellow
Write-Host "Folder: $Root"

foreach ($exe in 'curl.exe', 'tar.exe') {
    if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { Fail "$exe is missing. Windows 10 (1803) or newer is required." }
}
New-Item -ItemType Directory -Force $Tools | Out-Null

# ---------------------------------------------------------------- 1. Java 21
Step 'Java 21'
if (Get-JavaHome) {
    Ok "Using $(Get-JavaHome)"
} else {
    Download-Zip $JdkUrl 'jdk21'
    if (-not (Get-JavaHome)) { Fail 'JDK download did not produce a jdk-21 folder.' }
    Ok "Installed $(Get-JavaHome)"
}

# ---------------------------------------------------------------- 2. Node.js
Step 'Node.js'
$systemNodeOk = $false
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if (-not (Get-NodeDir) -and $nodeCmd) {
    $v = (& node -v) -replace '^v', ''
    $parts = $v.Split('.')
    if ([int]$parts[0] -gt 20 -or ([int]$parts[0] -eq 20 -and [int]$parts[1] -ge 9)) { $systemNodeOk = $true; Ok "Using system Node.js v$v" }
}
if (-not $systemNodeOk) {
    if (-not (Get-NodeDir)) { Download-Zip $NodeUrl 'node' }
    Ok "Using $(Get-NodeDir)"
}

# ---------------------------------------------------------------- 3. PostgreSQL binaries
Step 'PostgreSQL'
if (-not (Get-PgBin)) {
    New-Item -ItemType Directory -Force $Tools | Out-Null
    $zip = Join-Path $Tools 'pgsql.zip'
    Info 'Downloading PostgreSQL binaries (about 300 MB) ...'
    & curl.exe -fSL --retry 3 -o $zip $PgUrl
    if ($LASTEXITCODE -ne 0) { Fail "Download failed: $PgUrl" }
    Info 'Extracting ...'
    & tar.exe -xf $zip -C $Tools --exclude 'pgsql/pgAdmin 4/*' --exclude 'pgsql/doc/*' --exclude 'pgsql/StackBuilder/*' --exclude 'pgsql/symbols/*'
    if ($LASTEXITCODE -ne 0) { Fail 'Could not extract PostgreSQL.' }
    Remove-Item $zip -Force
}
Ok "Using $(Get-PgBin)"

# ---------------------------------------------------------------- 4. .env
Step 'Configuration (.env)'
function Set-Line([string]$text, [string]$key, [string]$value) {
    $pattern = "(?m)^$([regex]::Escape($key))=.*$"
    if ($text -match $pattern) { return [regex]::Replace($text, $pattern, "$key=$value") }
    return $text.TrimEnd() + "`r`n$key=$value`r`n"
}
if (Test-Path $EnvFile) {
    Ok '.env already exists - keeping your settings'
} else {
    $text = [IO.File]::ReadAllText((Join-Path $Root '.env.example'))
    $dbPassword = New-Secret 24
    $adminPassword = New-Secret 16
    $text = Set-Line $text 'LOCAL_PG_PORT' '5433'
    $text = Set-Line $text 'POSTGRES_PASSWORD' $dbPassword
    $text = Set-Line $text 'SPRING_DATASOURCE_PASSWORD' $dbPassword
    $text = Set-Line $text 'SPRING_DATASOURCE_URL' 'jdbc:postgresql://localhost:5433/bachelor_chef'
    $text = Set-Line $text 'APP_JWT_ACCESS_SECRET' (New-Secret 48)
    $text = Set-Line $text 'BOOTSTRAP_ADMIN_PASSWORD' $adminPassword
    $text = Set-Line $text 'E2E_ADMIN_PASSWORD' $adminPassword
    [IO.File]::WriteAllText($EnvFile, $text, (New-Object System.Text.UTF8Encoding($false)))
    Ok 'Created .env with random passwords and secrets'
}
Import-DotEnv
Use-Tools

# ---------------------------------------------------------------- 5. Database
Step 'Database'
$pgBin = Get-PgBin
$dbName = Get-EnvValue 'POSTGRES_DB' 'bachelor_chef'
$dbUser = Get-EnvValue 'POSTGRES_USER' 'bachelor_chef_app'
$dbPassword = Get-EnvValue 'SPRING_DATASOURCE_PASSWORD'
if (-not (Test-Path $PgData)) {
    $superPassword = New-Secret 24
    [IO.File]::WriteAllText($PgPwFile, $superPassword, (New-Object System.Text.UTF8Encoding($false)))
    Info 'Initialising a private PostgreSQL cluster ...'
    & "$pgBin\initdb.exe" -D $PgData -U postgres -A scram-sha-256 "--pwfile=$PgPwFile" -E UTF8 --locale=C | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'initdb failed.' }
}
if (-not (Test-Path $PgPwFile)) { Fail "$PgPwFile is missing; cannot manage the local database. Delete .devtools\pgdata and run install.bat again." }
Start-LocalPostgres | Out-Null
$env:PGPASSWORD = ([IO.File]::ReadAllText($PgPwFile)).Trim()
$port = Get-PgPort
$psql = "$pgBin\psql.exe"
if ($dbUser -notmatch '^[a-z_][a-z0-9_]*$' -or $dbName -notmatch '^[a-z_][a-z0-9_]*$') { Fail 'POSTGRES_USER and POSTGRES_DB must be lower-case letters, digits and underscores.' }
if ($dbPassword -match "'") { Fail 'SPRING_DATASOURCE_PASSWORD must not contain single quotes.' }
$roleSql = 'DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = ''' + $dbUser + ''') THEN CREATE ROLE ' + $dbUser + ' LOGIN; END IF; END $$; ALTER ROLE ' + $dbUser + ' WITH LOGIN PASSWORD ''' + $dbPassword + ''';'
& $psql -h localhost -p $port -U postgres -d postgres -q -c $roleSql
if ($LASTEXITCODE -ne 0) { Fail 'Could not create the database role.' }
$exists = & $psql -h localhost -p $port -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$dbName'"
if ("$exists".Trim() -ne '1') {
    & $psql -h localhost -p $port -U postgres -d postgres -q -c "CREATE DATABASE $dbName OWNER $dbUser"
    if ($LASTEXITCODE -ne 0) { Fail 'Could not create the database.' }
}
Remove-Item Env:\PGPASSWORD
Ok "Database '$dbName' ready on port $port"

# ---------------------------------------------------------------- 6. Build API
Step 'Building the API (first run downloads Maven libraries, a few minutes)'
Push-Location (Join-Path $Root 'backend')
try {
    & .\mvnw.cmd -q -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { Fail 'API build failed (see messages above).' }
} finally { Pop-Location }
Ok 'backend\target\bachelor-chef-api.jar'

# ---------------------------------------------------------------- 7. Build web app
Step 'Building the web app'
$frontend = Join-Path $Root 'frontend'
$apiPort = Get-EnvValue 'SERVER_PORT' '8080'
$localEnv = Join-Path $frontend '.env.local'
if (-not (Test-Path $localEnv)) {
    $content = "NEXT_PUBLIC_API_BASE_URL=/api/v1`r`nAPI_PROXY_TARGET=http://localhost:$apiPort`r`n"
    [IO.File]::WriteAllText($localEnv, $content, (New-Object System.Text.UTF8Encoding($false)))
}
Push-Location $frontend
try {
    & npm.cmd ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { Fail 'npm ci failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { Fail 'Web app build failed.' }
} finally { Pop-Location }
Ok 'frontend\.next'

# ---------------------------------------------------------------- done
$pgBinStop = Get-PgBin
& "$pgBinStop\pg_ctl.exe" stop -D $PgData -m fast | Out-Null

Write-Host "`nInstall complete." -ForegroundColor Green
Write-Host "Start the app with run.bat. Admin login (also stored in .env):"
Write-Host "    Email:    $(Get-EnvValue 'BOOTSTRAP_ADMIN_EMAIL')"
Write-Host "    Password: $(Get-EnvValue 'BOOTSTRAP_ADMIN_PASSWORD')"
Write-Host "After pulling new code, run install.bat again to rebuild."
