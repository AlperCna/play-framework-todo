#Requires -Version 5.1
# Tek komutla sifirdan tam kurulum:
#   1. .env hazirla
#   2. Docker konteynerini baslat (DB otomatik olusur)
#   3. PostgreSQL hazir olana dek bekle
#   4. Migrasyonlari calistir
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$EnvFile     = Join-Path $ProjectRoot ".env"

Write-Host "=== Mona DRP --- Tam Kurulum ===" -ForegroundColor Cyan
Write-Host ""

# 1. .env kontrolu
if (-not (Test-Path $EnvFile)) {
    Write-Host "[1/4] .env bulunamadi, .env.example'dan kopyalaniyor..." -ForegroundColor Yellow
    Copy-Item (Join-Path $ProjectRoot ".env.example") $EnvFile
    Write-Host "      Olusturuldu: .env (varsayilan degerlerle)" -ForegroundColor Green
} else {
    Write-Host "[1/4] .env mevcut, kullaniliyor." -ForegroundColor Green
}

# .env yukle
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

# 2. Docker konteyneri baslat
Write-Host ""
Write-Host "[2/4] Docker konteyneri baslatiliyor..." -ForegroundColor Yellow
Push-Location $ProjectRoot
docker compose up -d
Pop-Location
Write-Host "      Konteyner baslatildi." -ForegroundColor Green

# 3. PostgreSQL hazir olana dek bekle (max 60 sn)
Write-Host ""
Write-Host "[3/4] PostgreSQL hazir olana dek bekleniyor..." -ForegroundColor Yellow
$maxWait = 60
$elapsed = 0
$ready   = $false

while ($elapsed -lt $maxWait) {
    try {
        $null = docker compose -f (Join-Path $ProjectRoot "docker-compose.yml") exec -T postgres `
                pg_isready -U $dbUser -d $dbName -q 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
    $elapsed += 2
    Write-Host "      Bekleniyor... (${elapsed}s)" -ForegroundColor Gray
}

if (-not $ready) {
    Write-Error "HATA: PostgreSQL $maxWait saniye icinde hazir olmadi."
    exit 1
}
Write-Host "      PostgreSQL hazir." -ForegroundColor Green

# 4. Migrasyonlari calistir
Write-Host ""
Write-Host "[4/4] Migrasyonlar calistiriliyor..." -ForegroundColor Yellow
& (Join-Path $PSScriptRoot "migrate_drp_up.ps1")

Write-Host ""
Write-Host "=== Kurulum tamamlandi ===" -ForegroundColor Green
Write-Host ""
Write-Host "Baglanti bilgileri:" -ForegroundColor Cyan
Write-Host "  Host     : $dbHost"
Write-Host "  Port     : $dbPort"
Write-Host "  DB       : $dbName"
Write-Host "  Kullanici: $dbUser"
Write-Host ""
Write-Host "Sema dogrulama icin:" -ForegroundColor Cyan
Write-Host "  Get-Content scripts\check_drp_schema.sql -Raw | docker compose exec -T postgres psql -U ${dbUser} -d ${dbName}"
Write-Host ""
Write-Host "Uygulamayi baslatmak icin:" -ForegroundColor Cyan
Write-Host "  sbt run"
