#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/_comum.sh"
modo "$@"
if [[ "$MODO" == --plano ]]; then
    printf 'PLANO, sem chamadas Azure: criar grupo novo %s, VM %s, região %s, tamanho %s, imagem %s, disco %s.\n' "$RESOURCE_GROUP" "$VM_NAME" "$LOCATION" "$VM_SIZE" "$AZURE_IMAGE" "$AZURE_DISK_SKU"
    printf '%s\n' 'IP Standard estático; SSH 22 somente ADMIN_CIDR IPv4/32; HTTP 8080 público; nenhuma regra 5432. Chave SSH pública existente, sem geração.' 'Custo: estimativa pendente. Validar preços, crédito, política, quota e capacidade antes de autorizar.' 'Execução exige --executar, AUTORIZAR_CUSTOS=SIM e CONFIRMAR_CRIACAO=CRIAR:<grupo>, além dos parâmetros documentados.'
    exit 0
fi
[[ "${AUTORIZAR_CUSTOS:-}" == SIM && "${CONFIRMAR_CRIACAO:-}" == "CRIAR:$RESOURCE_GROUP" ]] || falhar 'Criação sem autorização explícita completa.'
validar_azure
[[ "$LOCATION" =~ ^[a-z0-9]+$ && "$VM_SIZE" =~ ^Standard_[A-Za-z0-9_]+$ ]] || falhar 'Região ou tamanho inválido.'
[[ "$AZURE_IMAGE" == Canonical:ubuntu-24_04-lts:server:* ]] || falhar 'Use Ubuntu 24.04 LTS amd64 da Canonical.'
[[ "$AZURE_DISK_SKU" == StandardSSD_LRS || "$AZURE_DISK_SKU" == Standard_LRS ]] || falhar 'SKU de disco não permitido.'
[[ "$ADMIN_USER" =~ ^[a-z_][a-z0-9_-]{0,30}$ && "$ADMIN_USER" != root ]] || falhar 'ADMIN_USER inválido.'
[[ "${ADMIN_CIDR:-}" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})/32$ ]] || falhar 'ADMIN_CIDR precisa ser IPv4/32.'
for octeto in "${BASH_REMATCH[@]:1}"; do
    [[ "$octeto" == 0 || "$octeto" != 0* ]] || falhar 'IPv4 não aceita octetos com zero inicial.'
    (( 10#$octeto <= 255 )) || falhar 'Octeto IPv4 fora de 0..255.'
done
[[ "$ADMIN_CIDR" != 0.0.0.0/32 && "$ADMIN_CIDR" != 255.255.255.255/32 ]] || falhar 'Informe o endereço público administrativo válido.'
[[ -n "${SSH_PUBLIC_KEY:-}" && -f "$SSH_PUBLIC_KEY" && ! -L "$SSH_PUBLIC_KEY" && "$SSH_PUBLIC_KEY" == *.pub ]] || falhar 'SSH_PUBLIC_KEY deve apontar para arquivo .pub existente, não um link.'
IFS=' ' read -r tipo material restante < "$SSH_PUBLIC_KEY" || [[ -n "${tipo:-}" && -n "${material:-}" ]] || falhar 'Arquivo de chave pública vazio ou ilegível.'
[[ "$tipo" == ssh-ed25519 || "$tipo" == ssh-rsa || "$tipo" == ecdsa-sha2-nistp256 || "$tipo" == ecdsa-sha2-nistp384 || "$tipo" == ecdsa-sha2-nistp521 ]] || falhar 'Formato de chave pública SSH não reconhecido.'
[[ "$material" =~ ^[A-Za-z0-9+/]+={0,3}$ ]] || falhar 'Material de chave pública inválido.'
command -v az >/dev/null || falhar 'Azure CLI não encontrada; instale e autentique separadamente, com autorização.'
validar_assinatura
existe=$(azure group exists --name "$RESOURCE_GROUP" --output tsv)
[[ "$existe" == false ]] || falhar 'Grupo já existe ou consulta inconclusiva; criação recusada.'
for provider in Microsoft.Compute Microsoft.Network; do
    [[ "$(azure provider show --namespace "$provider" --query registrationState --output tsv)" == Registered ]] || falhar "Provider $provider não registrado; script não registra providers."
done
[[ "$(azure vm image show --location "$LOCATION" --urn "$AZURE_IMAGE" --query architecture --output tsv)" == x64 ]] || falhar 'Imagem não confirmou arquitetura x64.'
[[ "$(azure vm list-skus --location "$LOCATION" --size "$VM_SIZE" --resource-type virtualMachines --all --query "[?name=='$VM_SIZE'].capabilities[] | [?name=='CpuArchitectureType'].value | [0]" --output tsv)" == x64 ]] || falhar 'SKU não confirmou arquitetura x64.'
[[ "$(azure vm list-skus --location "$LOCATION" --size "$VM_SIZE" --resource-type virtualMachines --all --query "[?name=='$VM_SIZE'].restrictions | [0] | length(@)" --output tsv)" == 0 ]] || falhar 'SKU ausente, restrito ou consulta inconclusiva.'
azure vm list-usage --location "$LOCATION" --output table
printf '%s\n' 'Consultas prévias concluídas; não garantem capacidade de alocação nem ausência de cobrança.'
AZURE_ALTERACAO_INICIADA=1
trap aviso_erro ERR
azure group create --name "$RESOURCE_GROUP" --location "$LOCATION" --tags projeto=refugio-devops --output none
azure network nsg create --resource-group "$RESOURCE_GROUP" --name refugio-nsg --location "$LOCATION" --tags projeto=refugio-devops --output none
azure network nsg rule create --resource-group "$RESOURCE_GROUP" --nsg-name refugio-nsg --name permitir-ssh-admin --priority 100 --direction Inbound --access Allow --protocol Tcp --source-address-prefixes "$ADMIN_CIDR" --source-port-ranges '*' --destination-address-prefixes '*' --destination-port-ranges 22 --output none
azure network nsg rule create --resource-group "$RESOURCE_GROUP" --nsg-name refugio-nsg --name permitir-api --priority 110 --direction Inbound --access Allow --protocol Tcp --source-address-prefixes Internet --source-port-ranges '*' --destination-address-prefixes '*' --destination-port-ranges 8080 --output none
azure network vnet create --resource-group "$RESOURCE_GROUP" --name refugio-vnet --location "$LOCATION" --address-prefixes 10.20.0.0/16 --subnet-name refugio-subnet --subnet-prefixes 10.20.0.0/24 --tags projeto=refugio-devops --output none
azure network public-ip create --resource-group "$RESOURCE_GROUP" --name refugio-ip --location "$LOCATION" --sku Standard --allocation-method Static --version IPv4 --tags projeto=refugio-devops --output none
azure network nic create --resource-group "$RESOURCE_GROUP" --name refugio-nic --location "$LOCATION" --vnet-name refugio-vnet --subnet refugio-subnet --network-security-group refugio-nsg --public-ip-address refugio-ip --tags projeto=refugio-devops --output none
azure vm create --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --location "$LOCATION" --size "$VM_SIZE" --image "$AZURE_IMAGE" --admin-username "$ADMIN_USER" --authentication-type ssh --ssh-key-values "$SSH_PUBLIC_KEY" --nics refugio-nic --storage-sku "$AZURE_DISK_SKU" --os-disk-delete-option Delete --nic-delete-option Delete --tags projeto=refugio-devops --output none
[[ "$(azure group show --name "$RESOURCE_GROUP" --query tags.projeto --output tsv)" == refugio-devops ]] || falhar 'Tag do grupo não foi confirmada.'
[[ "$(azure vm show --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --query storageProfile.osDisk.deleteOption --output tsv)" == Delete ]] || falhar 'Delete option do disco não confirmada.'
[[ "$(azure vm show --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --query 'networkProfile.networkInterfaces[0].deleteOption' --output tsv)" == Delete ]] || falhar 'Delete option da NIC não confirmada.'
azure vm get-instance-view --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --query 'instanceView.statuses[].displayStatus' --output table
azure network public-ip show --resource-group "$RESOURCE_GROUP" --name refugio-ip --query ipAddress --output tsv
printf '%s\n' 'VM criada. Docker ainda precisa ser instalado explicitamente na VM. Não houve SSH nem instalação automática. IP/disco podem continuar cobrados com VM desalocada.'
