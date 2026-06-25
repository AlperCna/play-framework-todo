#!/usr/bin/env bash
# Tek komutla sıfırdan tam kurulum:
#   1. .env hazırla
#   2. Docker konteyneri başlat (DB otomatik oluşur)
#   3. PostgreSQL hazır olana dek bekle
#   4. Migrationları çalıştır
#   5. Şemayı doğrula
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"

echo "=== Mona DRP — Tam Kurulum ==="
echo ""

# 1. .env kontrolü
if [ ! -f "$ENV_FILE" ]; then
  echo "[1/4] .env bulunamadı, .env.example'dan kopyalanıyor..."
  cp "$PROJECT_ROOT/.env.example" "$ENV_FILE"
  echo "      Oluşturuldu: .env (varsayılan değerlerle)"
else
  echo "[1/4] .env mevcut, kullanılıyor."
fi

set -o allexport
source "$ENV_FILE"
set +o allexport

# 2. Docker konteyneri başlat
echo ""
echo "[2/4] Docker konteyneri başlatılıyor..."
cd "$PROJECT_ROOT"
docker compose up -d
echo "      Konteyner başlatıldı."

# 3. PostgreSQL hazır olana dek bekle (max 60 sn)
echo ""
echo "[3/4] PostgreSQL hazır olana dek bekleniyor..."
MAX_WAIT=60
ELAPSED=0
until docker compose exec -T postgres pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do
  if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo "HATA: PostgreSQL $MAX_WAIT saniye içinde hazır olmadı."
    docker compose logs postgres
    exit 1
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
  echo "      Bekleniyor... (${ELAPSED}s)"
done
echo "      PostgreSQL hazır."

# 4. Migrationları çalıştır
echo ""
echo "[4/4] Migrationlar çalıştırılıyor..."
bash "$SCRIPT_DIR/migrate_drp_up.sh"

echo ""
echo "=== Kurulum tamamlandi ==="
echo ""
echo "Baglanti bilgileri:"
echo "  Host    : $DB_HOST"
echo "  Port    : $DB_PORT"
echo "  DB      : $DB_NAME"
echo "  Kullanici: $DB_USER"
echo ""
echo "Sema dogrulama:"
echo "  bash scripts/check_drp_schema.sql"
echo ""
echo "Uygulamayi baslatmak icin:"
echo "  sbt run"
