#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/_comum.sh"
GRUPO_INFORMADO=
args=()
for argumento in "$@"; do
    case "$argumento" in
        --*) args+=("$argumento") ;;
        *) [[ -z "$GRUPO_INFORMADO" ]] || falhar 'Informe somente um nome de grupo.'; GRUPO_INFORMADO=$argumento ;;
    esac
done
modo "${args[@]}"
if [[ "$MODO" == --plano ]]; then
    printf 'PLANO, sem chamadas Azure: apagar INTEGRALMENTE o grupo %s, incluindo VM, disco/volume Postgres, NIC, rede e IP. Todos os dados serão perdidos.\n' "${GRUPO_INFORMADO:-$RESOURCE_GROUP}"
    printf '%s\n' 'Execução exige --executar <nome-exato-do-grupo>, AUTORIZAR_EXCLUSAO=SIM e CONFIRMAR_EXCLUSAO=EXCLUIR:<grupo>. Assinatura, prefixo e tag serão conferidos antes de excluir.'
    exit 0
fi
[[ -n "$GRUPO_INFORMADO" && "$GRUPO_INFORMADO" == "$RESOURCE_GROUP" ]] || falhar 'Nome explícito precisa ser igual a RESOURCE_GROUP.'
[[ "${AUTORIZAR_EXCLUSAO:-}" == SIM && "${CONFIRMAR_EXCLUSAO:-}" == "EXCLUIR:$RESOURCE_GROUP" ]] || falhar 'Exclusão sem autorização explícita completa.'
validar_azure
command -v az >/dev/null || falhar 'Azure CLI não encontrada.'
validar_assinatura
validar_grupo
printf 'Escopo autorizado: exclusão irreversível de TODO o grupo %s, assinatura %s, tag projeto=refugio-devops.\n' "$RESOURCE_GROUP" "$AZURE_SUBSCRIPTION_ID"
azure resource list --resource-group "$RESOURCE_GROUP" --query '[].{nome:name,tipo:type}' --output table
AZURE_ALTERACAO_INICIADA=1
trap aviso_erro ERR
azure group delete --name "$RESOURCE_GROUP" --yes --output none
[[ "$(azure group exists --name "$RESOURCE_GROUP" --output tsv)" == false ]] || falhar 'Ausência do grupo não confirmada; exclusão não pode ser declarada concluída.'
[[ "$(azure resource list --query "[?resourceGroup=='$RESOURCE_GROUP'] | length(@)" --output tsv)" == 0 ]] || falhar 'Consulta de recursos restantes não confirmou lista vazia; verifique o grupo.'
printf 'Exclusão verificada: grupo %s ausente e consulta sem recursos restantes.\n' "$RESOURCE_GROUP"
