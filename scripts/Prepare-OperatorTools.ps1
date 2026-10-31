$ErrorActionPreference='Stop'
# Build package first: .\gradlew.bat bootJar --console=plain
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifact=Join-Path $PSScriptRoot '..\build\libs\eventflow-api-0.0.1-SNAPSHOT.jar'
$library=Join-Path $PSScriptRoot '..\build\operator-lib'
New-Item -ItemType Directory -Force -Path $library | Out-Null
$zip=[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $artifact).Path)
try {
    foreach($entry in $zip.Entries) {
        if($entry.FullName -like 'BOOT-INF/lib/*.jar') {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $library $entry.Name),$true)
        }
    }
} finally { $zip.Dispose() }
Write-Output 'Operator dependencies prepared locally. No database connection opened.'
