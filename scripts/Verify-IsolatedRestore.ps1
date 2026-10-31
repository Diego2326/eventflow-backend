param(
    [Parameter(Mandatory=$true)][string]$Archive,
    [Parameter(Mandatory=$true)][string]$DatabaseUser,
    [string]$DatabaseName='eventflow_restore_check',
    [int]$DatabasePort=55432,
    [string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin'
)
$ErrorActionPreference='Stop'
if ($DatabaseName -ne 'eventflow_restore_check' -or $DatabasePort -eq 5432) {
    throw 'Use the dedicated local restore database on a separate isolated server port.'
}
$archivePath=(Resolve-Path -LiteralPath $Archive).Path
$hashFile="$archivePath.sha256"
if (-not (Test-Path -LiteralPath $hashFile)) { $hashFile=Join-Path (Split-Path $archivePath) 'SHA256.txt' }
$expectedHash=((Get-Content -LiteralPath $hashFile -Raw).Trim() -split '\s+')[0]
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Backup checksum mismatch' }
$previousSslMode=$env:PGSSLMODE
try {
    # This target is hard-coded loopback; credentials never go into arguments.
    $env:PGSSLMODE='prefer'
    $objects=& (Join-Path $PostgresBin 'psql.exe') -X -W -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser -d $DatabaseName -tAc "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND c.relkind IN ('r','p','v','m','S','f');"
    if ($LASTEXITCODE -ne 0 -or "$objects".Trim() -ne '0') { throw 'Target must be accessible and empty; nothing restored.' }
    # A new database already contains public. Omit only its CREATE SCHEMA entry;
    # preserve all application objects and rows. No DROP/--clean is used.
    $toc=& (Join-Path $PostgresBin 'pg_restore.exe') --list $archivePath
    if ($LASTEXITCODE -ne 0) { throw 'Archive TOC could not be read.' }
    $tocFile=Join-Path (Split-Path $archivePath) ("restore-toc-"+[guid]::NewGuid().ToString('N')+'.txt')
    $toc | Where-Object { $_ -notmatch '^\d+; \d+ \d+ SCHEMA - public .*$' } | Set-Content -LiteralPath $tocFile -Encoding UTF8
    & (Join-Path $PostgresBin 'pg_restore.exe') -W -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser -d $DatabaseName --exit-on-error --single-transaction --no-owner --no-privileges "--use-list=$tocFile" $archivePath
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed: rollback; do not approve migrations.' }
    & (Join-Path $PostgresBin 'psql.exe') -X -W -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser -d $DatabaseName -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot 'supabase-preflight.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Restored database preflight failed.' }
    Write-Output 'Restore completed. Compare all source/restored table counts, FK/constraints, sequence state, user fingerprints and Flyway checksums before approving recovery.'
} finally { $env:PGSSLMODE=$previousSslMode }
