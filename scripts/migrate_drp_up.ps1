#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot  = Split-Path $PSScriptRoot -Parent
$MigrationsDir = Join-Path $ProjectRoot "app\migrations\drp-postgres"
$ComposeFile  = Join-Path $ProjectRoot "docker-compose.yml"
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

# Her migration + o migration uygulandiysa var olacak "sentinel" nesne.
# Sentinel mevcutsa o adim atlanir -> script tekrar calistirilabilir (idempotent).
$migrations = @(
    @{ File = "V001__asset_layer_up.sql";     Sentinel = "public.entities" }
    @{ File = "V002__discovery_layer_up.sql"; Sentinel = "public.candidates" }
    @{ File = "V003__storage_layer_up.sql";   Sentinel = "public.blob_storage" }
    @{ File = "V004__pipeline_layer_up.sql";  Sentinel = "public.crawl_results" }
    @{ File = "V005__decision_layer_up.sql";  Sentinel = "public.cases" }
    @{ File = "V006__pgmq_queues_up.sql";     Sentinel = "pgmq.q_crawl_queue" }
)

# Verilen relation (schema.tablo) DB'de var mi? to_regclass yoksa NULL doner.
function Test-RelationExists($relation) {
    $out = (docker compose -f $ComposeFile exec -T -e PGPASSWORD=$env:DB_PASSWORD postgres `
            psql -U $dbUser -d $dbName -tAc "SELECT to_regclass('$relation') IS NOT NULL;") -join "`n"
    return ($out.Trim() -eq 't')
}

foreach ($m in $migrations) {
    $migration = $m.File
    Write-Host "-> $migration" -ForegroundColor Yellow
    if (Test-RelationExists $m.Sentinel) {
        Write-Host "   ZATEN UYGULANMIS, atlaniyor ($($m.Sentinel) mevcut)." -ForegroundColor DarkGray
        continue
    }
    $filePath = Join-Path $MigrationsDir $migration
    Get-Content $filePath -Raw | docker compose -f $ComposeFile exec -T `
        -e PGPASSWORD=$env:DB_PASSWORD postgres `
        psql -U $dbUser -d $dbName -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "HATA: $migration basarisiz oldu."
        exit 1
    }
    Write-Host "   OK" -ForegroundColor Green
}

Write-Host ""
Write-Host "Migration tamamlandi." -ForegroundColor Green
Write-Host "Kontrol icin: Get-Content scripts\check_drp_schema.sql -Raw | docker compose exec -T postgres psql -U $dbUser -d $dbName"
