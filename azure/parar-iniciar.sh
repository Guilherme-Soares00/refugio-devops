#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/_comum.sh"
ACAO=parar
args=()
for argumento in "$@"; do
    case "$argumento" in
        parar|iniciar) [[ "$ACAO" == parar && ${acao_informada:-0} == 0 ]] || falhar 'Informe somente uma ação.'; ACAO=$argumento; acao_informada=1 ;;
        *) args+=("$argumento") ;;
    esac
done
modo "${args[@]}"
if [[ "$MODO" == --plano ]]; then
    printf 'PLANO, sem chamadas Azure: ação %s na VM %s do grupo %s.\n' "$ACAO" "$VM_NAME" "$RESOURCE_GROUP"
    printf '%s\n' 'parar usa deallocate: computação é desalocada, mas disco e IP continuam potencialmente cobrados; dados do volume persistem no disco.' 'iniciar volta a gerar cobrança de computação. Custo: estimativa pendente.' 'Execução requer autorização distinta por ação, assinatura UUID, grupo e tags conferidos.'
    exit 0
fi
if [[ "$ACAO" == iniciar ]]; then
    [[ "${AUTORIZAR_CUSTOS:-}" == SIM && "${CONFIRMAR_INICIO:-}" == "INICIAR:$RESOURCE_GROUP:$VM_NAME" ]] || falhar 'Início sem autorização explícita de custos completa.'
else
    [[ "${AUTORIZAR_ALTERACAO_AZURE:-}" == SIM && "${CONFIRMAR_PARADA:-}" == "PARAR:$RESOURCE_GROUP:$VM_NAME" ]] || falhar 'Desalocação sem autorização explícita completa.'
fi
validar_azure
command -v az >/dev/null || falhar 'Azure CLI não encontrada.'
validar_assinatura
validar_grupo
[[ "$(azure vm show --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --query tags.projeto --output tsv)" == refugio-devops ]] || falhar 'Tag projeto da VM não confere.'
AZURE_ALTERACAO_INICIADA=1
trap aviso_erro ERR
if [[ "$ACAO" == iniciar ]]; then
    azure vm start --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --output none
    esperado=PowerState/running
else
    azure vm deallocate --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --output none
    esperado=PowerState/deallocated
fi
estado=$(azure vm get-instance-view --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --query "instanceView.statuses[?starts_with(code, 'PowerState/')].code | [0]" --output tsv)
[[ "$estado" == "$esperado" ]] || falhar 'Estado final não confirmado; consulte a VM antes de repetir.'
printf 'Estado verificado: %s. Disco/IP não foram removidos; podem continuar cobrados.\n' "$estado"
