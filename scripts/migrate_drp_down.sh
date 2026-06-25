#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATIONS_DIR="$PROJECT_ROOT/app/migrations/drp-postgres"

# .env dosyasını yükle
ENV_FILE="$PROJECT_ROOT/.env"
if [ ! -f "$ENV_FILE" ]; then
  echo "HATA: .env dosyası bulunamadı."
  echo "Çözüm: cp .env.example .env"
  exit 1
fi
set -o allexport
source "$ENV_FILE"
set +o allexport

DB_URL="postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

echo "=== Mona DRP Migration DOWN ==="
echo "Hedef: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo ""
echo "UYARI: Bu işlem tüm DRP tablolarını ve queue'ları siler."
read -r -p "Devam etmek istiyor musunuz? (evet yazın): " confirm
if [ "$confirm" != "evet" ]; then
  echo "İptal edildi."
  exit 0
fi
echo ""

MIGRATIONS=(
  "V006__pgmq_queues_down.sql"
  "V005__decision_layer_down.sql"
  "V004__pipeline_layer_down.sql"
  "V003__storage_layer_down.sql"
  "V002__discovery_layer_down.sql"
  "V001__asset_layer_down.sql"
)

for migration in "${MIGRATIONS[@]}"; do
  echo "→ $migration"
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f "$MIGRATIONS_DIR/$migration"
  echo "  ✓ OK"
done

echo ""
echo "Rollback tamamlandı. Tüm DRP tabloları kaldırıldı."
