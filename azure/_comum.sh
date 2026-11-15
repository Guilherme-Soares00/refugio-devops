#!/usr/bin/env bash
set -Eeuo pipefail
export MSYS_NO_PATHCONV=1
AZURE_SUBSCRIPTION_ID=${AZURE_SUBSCRIPTION_ID:-}
RESOURCE_GROUP=${RESOURCE_GROUP:-refugio-devops}
VM_NAME=${VM_NAME:-refugio-vm}
LOCATION=${LOCATION:-brazilsouth}
VM_SIZE=${VM_SIZE:-Standard_B2s}
AZURE_IMAGE=${AZURE_IMAGE:-Canonical:ubuntu-24_04-lts:server:latest}
AZURE_DISK_SKU=${AZURE_DISK_SKU:-StandardSSD_LRS}
ADMIN_USER=${ADMIN_USER:-refugioadmin}
AZURE_ALTERACAO_INICIADA=0
falhar() {
    printf 'ERRO: %s\n' "$*" >&2
    if [[ "$AZURE_ALTERACAO_INICIADA" == 1 ]]; then
        printf '%s\n' 'Recursos parciais podem continuar cobrados. Não houve limpeza automática; confira o grupo antes de repetir.' >&2
    fi
    exit 1
}
modo() {
    [[ $# -le 1 ]] || falhar 'Use somente --plano ou --executar.'
    MODO=${1:---plano}
    [[ "$MODO" == --plano || "$MODO" == --executar ]] || falhar 'Modo inválido: use --plano ou --executar.'
}
validar_azure() {
    [[ "$AZURE_SUBSCRIPTION_ID" =~ ^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$ ]] || falhar 'AZURE_SUBSCRIPTION_ID deve ser UUID, sem espaços.'
    [[ "$RESOURCE_GROUP" =~ ^refugio-[a-z0-9][a-z0-9-]{0,65}$ ]] || falhar 'Grupo precisa começar com refugio- e usar letras minúsculas, números ou hífen.'
    [[ "$VM_NAME" =~ ^refugio-[a-z0-9][a-z0-9-]{0,48}$ ]] || falhar 'Nome da VM inválido.'
}
azure() { az "$@" --subscription "$AZURE_SUBSCRIPTION_ID" --only-show-errors; }
validar_assinatura() {
    local id estado
    id=$(azure account show --query id --output tsv)
    [[ "${id,,}" == "${AZURE_SUBSCRIPTION_ID,,}" ]] || falhar 'Assinatura retornada não corresponde à informada.'
    estado=$(azure account show --query state --output tsv)
    [[ "$estado" == Enabled ]] || falhar 'Assinatura não está habilitada.'
}
validar_grupo() {
    [[ "$(azure group exists --name "$RESOURCE_GROUP" --output tsv)" == true ]] || falhar 'Grupo não existe.'
    [[ "$(azure group show --name "$RESOURCE_GROUP" --query tags.projeto --output tsv)" == refugio-devops ]] || falhar 'Tag projeto do grupo não confere.'
}
aviso_erro() {
    local status=$?
    printf 'ERRO: operação interrompida (%s). Recursos parciais podem continuar cobrados. Não houve limpeza automática; revise o grupo e use limpar.sh somente com autorização específica.\n' "$status" >&2
    exit "$status"
}
