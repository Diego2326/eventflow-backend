param(
    [Parameter(Mandatory=$true)][string]$DatabaseHost,
    [Parameter(Mandatory=$true)][string]$DatabaseUser,
    [Parameter(Mandatory=$true)][string]$DatabaseName,
    [int]$DatabasePort = 5432,
    [switch]$FullDatabase,
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin'
)
$ErrorActionPreference = 'Stop'
# Passwords are requested by pg_dump locally; never accepted as script arguments.
if ($DatabaseHost -match '[@/\s]' -or $DatabaseUser -match '[@/\s]' -or $DatabaseName -match '[@/\s]') {
    throw 'Supply separate host, user and database names, never a connection URI.'
}
$backupRoot = Join-Path $PSScriptRoot '..\backups'
$backupDirectory = Join-Path $backupRoot ([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))
New-Item -ItemType Directory -Path $backupDirectory -ErrorAction Stop | Out-Null
$archiveName = if ($FullDatabase) { 'eventflow-database.dump' } else { 'eventflow-public.dump' }
$archive = Join-Path $backupDirectory $archiveName
# FullDatabase includes all database schemas accessible to the dump role. Failures
# are fatal; never silently exclude managed tables to claim a complete backup.
$previousSslMode = $env:PGSSLMODE
try {
    $env:PGSSLMODE = 'require'
    $dumpArgs = @('-h',$DatabaseHost,'-p',"$DatabasePort",'-U',$DatabaseUser,'-d',$DatabaseName,'-W','--format=custom',"--file=$archive")
    if (-not $FullDatabase) { $dumpArgs += '--schema=public' }
    & (Join-Path $PostgresBin 'pg_dump.exe') @dumpArgs
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed; the partial archive is not a confirmed backup.' }
    if ((Get-Item -LiteralPath $archive).Length -eq 0) { throw 'Empty backup archive.' }
    $toc = & (Join-Path $PostgresBin 'pg_restore.exe') --list $archive
    if ($LASTEXITCODE -ne 0) { throw 'Archive table-of-contents validation failed.' }
    $toc | Set-Content -LiteralPath (Join-Path $backupDirectory 'archive-toc.txt') -Encoding UTF8
    foreach ($table in @('country','phone_prefix','users','flyway_schema_history')) {
        if (-not ($toc -match "TABLE DATA public $table ")) { throw "Missing expected table data entry: $table" }
    }
    # Read/decompress all archive contents, discarding SQL without executing or logging it.
    & (Join-Path $PostgresBin 'pg_restore.exe') --file=NUL $archive
    if ($LASTEXITCODE -ne 0) { throw 'Full archive decoding failed.' }
    $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    "$hash  $archiveName" | Set-Content -LiteralPath (Join-Path $backupDirectory 'SHA256.txt') -Encoding ASCII
    Write-Output "Backup created: $archive"
    Write-Output 'Integrity and expected table entries checked. Restore to an isolated database is still required before calling recovery verified.'
} finally {
    $env:PGSSLMODE = $previousSslMode
}
