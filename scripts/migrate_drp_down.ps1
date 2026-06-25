#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot   = Split-Path $PSScriptRoot -Parent
$MigrationsDir = Join-Path $ProjectRoot "app\migrations\drp-postgres"
$EnvFile       = Join-Path $ProjectRoot ".env"

# .env dosyasını yükle
if (-not (Test-Path $EnvFile)) {
    Write-Error "HATA: .env dosyası bulunamadı. Çözüm: cp .env.example .env"
    exit 1
}
foreach ($line in Get-Content $EnvFile) {
    if ($line -match '^\s*#' -or $line.Trim() -eq '') { continue }
    if ($line -match '^([^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), 'Process')
    }
}

$dbHost = $env:DB_HOST
$dbPort = $env:DB_PORT
$dbName = $env:DB_NAME
$dbUser = $env:DB_USER
$env:PGPASSWORD = $env:DB_PASSWORD

Write-Host "=== Mona DRP Migration DOWN ===" -ForegroundColor Cyan
Write-Host "Hedef: ${dbHost}:${dbPort}/${dbName}" -ForegroundColor Gray
Write-Host ""
Write-Host "UYARI: Bu islem tum DRP tablolarini ve queue'lari siler." -ForegroundColor Red
$confirm = Read-Host "Devam etmek istiyor musunuz? (evet yazin)"
if ($confirm -ne "evet") {
    Write-Host "Iptal edildi." -ForegroundColor Yellow
    exit 0
}
Write-Host ""

$migrations = @(
    "V006__pgmq_queues_down.sql"
    "V005__decision_layer_down.sql"
    "V004__pipeline_layer_down.sql"
    "V003__storage_layer_down.sql"
    "V002__discovery_layer_down.sql"
    "V001__asset_layer_down.sql"
)

foreach ($migration in $migrations) {
    Write-Host "-> $migration" -ForegroundColor Yellow
    $filePath = Join-Path $MigrationsDir $migration
    & psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f $filePath
    if ($LASTEXITCODE -ne 0) {
        Write-Error "HATA: $migration basarisiz oldu."
        exit 1
    }
    Write-Host "   OK" -ForegroundColor Green
}

Write-Host ""
Write-Host "Rollback tamamlandi. Tum DRP tablolari kaldirildi." -ForegroundColor Green
