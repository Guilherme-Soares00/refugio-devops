#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/_comum.sh"
modo "$@"
if [[ "$MODO" == --plano ]]; then
    printf '%s\n' 'PLANO, sem instalar: somente dentro da VM Ubuntu 24.04 LTS amd64, configurar repositório Docker oficial com signed-by, instalar Engine, Buildx, Compose plugin, curl e git; habilitar Docker no boot e adicionar DOCKER_USER ao grupo docker.' 'Execução requer root, --executar, AUTORIZAR_ALTERACAO_LOCAL=SIM, CONFIRMAR_DOCKER=INSTALAR-DOCKER-NA-VM e DOCKER_USER explícito. Grupo docker equivale a acesso root.'
    exit 0
fi
[[ "${AUTORIZAR_ALTERACAO_LOCAL:-}" == SIM && "${CONFIRMAR_DOCKER:-}" == INSTALAR-DOCKER-NA-VM ]] || falhar 'Instalação sem autorização local explícita completa.'
[[ "${DOCKER_USER:-}" =~ ^[a-z_][a-z0-9_-]{0,30}$ && "$DOCKER_USER" != root ]] || falhar 'Informe DOCKER_USER existente e não root.'
[[ "$(uname -s)" == Linux && "$EUID" == 0 ]] || falhar 'Execute como root somente na VM Linux Ubuntu.'
[[ -f /etc/os-release ]] || falhar 'Distribuição não identificada.'
source /etc/os-release
[[ "${ID:-}" == ubuntu && "${VERSION_ID:-}" == 24.04 && "${VERSION_CODENAME:-}" == noble ]] || falhar 'Instalador exige Ubuntu 24.04 LTS.'
[[ "$(dpkg --print-architecture)" == amd64 ]] || falhar 'Arquitetura precisa ser amd64.'
id "$DOCKER_USER" >/dev/null 2>&1 || falhar 'DOCKER_USER não existe.'
trap 'printf "ERRO: instalação interrompida; alterações locais podem ser parciais. Nenhuma reversão automática.\n" >&2' ERR
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git openssl
install -m 0755 -d /etc/apt/keyrings
curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 https://download.docker.com/linux/ubuntu/gpg --output /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
printf '%s\n' 'deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable' > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
usermod -aG docker "$DOCKER_USER"
systemctl is-enabled --quiet docker
systemctl is-active --quiet docker
[[ " $(id -nG "$DOCKER_USER") " == *' docker '* ]] || falhar 'Inclusão no grupo docker não confirmada.'
docker --version
docker compose version
printf '%s\n' 'Docker instalado e ativo na VM. Encerre a sessão SSH e conecte novamente antes do deploy sem sudo. Nenhum projeto foi iniciado.'
