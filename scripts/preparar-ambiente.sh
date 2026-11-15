#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SEED=false
BIND=127.0.0.1
for arg in "$@"; do
  case "$arg" in
    --dados-demo) SEED=true ;;
    --publicar-api) BIND=0.0.0.0 ;;
    *) printf 'Opcao invalida: %s\n' "$arg" >&2; exit 2 ;;
  esac
done
if [[ -e "$ROOT/.env" ]]; then
  printf '.env ja existe; nenhuma credencial foi alterada.\n'
  exit 0
fi
command -v openssl >/dev/null || { printf 'Instale OpenSSL antes de continuar.\n' >&2; exit 1; }
umask 077
PASSWORD="$(openssl rand -hex 24)"
JWT="$(openssl rand -base64 48 | tr -d '\r\n')"
set -o noclobber
{
  printf 'POSTGRES_DB=refugio\nPOSTGRES_USER=refugio_app\nPOSTGRES_PASSWORD=%s\n' "$PASSWORD"
  printf 'POSTGRES_PORT=5432\nSPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/refugio\n'
  printf 'SPRING_JPA_HIBERNATE_DDL_AUTO=update\nJWT_SECRET=%s\n' "$JWT"
  printf 'JWT_EXPIRATION_SECONDS=3600\nCORS_ALLOWED_ORIGINS=http://localhost:3000\n'
  printf 'APP_SEED_ENABLED=%s\nAPI_BIND_ADDRESS=%s\nAPI_PORT=8080\n' "$SEED" "$BIND"
  printf 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError\n'
} > "$ROOT/.env"
printf '.env criado com segredos aleatorios; valores nao serao exibidos.\n'
printf 'Dados demonstrativos: %s. Interface da API: %s.\n' "$SEED" "$BIND"
