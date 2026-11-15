#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/_comum.sh"
modo "$@"
REPOSITORIO=https://github.com/Guilherme-Soares00/refugio-devops.git
DESTINO="$HOME/refugio-devops"
if [[ "$MODO" == --plano ]]; then
    printf 'PLANO, sem rede/execução: clonar ou atualizar ff-only %s em %s; preservar .env; preparar dados demo e publicar API; docker compose up -d --build --wait --wait-timeout 300; verificar health e GET público.\n' "$REPOSITORIO" "$DESTINO"
    printf '%s\n' 'Executar somente na VM após instalar Docker e publicar o repositório. Exige --executar, AUTORIZAR_ALTERACAO_LOCAL=SIM e CONFIRMAR_DEPLOY=DEPLOY-REFUGIO-NA-VM.'
    exit 0
fi
[[ "${AUTORIZAR_ALTERACAO_LOCAL:-}" == SIM && "${CONFIRMAR_DEPLOY:-}" == DEPLOY-REFUGIO-NA-VM ]] || falhar 'Deploy sem autorização local explícita completa.'
[[ "$(uname -s)" == Linux && "$EUID" != 0 ]] || falhar 'Execute como usuário não root, no grupo docker, somente na VM Linux.'
[[ -f /etc/os-release ]] || falhar 'Distribuição não identificada.'
source /etc/os-release
[[ "${ID:-}" == ubuntu && "${VERSION_ID:-}" == 24.04 ]] || falhar 'Deploy exige VM Ubuntu 24.04 LTS.'
for programa in git docker curl; do command -v "$programa" >/dev/null || falhar "$programa ausente; execute o instalador na VM primeiro."; done
docker info --format '{{.ServerVersion}}' >/dev/null
docker compose version >/dev/null
[[ ! -L "$DESTINO" ]] || falhar 'Destino não pode ser link simbólico.'
trap 'printf "ERRO: deploy interrompido; repositório ou contêineres podem estar parcialmente atualizados. .env não foi exibido. Nenhuma remoção automática.\n" >&2' ERR
if [[ -e "$DESTINO" ]]; then
    [[ -d "$DESTINO/.git" && ! -L "$DESTINO/.git" ]] || falhar 'Destino existente não é checkout Git esperado.'
    [[ "$(git -C "$DESTINO" rev-parse --show-toplevel)" == "$DESTINO" ]] || falhar 'Raiz Git inesperada.'
    [[ "$(git -C "$DESTINO" remote get-url origin)" == "$REPOSITORIO" ]] || falhar 'Origin diferente do repositório fixo.'
    [[ "$(git -C "$DESTINO" symbolic-ref --short HEAD)" == main ]] || falhar 'Checkout precisa estar na branch main.'
    [[ -z "$(git -C "$DESTINO" status --porcelain)" ]] || falhar 'Checkout com alterações locais; atualização recusada.'
    git -C "$DESTINO" fetch --no-tags origin main
    git -C "$DESTINO" merge --ff-only origin/main
else
    git clone --branch main --single-branch -- "$REPOSITORIO" "$DESTINO"
fi
cd -- "$DESTINO"
[[ -f scripts/preparar-ambiente.sh ]] || falhar 'Helper scripts/preparar-ambiente.sh ausente; repositório ainda não está pronto/publicado.'
[[ ! -L .env ]] || falhar '.env não pode ser link simbólico.'
if [[ -e .env ]]; then
    [[ -f .env && "$(stat -c '%a' .env)" == 600 ]] || falhar '.env existente precisa ser arquivo regular com permissão 600; conteúdo não será alterado.'
    ambiente_antes=$(sha256sum .env)
fi
umask 077
bash scripts/preparar-ambiente.sh --dados-demo --publicar-api
[[ -f .env && ! -L .env ]] || falhar 'Helper não produziu .env regular.'
if [[ -n "${ambiente_antes:-}" ]]; then
    [[ "$(sha256sum .env)" == "$ambiente_antes" ]] || falhar 'Helper alterou .env existente; interrompido antes do Compose.'
fi
[[ "$(stat -c '%a' .env)" == 600 ]] || falhar '.env precisa de permissão 600.'
export COMPOSE_PROJECT_NAME=refugio-devops APP_SEED_ENABLED=true API_BIND_ADDRESS=0.0.0.0 API_PORT=8080 POSTGRES_PORT=5432
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
curl --fail --silent --show-error --max-time 15 http://127.0.0.1:8080/actuator/health >/dev/null
curl --fail --silent --show-error --max-time 15 http://127.0.0.1:8080/api/pontos >/dev/null
printf '%s\n' 'Compose pronto; health e GET /api/pontos responderam HTTP de sucesso na VM. Acesso externo pelo IP deve ser verificado separadamente; autenticação administrativa somente por túnel SSH ou HTTPS.'
