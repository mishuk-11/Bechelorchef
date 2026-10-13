<# : admin.bat - creates the Bachelor Chef demo accounts (3 admins + 10 users, password 123456)
@echo off
setlocal
set "ADMINBAT=%~f0"
set "ADMINBAT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([IO.File]::ReadAllText($env:ADMINBAT))"
set EXITCODE=%ERRORLEVEL%
echo.
pause
exit /b %EXITCODE%
#>

# ---------------------------------------------------------------------------------------------
# Creates (or resets) the local demo accounts in the project's database:
#   admins: inzamam@admin.su, cherry@admin.su, saima@admin.su
#   users : user1@user.com ... user10@user.com
#   password for all: 123456  (LOCAL TESTING ONLY - never use these accounts on a public site)
# Running it again is safe: existing demo accounts get password 123456, their role and ACTIVE status back.
# Place this file next to the "Bechelorchef" folder (or inside it). Needs install.bat to have run once
# and the app to have started once (run.bat) so the database tables exist.
# ---------------------------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$dir = $env:ADMINBAT_DIR
$proj = @((Join-Path $dir 'Bechelorchef'), $dir) | Where-Object { Test-Path (Join-Path $_ 'scripts\common.ps1') } | Select-Object -First 1
if (-not $proj) {
    Write-Host 'ERROR: admin.bat must be next to the Bechelorchef folder (or inside it).' -ForegroundColor Red
    exit 1
}
. (Join-Path $proj 'scripts\common.ps1')

Write-Host 'Bachelor Chef - create demo accounts' -ForegroundColor Yellow
Import-DotEnv

Step 'Database'
if (-not (Start-LocalPostgres)) { Info 'No private database in .devtools - using SPRING_DATASOURCE_URL from .env' }
$url = Get-EnvValue 'SPRING_DATASOURCE_URL'
if ($url -notmatch '^jdbc:postgresql://([^:/?]+)(?::(\d+))?/([^?]+)') { Fail "Cannot read SPRING_DATASOURCE_URL in .env: $url" }
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '5432' }
$dbName = $Matches[3]
$dbUser = Get-EnvValue 'SPRING_DATASOURCE_USERNAME'
$env:PGPASSWORD = Get-EnvValue 'SPRING_DATASOURCE_PASSWORD'
$pgBin = Get-PgBin
$psql = if ($pgBin) { Join-Path $pgBin 'psql.exe' } else { (Get-Command psql -ErrorAction SilentlyContinue).Source }
if (-not $psql) { Fail 'psql was not found. Run install.bat first.' }
$psqlArgs = @('-h', $dbHost, '-p', $dbPort, '-U', $dbUser, '-d', $dbName, '-v', 'ON_ERROR_STOP=1', '-q')

$hasTable = (& $psql @psqlArgs -At -c "SELECT to_regclass('public.users') IS NOT NULL;" | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { Fail "Could not connect to the database $dbName on ${dbHost}:$dbPort." }
if ($hasTable -ne 't') { Fail 'The database tables do not exist yet. Start the app once with run.bat, then run admin.bat again.' }
Ok "Connected to $dbName on ${dbHost}:$dbPort"

Step 'Accounts'
# BCrypt hash of the password 123456 (cost 12), the same format the app itself stores.
$hash = '$2a$12$sR8ntV1nVtbU0VKMAXz/.u3uz6SeMxDSR9w22AdwNy5a1XOX70HY.'
$accounts = @(
    @('Inzamam', 'inzamam@admin.su', 'ADMIN'),
    @('Cherry', 'cherry@admin.su', 'ADMIN'),
    @('Saima', 'saima@admin.su', 'ADMIN')
) + (1..10 | ForEach-Object { , @("User $_", "user$_@user.com", 'USER') })
$values = ($accounts | ForEach-Object { "('{0}', '{1}', '{2}', '{3}', 'ACTIVE')" -f $_[0], $_[1], $hash, $_[2] }) -join ",`n"
$sql = @"
INSERT INTO users (full_name, email, password_hash, role, status) VALUES
$values
ON CONFLICT (email) DO UPDATE
   SET password_hash = EXCLUDED.password_hash, role = EXCLUDED.role, status = 'ACTIVE', updated_at = now();
SELECT email AS "Email", role AS "Role", status AS "Status"
  FROM users WHERE email LIKE '%@admin.su' OR email LIKE '%@user.com' ORDER BY role, user_id;
"@
$tmp = Join-Path $env:TEMP 'bachelor-chef-demo-accounts.sql'
[IO.File]::WriteAllText($tmp, $sql)
try {
    & $psql @psqlArgs -f $tmp
    if ($LASTEXITCODE -ne 0) { Fail 'Creating the accounts failed (see the message above).' }
} finally {
    Remove-Item $tmp -ErrorAction SilentlyContinue
}

Write-Host ''
Ok 'Done. Log in at http://localhost:3000 (start the app with run.bat if it is not running):'
Info 'Admins : inzamam@admin.su, cherry@admin.su, saima@admin.su'
Info 'Users  : user1@user.com ... user10@user.com'
Info 'Password for all: 123456   (local testing only)'
Info 'Chrome may show a "Change your password" warning after login - just close it.'
