#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot  = Split-Path $PSScriptRoot -Parent
$MigrationsDir = Join-Path $ProjectRoot "app\migrations\drp-postgres"
$EnvFile      = Join-Path $ProjectRoot ".env"

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

Write-Host "=== Mona DRP Migration UP ===" -ForegroundColor Cyan
Write-Host "Hedef: ${dbHost}:${dbPort}/${dbName}" -ForegroundColor Gray
Write-Host ""

$migrations = @(
    "V001__asset_layer_up.sql"
    "V002__discovery_layer_up.sql"
    "V003__storage_layer_up.sql"
    "V004__pipeline_layer_up.sql"
    "V005__decision_layer_up.sql"
    "V006__pgmq_queues_up.sql"
)

# psql yerel kurulu mu?
$psqlLocal = $null -ne (Get-Command psql -ErrorAction SilentlyContinue)

foreach ($migration in $migrations) {
    Write-Host "-> $migration" -ForegroundColor Yellow
    $filePath = Join-Path $MigrationsDir $migration

    if ($psqlLocal) {
        & psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f $filePath
    } else {
        # Yerel psql yoksa Docker container icindeki psql kullan
        Get-Content $filePath -Raw | docker compose exec -T postgres `
            psql -U $dbUser -d $dbName -v ON_ERROR_STOP=1
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Error "HATA: $migration basarisiz oldu."
        exit 1
    }
    Write-Host "   OK" -ForegroundColor Green
}

Write-Host ""
Write-Host "Migration tamamlandi." -ForegroundColor Green
if ($psqlLocal) {
    Write-Host "Kontrol icin: psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -f scripts\check_drp_schema.sql"
} else {
    Write-Host "Kontrol icin: docker compose exec postgres psql -U $dbUser -d $dbName -f /dev/stdin < scripts\check_drp_schema.sql"
}
