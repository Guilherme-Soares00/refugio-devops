#!/usr/bin/env bash
set -Eeuo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
SANDBOX=$(mktemp -d "${TMPDIR:-/tmp}/refugio-azure-tests.XXXXXX")
trap 'rm -rf -- "$SANDBOX"' EXIT
export AZURE_TEST_LOG="$SANDBOX/chamadas"
export AZURE_SUBSCRIPTION_ID=11111111-1111-1111-1111-111111111111
export RESOURCE_GROUP=refugio-teste VM_NAME=refugio-vm LOCATION=brazilsouth VM_SIZE=Standard_B2s
export AZURE_IMAGE=Canonical:ubuntu-24_04-lts:server:latest AZURE_DISK_SKU=StandardSSD_LRS ADMIN_USER=refugioadmin
export ADMIN_CIDR=192.0.2.10/32
export SSH_PUBLIC_KEY="$SANDBOX/chave.pub"
printf '%s\n' 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFicticioSomenteTeste chave-de-teste' > "$SSH_PUBLIC_KEY"
unset CONFIRMAR_CRIACAO CONFIRMAR_DOCKER CONFIRMAR_DEPLOY CONFIRMAR_INICIO CONFIRMAR_PARADA CONFIRMAR_EXCLUSAO AUTORIZAR_CUSTOS AUTORIZAR_ALTERACAO_LOCAL AUTORIZAR_ALTERACAO_AZURE AUTORIZAR_EXCLUSAO || true
az() { printf 'az %s\n' "$*" >> "$AZURE_TEST_LOG"; return 97; }
ssh() { printf 'ssh\n' >> "$AZURE_TEST_LOG"; return 97; }
sudo() { printf 'sudo\n' >> "$AZURE_TEST_LOG"; return 97; }
docker() { printf 'docker\n' >> "$AZURE_TEST_LOG"; return 97; }
curl() { printf 'curl\n' >> "$AZURE_TEST_LOG"; return 97; }
git() { printf 'git\n' >> "$AZURE_TEST_LOG"; return 97; }
apt-get() { printf 'apt-get\n' >> "$AZURE_TEST_LOG"; return 97; }
systemctl() { printf 'systemctl\n' >> "$AZURE_TEST_LOG"; return 97; }
usermod() { printf 'usermod\n' >> "$AZURE_TEST_LOG"; return 97; }
ssh-keygen() { printf 'ssh-keygen\n' >> "$AZURE_TEST_LOG"; return 97; }
export -f az ssh sudo docker curl git apt-get systemctl usermod ssh-keygen
passed=0
ok() { passed=$((passed + 1)); printf 'OK %s\n' "$1"; }
sem_chamadas() { [[ ! -s "$AZURE_TEST_LOG" ]] || { printf 'FALHA: comando externo chamado\n' >&2; exit 1; }; }
for script in 01-provisionar 02-instalar-docker 03-deploy parar-iniciar limpar; do
    [[ -f "$ROOT/azure/$script.sh" ]] || { printf 'FALHA: script ausente: %s\n' "$script" >&2; exit 1; }
    bash "$ROOT/azure/$script.sh" > "$SANDBOX/saida" 2>&1
    sem_chamadas
    ok "$script: plano padrão não chama comandos externos"
    bash "$ROOT/azure/$script.sh" --plano > "$SANDBOX/saida" 2>&1
    sem_chamadas
    ok "$script: --plano não chama comandos externos"
    if bash "$ROOT/azure/$script.sh" --executar > "$SANDBOX/saida" 2>&1; then
        printf 'FALHA: execução sem autorização aceita: %s\n' "$script" >&2; exit 1
    fi
    sem_chamadas
    ok "$script: execução sem autorização recusada antes de comandos externos"
done
for script in 01-provisionar 02-instalar-docker 03-deploy parar-iniciar limpar; do
    env AUTORIZAR_CUSTOS=SIM CONFIRMAR_CRIACAO=CRIAR:refugio-teste AUTORIZAR_ALTERACAO_LOCAL=SIM CONFIRMAR_DOCKER=INSTALAR-DOCKER-NA-VM CONFIRMAR_DEPLOY=DEPLOY-REFUGIO-NA-VM AUTORIZAR_ALTERACAO_AZURE=SIM CONFIRMAR_PARADA=PARAR:refugio-teste:refugio-vm AUTORIZAR_EXCLUSAO=SIM CONFIRMAR_EXCLUSAO=EXCLUIR:refugio-teste bash "$ROOT/azure/$script.sh" --plano > "$SANDBOX/saida" 2>&1
    sem_chamadas
    ok "$script: mesmo com autorizações --plano não executa"
done
bash "$ROOT/azure/parar-iniciar.sh" iniciar --plano > "$SANDBOX/saida" 2>&1
sem_chamadas
ok 'iniciar --plano não chama az'
recusar_sem_comandos() {
    local nome=$1 trecho=$2
    shift 2
    : > "$AZURE_TEST_LOG"
    if "$@" > "$SANDBOX/saida" 2>&1; then printf 'FALHA: %s foi aceito\n' "$nome" >&2; exit 1; fi
    local saida
    saida=$(< "$SANDBOX/saida")
    [[ "$saida" == *"$trecho"* ]] || { printf 'FALHA: motivo inesperado em %s: %s\n' "$nome" "$saida" >&2; exit 1; }
    sem_chamadas
    ok "$nome"
}
recusar_sem_comandos 'criação: só flag de custos não autoriza' 'autorização' env AUTORIZAR_CUSTOS=SIM bash "$ROOT/azure/01-provisionar.sh" --executar
recusar_sem_comandos 'criação: só confirmação não autoriza' 'autorização' env CONFIRMAR_CRIACAO=CRIAR:refugio-teste bash "$ROOT/azure/01-provisionar.sh" --executar
recusar_sem_comandos 'criação: confirmação de outro grupo recusada' 'autorização' env AUTORIZAR_CUSTOS=SIM CONFIRMAR_CRIACAO=CRIAR:refugio-outro bash "$ROOT/azure/01-provisionar.sh" --executar
recusar_sem_comandos 'instalação: só flag não autoriza' 'autorização' env AUTORIZAR_ALTERACAO_LOCAL=SIM bash "$ROOT/azure/02-instalar-docker.sh" --executar
recusar_sem_comandos 'instalação: só confirmação não autoriza' 'autorização' env CONFIRMAR_DOCKER=INSTALAR-DOCKER-NA-VM bash "$ROOT/azure/02-instalar-docker.sh" --executar
recusar_sem_comandos 'deploy: só flag não autoriza' 'autorização' env AUTORIZAR_ALTERACAO_LOCAL=SIM bash "$ROOT/azure/03-deploy.sh" --executar
recusar_sem_comandos 'deploy: só confirmação não autoriza' 'autorização' env CONFIRMAR_DEPLOY=DEPLOY-REFUGIO-NA-VM bash "$ROOT/azure/03-deploy.sh" --executar
recusar_sem_comandos 'iniciar: só custos não autoriza' 'autorização' env AUTORIZAR_CUSTOS=SIM bash "$ROOT/azure/parar-iniciar.sh" iniciar --executar
recusar_sem_comandos 'iniciar: só confirmação não autoriza' 'autorização' env CONFIRMAR_INICIO=INICIAR:refugio-teste:refugio-vm bash "$ROOT/azure/parar-iniciar.sh" iniciar --executar
recusar_sem_comandos 'parar: só flag não autoriza' 'autorização' env AUTORIZAR_ALTERACAO_AZURE=SIM bash "$ROOT/azure/parar-iniciar.sh" parar --executar
recusar_sem_comandos 'parar: só confirmação não autoriza' 'autorização' env CONFIRMAR_PARADA=PARAR:refugio-teste:refugio-vm bash "$ROOT/azure/parar-iniciar.sh" parar --executar
recusar_sem_comandos 'exclusão: só flag não autoriza' 'autorização' env AUTORIZAR_EXCLUSAO=SIM bash "$ROOT/azure/limpar.sh" --executar refugio-teste
recusar_sem_comandos 'exclusão: só confirmação não autoriza' 'autorização' env CONFIRMAR_EXCLUSAO=EXCLUIR:refugio-teste bash "$ROOT/azure/limpar.sh" --executar refugio-teste
recusar_sem_comandos 'exclusão: nome explícito obrigatório' 'Nome explícito' env AUTORIZAR_EXCLUSAO=SIM CONFIRMAR_EXCLUSAO=EXCLUIR:refugio-teste bash "$ROOT/azure/limpar.sh" --executar
export AUTORIZAR_CUSTOS=SIM CONFIRMAR_CRIACAO=CRIAR:refugio-teste
recusar_sem_comandos 'UUID inválido recusado antes do az' 'UUID' env AZURE_SUBSCRIPTION_ID=123 bash "$ROOT/azure/01-provisionar.sh" --executar
recusar_sem_comandos 'grupo sem prefixo recusado antes do az' 'começar com refugio-' env RESOURCE_GROUP=outro CONFIRMAR_CRIACAO=CRIAR:outro bash "$ROOT/azure/01-provisionar.sh" --executar
for cidr in 0.0.0.0/0 192.0.2.0/24 256.1.1.1/32 019.0.0.1/32; do
    recusar_sem_comandos "CIDR inválido recusado: $cidr" '' env ADMIN_CIDR="$cidr" bash "$ROOT/azure/01-provisionar.sh" --executar
done
recusar_sem_comandos 'chave inexistente recusada, sem gerar chave' '.pub existente' env SSH_PUBLIC_KEY="$SANDBOX/ausente.pub" bash "$ROOT/azure/01-provisionar.sh" --executar
for script in 01-provisionar 02-instalar-docker 03-deploy parar-iniciar limpar; do
    recusar_sem_comandos "$script: modos contraditórios recusados" 'somente --plano ou --executar' bash "$ROOT/azure/$script.sh" --plano --executar
done
az() {
    printf 'az %s\n' "$*" >> "$AZURE_TEST_LOG"
    local consulta= anterior= argumento
    for argumento in "$@"; do
        [[ "$anterior" != --query ]] || consulta=$argumento
        anterior=$argumento
    done
    case "$1 $2 ${3:-}" in
        'account show '*)
            if [[ "$consulta" == id ]]; then printf '%s\n' "$AZURE_SUBSCRIPTION_ID"; else printf 'Enabled\n'; fi ;;
        'group exists '*)
            if [[ -e "$AZURE_TEST_LOG.deleted" ]]; then printf 'false\n';
            elif [[ "$AZURE_TEST_SCENARIO" == criar || "$AZURE_TEST_SCENARIO" == parcial || "$AZURE_TEST_SCENARIO" == imagem-arm ]]; then printf 'false\n';
            else printf 'true\n'; fi ;;
        'group show '*) if [[ "$AZURE_TEST_SCENARIO" == tag-estranha ]]; then printf 'outro\n'; else printf 'refugio-devops\n'; fi ;;
        'provider show '*) printf 'Registered\n' ;;
        'vm image show') if [[ "$AZURE_TEST_SCENARIO" == imagem-arm ]]; then printf 'Arm64\n'; else printf 'x64\n'; fi ;;
        'vm list-skus '*) if [[ "$consulta" == *CpuArchitectureType* ]]; then printf 'x64\n'; else printf '0\n'; fi ;;
        'vm list-usage '*) return 0 ;;
        'group create '*) : > "$AZURE_TEST_LOG.created" ;;
        'network nic create') [[ "$AZURE_TEST_SCENARIO" != parcial ]] || return 41 ;;
        'network nsg create'|'network nsg rule'|'network vnet create'|'network public-ip create'|'vm create '*) return 0 ;;
        'vm show '*) if [[ "$consulta" == tags.projeto ]]; then printf 'refugio-devops\n'; else printf 'Delete\n'; fi ;;
        'network public-ip show') printf '192.0.2.42\n' ;;
        'vm get-instance-view '*)
            case "$AZURE_TEST_SCENARIO" in iniciar) printf 'PowerState/running\n' ;; parar) printf 'PowerState/deallocated\n' ;; *) printf 'VM running\n' ;; esac ;;
        'vm start '*|'vm deallocate '*) return 0 ;;
        'resource list '*)
            if [[ -e "$AZURE_TEST_LOG.deleted" && " $* " == *' --resource-group '* ]]; then
                printf 'ResourceGroupNotFound\n' >&2
                return 3
            fi
            if [[ "$consulta" == 'length(@)' || "$consulta" == *'| length(@)' ]]; then printf '0\n'; fi ;;
        'group delete '*) if [[ "$AZURE_TEST_SCENARIO" != exclusao-incompleta ]]; then : > "$AZURE_TEST_LOG.deleted"; fi ;;
        *) printf 'Comando az inesperado no double: %s\n' "$*" >&2; return 96 ;;
    esac
}
export -f az
simular() {
    export AZURE_TEST_SCENARIO=$1
    shift
    : > "$AZURE_TEST_LOG"
    rm -f -- "$AZURE_TEST_LOG.deleted" "$AZURE_TEST_LOG.created"
    "$@" > "$SANDBOX/saida" 2>&1
}
contem() { local texto; texto=$(< "$AZURE_TEST_LOG"); [[ "$texto" == *"$1"* ]] || { printf 'FALHA: chamada esperada não encontrada: %s\n' "$1" >&2; exit 1; }; }
nao_contem() { local texto; texto=$(< "$AZURE_TEST_LOG"); [[ "$texto" != *"$1"* ]] || { printf 'FALHA: chamada proibida encontrada: %s\n' "$1" >&2; exit 1; }; }
simular criar bash "$ROOT/azure/01-provisionar.sh" --executar
contem 'group create'
contem '--source-address-prefixes 192.0.2.10/32'
contem '--destination-port-ranges 22'
contem '--destination-port-ranges 8080'
contem '--sku Standard --allocation-method Static'
contem '--os-disk-delete-option Delete --nic-delete-option Delete'
nao_contem '5432'
nao_contem 'account set'
nao_contem 'provider register'
nao_contem 'ssh-keygen'
while IFS= read -r linha; do [[ "$linha" == *"--subscription $AZURE_SUBSCRIPTION_ID --only-show-errors"* ]] || { printf 'FALHA: comando sem escopo explícito\n' >&2; exit 1; }; done < "$AZURE_TEST_LOG"
ok 'provisionamento simulado: escopo, NSG, IP, chave existente e Delete options'
for caso in existente imagem-arm; do
    if simular "$caso" bash "$ROOT/azure/01-provisionar.sh" --executar; then printf 'FALHA: pré-condição aceita: %s\n' "$caso" >&2; exit 1; fi
    nao_contem 'group create'
    ok "pré-consultas impedem criação: $caso"
done
if simular parcial bash "$ROOT/azure/01-provisionar.sh" --executar; then printf 'FALHA: erro de provisionamento ignorado\n' >&2; exit 1; fi
contem 'group create'
nao_contem 'group delete'
saida=$(< "$SANDBOX/saida")
[[ "$saida" == *'Recursos parciais'* ]] || { printf 'FALHA: aviso de recursos parciais ausente\n' >&2; exit 1; }
ok 'falha parcial avisa cobrança e não limpa automaticamente'
for acao in iniciar parar; do
    simular "$acao" env AUTORIZAR_CUSTOS=SIM CONFIRMAR_INICIO=INICIAR:refugio-teste:refugio-vm AUTORIZAR_ALTERACAO_AZURE=SIM CONFIRMAR_PARADA=PARAR:refugio-teste:refugio-vm bash "$ROOT/azure/parar-iniciar.sh" "$acao" --executar
    if [[ "$acao" == iniciar ]]; then contem 'vm start'; else contem 'vm deallocate'; fi
    contem 'vm get-instance-view'
    ok "$acao simulado e estado relido"
done
if simular tag-estranha env AUTORIZAR_EXCLUSAO=SIM CONFIRMAR_EXCLUSAO=EXCLUIR:refugio-teste bash "$ROOT/azure/limpar.sh" --executar refugio-teste; then printf 'FALHA: exclusão de grupo com tag estranha aceita\n' >&2; exit 1; fi
nao_contem 'group delete'
ok 'tag estranha impede exclusão'
simular excluir env AUTORIZAR_EXCLUSAO=SIM CONFIRMAR_EXCLUSAO=EXCLUIR:refugio-teste bash "$ROOT/azure/limpar.sh" --executar refugio-teste
contem 'group delete'
contem "--query [?resourceGroup=='refugio-teste'] | length(@)"
[[ -e "$AZURE_TEST_LOG.deleted" ]] || exit 1
ok 'exclusão simulada espera e verifica ausência e lista vazia'
if simular exclusao-incompleta env AUTORIZAR_EXCLUSAO=SIM CONFIRMAR_EXCLUSAO=EXCLUIR:refugio-teste bash "$ROOT/azure/limpar.sh" --executar refugio-teste; then printf 'FALHA: exclusão sem ausência confirmada aceita\n' >&2; exit 1; fi
ok 'exclusão não declara sucesso se grupo ainda existe'
printf 'RESULTADO: %s verificações passaram; nenhuma operação externa real.\n' "$passed"
