<a id="inicio"></a>

# Refúgio — DevOps e Cloud

API CitizenTech de pontos de alívio térmico, executada com Java 25 e PostgreSQL 16 em dois containers integrados. Repositório independente da entrega Java Advanced, com o código-fonte necessário para reconstruir a aplicação.

> **Dados demonstrativos. Confirme local e horários antes de usar.** Pontos, usuários e leituras da carga inicial são fictícios.

## Navegação

- [Como executar](#como-executar)
- [Azure](#azure)
- [Arquitetura](#arquitetura)
- [Evidências](#evidencias)

## Equipe

| Integrante | RM | Turma |
| --- | --- | --- |
| Guilherme Soares | 568227 | A preencher |
| Demais integrantes | A preencher | A preencher |

## Estado da implantação

A validação local não comprova implantação Azure. A execução em nuvem depende de autorização de custos e de publicação dos commits deste repositório. Consulte as evidências para distinguir o que foi executado do que permanece pendente.

## Organização

```text
Dockerfile                  build Maven e runtime Java 25 não-root
docker-compose.yml          API, PostgreSQL, rede e volume nomeados
.env.example                configuração sem segredos
src/                        fonte e testes da API Spring Boot
scripts/                    preparação local e testes HTTP
azure/                      provisionamento, instalação, deploy e ciclo de vida
docs/devops/                diagramas e evidências da execução
```

## Origem da aplicação

O código da API foi copiado do projeto [Refúgio Java](https://github.com/Guilherme-Soares00/refugio), revisão `d492a82f82a96bf56e432aa9f85408ba2ede0943`. Esta entrega possui histórico Git próprio e não altera aquele repositório. O domínio e a segurança foram preservados: Ponto, Recurso, Medição, Ocorrência e Usuário; JWT com papéis CIDADAO/GESTOR.

Não há integração automática com os bancos Oracle das demais entregas, CI/CD, Kubernetes, ACR, proxy reverso ou HTTPS. São limites do escopo atual, não componentes implantados.

<a id="como-executar"></a>

## Como executar

### 1. Escopo e configuração do ambiente

Dois containers: `refugio-api` (Spring Boot/JRE 25, usuário Linux 10001, porta 8080) e `refugio-db` (`postgres:16`, porta 5432). Comunicação pela rede `refugio-net`; dados no volume `refugio-pgdata`. O Oracle não faz parte deste Compose.

Pré-requisitos locais: Docker Desktop em modo Linux ou Docker Engine, Compose moderno com `up --wait`, Git, Bash e OpenSSL. Git Bash inclui Bash/OpenSSL no Windows. Node.js 22+ é opcional para os testes HTTP automatizados. Java 25 é necessário **somente para compilar/testar fora do Docker**; o Maven Wrapper já está incluído. Não use Java 21 para o JAR compilado em Java 25.

No Windows, inicie o Docker Desktop e aguarde o motor Linux ficar disponível. `docker version` deve mostrar Client **e Server**. Os blocos abaixo indicam onde executar; não misture comandos locais com um terminal SSH remoto.

#### Execução rápida — Git Bash local, WSL ou Linux

```bash
git clone https://github.com/Guilherme-Soares00/refugio-devops.git
cd refugio-devops
bash scripts/preparar-ambiente.sh --dados-demo
docker compose up -d --build --wait --wait-timeout 240
docker compose ps
curl -fsS http://localhost:8080/actuator/health
```

Esperado: dois serviços `healthy` e `{"status":"UP"}`. A publicação deste código no GitHub é um pré-requisito para clonar em outra máquina; antes do primeiro push, execute na cópia local já preparada.

O preparador gera senha aleatória e chave JWT Base64 com 48 bytes aleatórios. Não mostra os valores e não sobrescreve `.env` existente. `--dados-demo` habilita os usuários e dados fictícios necessários à demonstração. Sem essa opção, o seed fica desabilitado. A API local é publicada em `127.0.0.1` por padrão. O script recusa opções desconhecidas.

**Não grave `.env`, tokens ou comandos que imprimam credenciais. Não execute `docker compose config` sem `--quiet` durante a gravação:** a saída expandida contém segredos.

#### PowerShell local

Na raiz já clonada, use os mesmos comandos Docker. Para preparar o ambiente, abra Git Bash nessa pasta e execute o preparador. Não tente executar um `.sh` diretamente no PowerShell sem um Bash instalado.

#### Interfaces

- Swagger: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI: <http://localhost:8080/v3/api-docs>
- Catálogo: <http://localhost:8080/api/pontos>
- Saúde: <http://localhost:8080/actuator/health>

Saúde disponível não comprova término do seed: aguarde o login demonstrativo funcionar ou confira as contagens no banco. As credenciais abaixo são públicas, fictícias e exclusivas de demonstração.

| Papel | E-mail | Senha demonstrativa |
| --- | --- | --- |
| GESTOR | gestor09@example.test | GestorDemo2026! |
| CIDADAO | cidadao01@example.test | CidadaoDemo2026! |

O cadastro público sempre cria CIDADAO. GESTOR pode cadastrar/alterar/excluir pontos e recursos; CIDADAO não pode. O catálogo é público. Não habilite esse seed em um serviço real.

### 2. Variáveis e segredos

`.env.example` é apenas um modelo. As linhas vazias de senha/chave precisam ser preenchidas pelo preparador ou por um mecanismo seguro. `.env` é ignorado pelo Git e pelo Docker. Não use uma chave JWT real em argumentos ou capturas.

| Variável de configuração | Padrão/finalidade |
| --- | --- |
| POSTGRES_DB | `refugio`; banco inicial |
| POSTGRES_USER | `refugio_app`; usuário de inicialização |
| POSTGRES_PASSWORD | obrigatória; gerada com 24 bytes aleatórios em hexadecimal |
| POSTGRES_PORT | `5432`; publicação apenas em loopback do host |
| SPRING_DATASOURCE_URL | `jdbc:postgresql://db:5432/refugio`; `db` é o serviço, não localhost |
| SPRING_DATASOURCE_USERNAME | recebida pela API a partir de POSTGRES_USER no Compose |
| SPRING_DATASOURCE_PASSWORD | recebida pela API a partir de POSTGRES_PASSWORD no Compose |
| SPRING_JPA_HIBERNATE_DDL_AUTO | `update`; criação/ajuste acadêmico das tabelas, não migração de produção |
| JWT_SECRET | Base64 com no mínimo 32 bytes decodificados; preparador gera 48 |
| JWT_EXPIRATION_SECONDS | `3600` segundos |
| CORS_ALLOWED_ORIGINS | `http://localhost:3000`; ajuste à origem real do cliente web |
| APP_SEED_ENABLED | `false`; `--dados-demo` gera `true` |
| API_BIND_ADDRESS | `127.0.0.1` local; `--publicar-api` gera `0.0.0.0` para a VM |
| API_PORT | `8080` no host |
| JAVA_TOOL_OPTIONS | `-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError` |
| SERVER_PORT | `8080` dentro da API; porta interna fixa neste Compose |

POSTGRES_USER/PASSWORD são a fonte única do Compose para evitar divergência entre banco e aplicação. As variáveis SPRING_DATASOURCE_USERNAME/PASSWORD existem no ambiente da API, sem duplicar segredos no `.env`. Se mudar POSTGRES_DB, ajuste também a URL JDBC explicitamente configurada.

Compose lê `.env` para interpolação; o Java iniciado fora dele não lê esse arquivo automaticamente. `env_file` não é necessário porque cada variável enviada ao container está explicitamente declarada em `environment`. Isso evita mandar os segredos do banco para serviços que não precisam deles.

**Senha de volume existente:** mudar POSTGRES_PASSWORD no `.env` não muda o usuário já inicializado no PostgreSQL. Não apague o volume para contornar isso. Faça uma alteração administrativa de senha, sincronize a configuração e recrie apenas a API.

### 3. Dockerfile explicado

| Instrução | Motivo |
| --- | --- |
| FROM 25-jdk-jammy AS build | compila com o mesmo Java do projeto |
| WORKDIR /build | define a pasta de trabalho da compilação |
| COPY .mvn, mvnw, pom.xml, src/main | inclui wrapper, dependências e código de produção |
| RUN --mount=type=cache ... package | reutiliza downloads Maven sem levar cache ao runtime |
| -Dmaven.test.skip=true | build de imagem não executa testes destrutivos de banco; execute-os separadamente |
| FROM 25-jre-jammy | imagem final recebe JRE, não Maven/JDK de compilação |
| WORKDIR /app | diretório previsível para execução e evidência |
| apt-get ... curl | instala explicitamente a ferramenta usada pelo healthcheck |
| groupadd / useradd | cria usuário/grupo Linux 10001 |
| ENV | padrões de porta e ajuste de memória, sem segredos |
| COPY --from=build --chown | copia somente o JAR com proprietário não-root |
| USER 10001:10001 | executa a JVM sem privilégios root |
| EXPOSE 8080 | documenta a porta da imagem; não a publica sozinho |
| ENTRYPOINT em array | processo Java recebe os sinais de parada diretamente |

O runtime remove apenas listas temporárias do apt da própria imagem. A `.dockerignore` exclui Git, segredos, testes e materiais não usados no build. A imagem é a receita empacotada; o container é a instância em execução.

### 4. CRUD e códigos HTTP

#### Teste automatizado — máquina local

```bash
node scripts/testar-api.mjs http://localhost:8080 crud
```

Executa login, catálogo, OpenAPI, Actuator, 401/403/400/404/409 e criar/ler/alterar/excluir Ponto e Recurso. Usa `REF-989` exclusivamente para esse teste e o remove no final. Não execute se já houver um registro importante com esse código. Se houver falha intermediária, os registros podem permanecer; confira pelo catálogo e limpe somente os dados de teste pela API.

#### Teste manual — Git Bash local

Mantenha o token em memória. Atribuições não o imprimem; não ative `set -x`.

```bash
BASE=http://localhost:8080
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' --data '{"email":"gestor09@example.test","senha":"GestorDemo2026!"}' | node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(JSON.parse(d).accessToken))')
PONTO=$(curl -fsS -X POST "$BASE/api/pontos" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" --data '{"codigoPublico":"REF-988","nome":"Ponto manual DevOps","logradouro":"Rua Demonstrativa","numero":"100","bairro":"Bairro DevOps","cidade":"Cidade Demonstrativa","uf":"SP","horarioTexto":"08:00-18:00","status":"ATIVO"}')
PONTO_ID=$(printf '%s' "$PONTO" | node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(String(JSON.parse(d).id)))')
curl -fsS "$BASE/api/pontos/$PONTO_ID"
RECURSO=$(curl -fsS -X POST "$BASE/api/recursos" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" --data "{\"pontoId\":$PONTO_ID,\"tipo\":\"AGUA\",\"disponivel\":true}")
RECURSO_ID=$(printf '%s' "$RECURSO" | node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(String(JSON.parse(d).id)))')
curl -fsS "$BASE/api/pontos/$PONTO_ID/recursos"
curl -sS -w '%{http_code}\n' -X PUT "$BASE/api/pontos/$PONTO_ID" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" --data '{"nome":"Ponto manual atualizado","logradouro":"Rua Demonstrativa","numero":"100","bairro":"Bairro DevOps","cidade":"Cidade Demonstrativa","uf":"SP","horarioTexto":"09:00-17:00","status":"ATIVO"}'
curl -sS -w '%{http_code}\n' -X PUT "$BASE/api/recursos/$RECURSO_ID" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" --data "{\"pontoId\":$PONTO_ID,\"tipo\":\"AGUA\",\"disponivel\":false}"
curl -fsS "$BASE/api/pontos/$PONTO_ID/recursos"
curl -sS -w '%{http_code}\n' -X DELETE "$BASE/api/recursos/$RECURSO_ID" -H "Authorization: Bearer $TOKEN"
curl -sS -w '%{http_code}\n' -X DELETE "$BASE/api/pontos/$PONTO_ID" -H "Authorization: Bearer $TOKEN"
unset TOKEN
```

POST retorna 201; GET 200; PUT/DELETE 204. O código público do ponto é imutável. Para remover um ponto com recursos, exclua primeiro os recursos criados para esse teste; pontos com histórico de medição/ocorrência não devem ser apagados, e sim inativados. Erros seguem JSON com status, erro, mensagem, path e timestamp.

### 5. SELECT, usuários, logs e acesso — ambos os containers

Execute na raiz do projeto, localmente ou por SSH na VM:

```bash
docker compose ps
docker compose logs --tail 60 api
docker compose logs --tail 60 db
docker compose exec -T api sh -c 'whoami; id; pwd; ls -la /app'
docker compose exec -T api sh -c 'printf "SERVER_PORT=%s\n" "$SERVER_PORT"; printf "SPRING_DATASOURCE_URL=%s\n" "$SPRING_DATASOURCE_URL"'
docker compose exec -T db sh -c 'id postgres; pwd; ls -ld /var/lib/postgresql/data'
docker compose exec -T db sh -c 'printf "POSTGRES_DB=%s\nPOSTGRES_USER=%s\n" "$POSTGRES_DB" "$POSTGRES_USER"'
docker compose top db
docker compose exec -T db psql -U refugio_app -d refugio -v ON_ERROR_STOP=1 -c "SELECT p.codigo_publico,p.nome,r.tipo,r.disponivel FROM ponto_refugio p JOIN recurso_ponto r ON r.ponto_id=p.id ORDER BY p.codigo_publico,r.tipo;"
docker volume inspect refugio-pgdata --format '{{.Name}} {{.Driver}}'
docker network inspect refugio-net --format '{{.Name}} {{.Driver}}'
```

A API roda como `refugio`, UID/GID 10001, em `/app`. Seu filesystem não contém dados permanentes do domínio; os logs vão para stdout/stderr e são acessíveis com `logs api`.

**Banco:** `docker compose exec db whoami` pode retornar `root` porque um `exec` herda o usuário configurado na imagem. Isso não prova que o servidor PostgreSQL roda como root. O entrypoint inicializa permissões e entrega o processo ao usuário `postgres`. A evidência correta é `docker compose top db` (UID 999 na imagem testada) junto com `id postgres`; também é possível usar `docker compose exec -T --user postgres db id`. O UID pode mudar se a variante da imagem mudar: confira, não decore.

`POSTGRES_USER=refugio_app` é uma conta **do banco**, diferente do usuário Linux postgres; na imagem oficial a conta de inicialização tem privilégios elevados. Esta entrega demonstra integração, não mínimo privilégio SQL de produção. Logs do banco são `logs db`, diretório de dados `/var/lib/postgresql/data`, volume nomeado `refugio-pgdata`.

Nunca use `printenv` sem selecionar nomes não secretos. Não grave a saída completa de `docker inspect`, que pode incluir Environment com senhas.

### 6. Persistência e idempotência

**Atenção:** os comandos abaixo interrompem e recriam os containers deste Compose. Não removem volumes. Feche qualquer operação de escrita antes de executar.

```bash
node scripts/testar-api.mjs http://localhost:8080 preparar-persistencia
docker compose exec -T db psql -U refugio_app -d refugio -c "SELECT p.id,p.codigo_publico,r.id AS recurso_id,r.tipo FROM ponto_refugio p JOIN recurso_ponto r ON r.ponto_id=p.id WHERE p.codigo_publico='REF-990';"
docker compose down
docker compose up -d --wait --wait-timeout 240
node scripts/testar-api.mjs http://localhost:8080 verificar-persistencia
docker compose exec -T db psql -U refugio_app -d refugio -c "SELECT p.id,p.codigo_publico,r.id AS recurso_id,r.tipo FROM ponto_refugio p JOIN recurso_ponto r ON r.ponto_id=p.id WHERE p.codigo_publico='REF-990';"
```

O preparo usa REF-990 e grava apenas IDs não secretos em `target/devops/persistencia.json` (não versionado). Prepare uma vez; se já existir, use diretamente a verificação, sem recriar o registro. O teste conserva esse ponto/recurso para a próxima demonstração. IDs não são necessariamente sequenciais: testes e conflitos consomem sequências.

O seed usa e-mail, código público, par ponto/tipo e chaves de identificação das leituras/ocorrências para não duplicar cargas em reinícios sequenciais. Não sobrescreve alterações. Recriar um registro demonstrativo removido é comportamento da carga inicial; use códigos fora de REF-001…010 para demonstrar exclusão. Não execute múltiplas APIs com seed em paralelo neste ambiente.

**Nunca executar `down -v` para provar persistência:** a opção remove o volume e apaga os dados. `docker system prune` também não faz parte do roteiro.

### 7. Testes Java — banco descartável separado

A suíte usa `ddl-auto` destrutivo. Não aponte para `refugio` nem para banco compartilhado. A proteção exige nome exato `refugio_test`.

```bash
docker run --name refugio-devops-test --rm -d -p 127.0.0.1:55432:5432 -e POSTGRES_DB=refugio_test -e POSTGRES_USER=refugio_test -e POSTGRES_PASSWORD=refugio_test_only postgres:16
docker exec refugio-devops-test pg_isready -U refugio_test -d refugio_test
```

Espere `accepting connections`. No **PowerShell local** com JDK 25:

```powershell
.\mvnw.cmd -B verify
```

Ou Linux/WSL com JDK 25: `bash mvnw -B verify`. Relatórios reais: `target/surefire-reports`. Depois, `docker stop refugio-devops-test` remove esse container descartável e seu banco de testes; não afeta o volume principal. Se já existir, confira seu estado em vez de repetir `docker run`.

Validação adicional de configuração, sem criar recursos Azure:

```bash
node --test tests/compose.test.mjs tests/environment.test.mjs tests/api-safety.test.mjs
bash tests/test-azure.sh
```

### 8. Azure — autorização antes de custos

**Não execute provisionamento sem aprovação explícita.** Os scripts possuem modo plano por padrão. Criar VM, discos e IP público pode consumir créditos; ligar uma VM também. Não se presume gratuidade do Azure for Students. Região, SKU, quotas e preço continuam pendentes de conferência na assinatura autorizada.

O passo a passo de login, parâmetros, provisionamento, transferência do instalador, deploy e ciclo de vida está em **[seção Azure](#azure)**. Esse guia usa Azure CLI, sem cloud-init ocultando a instalação Docker. Não há CI/CD.

```bash
bash azure/01-provisionar.sh --plano
bash azure/02-instalar-docker.sh --plano
bash azure/03-deploy.sh --plano
bash azure/parar-iniciar.sh --plano
bash azure/limpar.sh --plano
```

A VM deve usar IP público **estático**, SSH restrito ao IPv4 administrativo /32 e 8080 para demonstração. Não abrir 5432 no NSG. A API só fica pública quando `API_BIND_ADDRESS=0.0.0.0` na VM; a instalação local permanece loopback.

#### Testes a partir do computador, depois da implantação autorizada

Leitura pública, **Git Bash local**, substituindo o valor do IP retornado pelo Azure:

```bash
VM_IP=IP_PUBLICO_DA_VM
curl -fsS "http://$VM_IP:8080/actuator/health"
curl -fsS "http://$VM_IP:8080/api/pontos"
```

Abra também `http://IP_PUBLICO_DA_VM:8080/swagger-ui/index.html`. Não execute esses comandos apenas dentro da VM e chame isso de acesso externo.

Como não há HTTPS, **login e requisições com token devem usar túnel SSH**, não HTTP aberto. Deixe o túnel abaixo em um segundo terminal local. A chave deve existir fora do repositório; compare a impressão digital do host com a obtida pelo canal autenticado do Azure antes de aceitar a primeira conexão.

```bash
ssh -i "$HOME/.ssh/CHAVE_DO_PROJETO" -N -L 127.0.0.1:18080:127.0.0.1:8080 USUARIO_DA_VM@IP_PUBLICO_DA_VM
```

Em outro terminal local:

```bash
node scripts/testar-api.mjs http://localhost:18080 crud
```

A aplicação ainda roda na VM Azure; somente o transporte administrativo é protegido por SSH. O HTTP público expõe apenas o catálogo usado na demonstração. O seed tem credenciais públicas: isso **não é uma implantação segura para dados reais**, mesmo com o túnel administrativo.

#### Persistência e reinício da VM — pendentes

Pelo computador local e com o túnel SSH ativo, prepare/verifique o registro com `node scripts/testar-api.mjs http://localhost:18080 preparar-persistencia` e depois `verificar-persistencia`, usando a mesma URL nas duas fases. Na sessão SSH remota, execute `docker compose down` sem `-v`, `docker compose up -d --wait --wait-timeout 240` e o SELECT. O instalador da VM não instala Node: os testes Node ficam no computador, não no terminal remoto. Para provar reboot, após autorização execute o reinício da VM, aguarde seu retorno, reconecte por SSH, reabra o túnel e verifique `systemctl is-enabled docker`, `docker compose ps`, endpoint público e SELECT do REF-990. `restart: unless-stopped` só reinicia automaticamente containers não deixados deliberadamente parados; depois de `compose down` será necessário `compose up` novamente.

Antes de reiniciar, deixe os containers em execução (`compose up -d`), não em estado deliberadamente parado. Após autorização, execute **no terminal local autenticado no Azure**:

```bash
az vm restart --subscription "$AZURE_SUBSCRIPTION_ID" --resource-group "$RESOURCE_GROUP" --name "$VM_NAME"
az vm get-instance-view --subscription "$AZURE_SUBSCRIPTION_ID" --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --query 'instanceView.statuses[].displayStatus' --output table
```

Depois do retorno, reabra a sessão **SSH remota**, entre em `$HOME/refugio-devops` e confira:

```bash
systemctl is-enabled docker
docker compose ps
docker compose exec -T db psql -U refugio_app -d refugio -c "SELECT p.codigo_publico,r.tipo FROM ponto_refugio p JOIN recurso_ponto r ON r.ponto_id=p.id WHERE p.codigo_publico='REF-990';"
```

Reabra o túnel e confira também o endpoint público a partir do computador. Esses comandos estão documentados, mas ainda não foram executados em uma VM deste projeto.

**Lembrete de custo:** planeje manter a VM disponível para a demonstração de 23–27/11. Entre sessões, deallocate pode reduzir cobrança de computação, mas disco e IP podem continuar cobrados. Antes da aula, inicie com antecedência e teste o endpoint. Não exclua o grupo até concluir demonstrações e salvar os dados necessários. Exclusão do grupo é destrutiva e exige confirmação própria.

### 9. Problemas frequentes

| Sintoma | Conferência e menor correção |
| --- | --- |
| Erro no pipe dockerDesktopLinuxEngine | abrir Docker Desktop, aguardar motor Linux; conferir Client/Server |
| Porta 8080 ou 5432 ocupada | identificar o serviço; ajustar API_PORT/POSTGRES_PORT no `.env`, sem parar serviços alheios |
| Container com nome já existente | conferir `docker ps -a` e projeto proprietário; não apagar containers desconhecidos |
| API não conecta ao banco | URL dentro do Compose usa `db:5432`, credenciais correspondem ao volume; conferir logs |
| WeakKeyException/chave JWT inválida | gerar Base64 de 32+ bytes aleatórios; não usar texto curto nem trocar a chave sem considerar tokens existentes |
| Login demo retorna 401 | conferir seed habilitado e término da carga; não imprimir hashes/senhas do banco |
| healthcheck falha | conferir logs; `curl` é instalado no runtime; validar porta interna 8080 e prazo de inicialização |
| Azure falha por região/SKU/quota ou HTTP público inacessível | consultar política/quotas/SKUs antes de nova tentativa; para acesso, conferir VM ligada, bind, NSG e porta; não migrar para SKU mais cara automaticamente |

### 10. Limitações e pendências

Sem HTTPS, domínio, backup automatizado, alta disponibilidade, migrações versionadas ou monitoramento externo. Imagens por tag podem receber atualizações; registre versão efetiva no momento da demonstração. Recursos gratuitos/quota/capacidade não estão garantidos. A imagem da API não executa os testes de banco durante seu build. Dados são fictícios; a etiqueta de temperatura é didática, não índice de segurança ou recomendação médica.

Nuvem, reinício real da VM, integração Flutter/Arduino, vídeo, link não listado e PDF com equipe completa só devem ser marcados concluídos após evidência real. Não é necessário modificar o projeto Java original para executar este repositório.

<a id="azure"></a>

## Azure

### Estado e escopo

**Preparação local, não implantação comprovada. Custo: estimativa pendente.** Nenhuma assinatura, região disponível, quota, capacidade ou preço foi confirmado por estes testes. Não há autorização implícita para consumir créditos, fazer login, registrar providers, provisionar, instalar ou publicar código.

A entrega executa uma VM Ubuntu 24.04 LTS amd64, Docker instalado explicitamente nela e Compose com Java 25 e PostgreSQL 16. O projeto Compose é `refugio-devops`; os containers são `refugio-api` e `refugio-db`; a rede global é `refugio-net`; o volume global é `refugio-pgdata`. Não execute um segundo projeto com esses nomes no mesmo host. PostgreSQL só publica `127.0.0.1:5432`; API publica `8080`. Ambos usam `restart: unless-stopped` no Compose.

Os cinco scripts operacionais usam `--plano` por padrão. Nesse modo não chamam nem mesmo `az`, não consultam a nuvem, não acessam o GitHub e não instalam nada. `_comum.sh` é biblioteca: mantenha-a ao lado dos demais scripts. Não há login, mudança de assinatura global, registro de provider, geração de chave, SSH ou limpeza automática dentro dos scripts.

### 1. Parâmetros — terminal local Git Bash

Execute da raiz do repositório. No Windows, use caminhos como `C:/Users/seu-usuario/.ssh/chave.pub` ou caminhos válidos no Git Bash. Os scripts desativam a conversão automática de argumentos MSYS para preservar CIDRs e consultas; o caminho da chave pública é convertido explicitamente com `cygpath -m` para a Azure CLI nativa, sem alterar o arquivo.

| Variável | Valor padrão | O que informar/verificar |
| --- | --- | --- |
| `AZURE_SUBSCRIPTION_ID` | nenhum | UUID da assinatura autorizada; formato validado antes da primeira chamada Azure |
| `RESOURCE_GROUP` | `refugio-devops` | grupo dedicado **novo**, prefixo `refugio-`, letras minúsculas, números e hífen |
| `LOCATION` | `brazilsouth` | região permitida pela assinatura; exemplo de configuração, não disponibilidade confirmada |
| `VM_SIZE` | `Standard_B2s` | tamanho x64 com memória para build Java e banco; capacidade e preço pendentes |
| `VM_NAME` | `refugio-vm` | nome da VM, prefixo `refugio-` |
| `ADMIN_USER` | `refugioadmin` | usuário Linux não root |
| `ADMIN_CIDR` | nenhum | seu IPv4 público administrativo com `/32`; sem rede ampla, zeros iniciais ou octetos inválidos |
| `SSH_PUBLIC_KEY` | nenhum | caminho de arquivo `.pub` existente, fornecido por você; nunca a chave privada |

Opcionais: `AZURE_IMAGE=Canonical:ubuntu-24_04-lts:server:latest` aceita essa família Ubuntu LTS, com arquitetura x64 conferida na execução; `AZURE_DISK_SKU=StandardSSD_LRS` aceita somente `StandardSSD_LRS` ou `Standard_LRS`. Os padrões ficam no topo de `_comum.sh`. Nenhum tamanho/região é trocado automaticamente em caso de erro.

Preparação de variáveis sem operação Azure, no terminal local Git Bash:

```bash
export AZURE_SUBSCRIPTION_ID='SUBSTITUA-PELO-UUID-AUTORIZADO'
export RESOURCE_GROUP=refugio-devops
export LOCATION=brazilsouth
export VM_SIZE=Standard_B2s
export VM_NAME=refugio-vm
export ADMIN_USER=refugioadmin
export ADMIN_CIDR='SEU-IPV4-PUBLICO/32'
export SSH_PUBLIC_KEY="$HOME/.ssh/sua-chave-existente.pub"
bash azure/01-provisionar.sh --plano
```

Os marcadores não são valores válidos e precisam ser substituídos. Não grave valores privados ou segredos no repositório. A autenticação da Azure CLI deve ocorrer separadamente, apenas quando autorizada. Antes da primeira execução real, confira a ajuda da CLI instalada para os comandos utilizados, suas permissões e os providers já registrados. Esses scripts não instalam nem fixam versão da CLI.

### 2. Autenticação, disponibilidade e custo — antes de provisionar

Estes comandos são executados no terminal **local Git Bash, WSL ou Azure Cloud Shell**, com uma conta e assinatura autorizadas. Não criam VM. Autenticação é separada de autorização para consumir créditos. Se a sessão já estiver autenticada, não repita login.

```bash
az login --use-device-code
az account list --query '[].{nome:name,id:id,estado:state}' --output table
az account show --subscription "$AZURE_SUBSCRIPTION_ID" --query '{nome:name,estado:state}' --output table
az account list-locations --subscription "$AZURE_SUBSCRIPTION_ID" --query '[].{regiao:name,nome:displayName}' --output table
az vm list-skus --subscription "$AZURE_SUBSCRIPTION_ID" --location "$LOCATION" --size "$VM_SIZE" --resource-type virtualMachines --all --output table
az vm list-usage --subscription "$AZURE_SUBSCRIPTION_ID" --location "$LOCATION" --output table
```

A lista geral de regiões não garante que a política do Azure for Students permita criação nelas; confira restrições da assinatura, quotas por família e região. Um SKU listado ainda pode falhar por falta de capacidade. Se região/tamanho forem bloqueados, interrompa e reveja `LOCATION`/`VM_SIZE` antes de outra tentativa, sem trocar para uma opção mais cara automaticamente.

A estimativa deve separar VM Linux, disco de SO, IP público Standard e tráfego. Consulte [preços de VM Linux](https://azure.microsoft.com/pricing/details/virtual-machines/linux/), [discos gerenciados](https://azure.microsoft.com/pricing/details/managed-disks/) e [IP público](https://azure.microsoft.com/pricing/details/ip-addresses/), selecionando a região e o tamanho efetivos. Sem essa consulta e confirmação de saldo, não há preço ou gratuidade garantidos. A proposta inicial de `Standard_B2s` é hipótese de dimensionamento, não autorização de custos.

### 3. Criar infraestrutura — terminal local Git Bash, somente após autorização de custos

**O comando abaixo cria recursos cobrados. Não é etapa de teste local.** Ele exige simultaneamente flag, autorização de custos e confirmação exata do grupo:

```bash
AUTORIZAR_CUSTOS=SIM CONFIRMAR_CRIACAO="CRIAR:$RESOURCE_GROUP" \
  bash azure/01-provisionar.sh --executar
```

Antes de criar o grupo, o script faz somente leituras: assinatura/id/estado, ausência do grupo, registro dos providers Compute/Network, imagem x64, SKU x64 sem restrições e quadro de quotas. A disponibilidade de quota não garante alocação, nem as leituras substituem a avaliação de políticas ou de custos. Grupo existente, inclusive com a tag correta, é recusado. Não há retomada automática de implantação parcial.

A infraestrutura inclui:

- Grupo e recursos identificados por `projeto=refugio-devops`.
- IP público IPv4 Standard estático; NIC, VNet e subnet dedicadas.
- NSG com duas permissões customizadas de entrada: TCP 22 de `ADMIN_CIDR` e TCP 8080 de `Internet`. Não há regra pública para 5432; permanecem as regras padrão da plataforma para rede virtual e bloqueio externo.
- VM Ubuntu x64 com chave pública existente e autenticação SSH; sem senha gerada.
- Disco simples Standard e opções `Delete` para disco de SO e NIC, conferidas por leitura após criar a VM.

O IP é mostrado apenas quando retornado por uma execução real autorizada. Não há IP de exemplo tratado como real. O script não instala Docker nem abre uma sessão SSH. Se falhar após começar a criação, podem restar recursos cobrados; não há exclusão automática. Analise o grupo e autorize a limpeza em separado.

### 4. Transferir e instalar Docker — contextos local e remoto separados

Somente depois de uma VM real autorizada e pronta: confira a identidade/fingerprint SSH por canal confiável; nunca desative a verificação do host. Use sua chave privada existente apenas na máquina local. Não envie `.env`, chave privada ou token para o Git.

Antes da primeira conexão, no terminal **local autenticado no Azure**, obtenha a impressão digital da chave de host pelo canal autenticado. Esse comando roda apenas uma consulta ao arquivo público dentro da VM já autorizada; não é uma verificação a executar antes de criar a VM:

```bash
az vm run-command invoke --subscription "$AZURE_SUBSCRIPTION_ID" --resource-group "$RESOURCE_GROUP" --name "$VM_NAME" --command-id RunShellScript --scripts 'ssh-keygen -l -E sha256 -f /etc/ssh/ssh_host_ed25519_key.pub' --query 'value[0].message' --output tsv
```

Compare o fingerprint ED25519 retornado com o mostrado pelo SSH antes de responder `yes`. Ele identifica o servidor, não a chave pessoal do usuário. Se recriar a VM, confira novamente; não contorne um alerta de mudança de identidade.

Terminal **local Git Bash**, ajustando o IP real retornado e o caminho da chave privada, e somente após autorizar SSH:

```bash
export PUBLIC_IP='SUBSTITUA-PELO-IP-REAL'
export SSH_PRIVATE_KEY="$HOME/.ssh/sua-chave-existente"
export DESTINO_SSH="$ADMIN_USER@$PUBLIC_IP"
ssh -i "$SSH_PRIVATE_KEY" "$DESTINO_SSH"
```

Na sessão **remota Ubuntu**, crie somente a pasta de scripts e saia:

```bash
mkdir -p "$HOME/azure-refugio"
exit
```

De volta ao terminal **local Git Bash**, transfira os scripts com a biblioteca antes de conectar novamente:

```bash
scp -i "$SSH_PRIVATE_KEY" azure/_comum.sh azure/02-instalar-docker.sh azure/03-deploy.sh "$DESTINO_SSH:azure-refugio/"
ssh -i "$SSH_PRIVATE_KEY" "$DESTINO_SSH"
```

Na sessão **remota Ubuntu 24.04 LTS amd64**, execute o plano e depois, apenas com autorização para alterar a VM, a instalação. `DOCKER_USER` deve ser o usuário Linux já existente; `id -un` captura o usuário antes do sudo:

```bash
cd "$HOME/azure-refugio"
bash 02-instalar-docker.sh --plano
sudo env AUTORIZAR_ALTERACAO_LOCAL=SIM CONFIRMAR_DOCKER=INSTALAR-DOCKER-NA-VM DOCKER_USER="$(id -un)" \
  bash 02-instalar-docker.sh --executar
```

O instalador exige root/Linux/Ubuntu 24.04/amd64 e usuário não root validado. Usa o repositório HTTPS oficial Docker com chave `signed-by`, instala Docker Engine, Buildx, Compose plugin, curl, git e OpenSSL, habilita o serviço no boot e verifica serviço/grupo. O grupo `docker` concede privilégios equivalentes a root. Saia e reconecte por SSH para aplicar a nova associação; não rode deploy com sudo.

### 5. Deploy — sessão remota Ubuntu, usuário no grupo docker

O repositório remoto precisa conter a versão aprovada/publicada. Um repositório vazio não permite deploy: publicação é uma autorização separada e não é feita por estes scripts.

```bash
cd "$HOME/azure-refugio"
bash 03-deploy.sh --plano
AUTORIZAR_ALTERACAO_LOCAL=SIM CONFIRMAR_DEPLOY=DEPLOY-REFUGIO-NA-VM \
  bash 03-deploy.sh --executar
```

O destino fixo é `$HOME/refugio-devops`; a origem fixa é `https://github.com/Guilherme-Soares00/refugio-devops.git`, branch `main`. O script recusa links, diretório estranho, origin diferente, branch diferente e alterações locais; atualização usa fetch e merge `--ff-only`, nunca reset ou remoção de trabalho local.

Ele chama `bash scripts/preparar-ambiente.sh --dados-demo --publicar-api`. O helper cria `.env` com permissão 600 somente se ausente; arquivo existente não é sobrescrito, exibido nem enviado. O deploy confere seu hash antes/depois sem mostrar o hash/conteúdo. Se o arquivo existente tiver outra permissão, ajuste-a conscientemente na VM antes de repetir. São fornecidas ao Compose somente variáveis não secretas: projeto `refugio-devops`, carga demo ativa, API em `0.0.0.0:8080`, banco publicado só no loopback 5432 definido no Compose. Credenciais existentes permanecem no `.env`.

A subida usa `docker compose up -d --build --wait --wait-timeout 300`. Esse timeout limita a espera de saúde, não o tempo total do build. Depois são consultados os serviços e os endpoints locais `/actuator/health` e `/api/pontos` com curl, instalado na etapa anterior. Sucesso local não comprova acessibilidade externa.

Terminal **local**, leitura pública sem autenticação, após trocar o IP:

```bash
curl --fail "http://$PUBLIC_IP:8080/api/pontos"
```

Não envie senha/JWT pelo IP público HTTP. Para autenticação e escrita administrativas, use HTTPS ou um túnel SSH autorizado, por exemplo no terminal **local Git Bash**:

```bash
ssh -i "$SSH_PRIVATE_KEY" -N -L 127.0.0.1:18080:127.0.0.1:8080 "$DESTINO_SSH"
```

Enquanto o túnel estiver ativo, use `http://127.0.0.1:18080` no cliente administrativo. Não imprima tokens, `.env` ou resultados de login em logs públicos.

### 6. Parar, iniciar e excluir — terminal local Git Bash

Planos seguros, sem chamadas Azure:

```bash
bash azure/parar-iniciar.sh parar --plano
bash azure/parar-iniciar.sh iniciar --plano
bash azure/limpar.sh --plano "$RESOURCE_GROUP"
```

**Desalocar** requer autorização própria; não equivale a eliminar todos os custos. Disco, volume e IP continuam existentes e podem ser cobrados:

```bash
AUTORIZAR_ALTERACAO_AZURE=SIM CONFIRMAR_PARADA="PARAR:$RESOURCE_GROUP:$VM_NAME" \
  bash azure/parar-iniciar.sh parar --executar
```

**Iniciar** volta a gerar cobrança de computação e exige nova confirmação de custos:

```bash
AUTORIZAR_CUSTOS=SIM CONFIRMAR_INICIO="INICIAR:$RESOURCE_GROUP:$VM_NAME" \
  bash azure/parar-iniciar.sh iniciar --executar
```

Ambas as ações validam assinatura, prefixo, existência e tag do grupo e da VM, e releem o estado de energia antes de declarar sucesso. Reiniciar não remove o volume Postgres; não use `docker compose down -v` se pretende demonstrar persistência.

**Excluir é irreversível:** apaga o grupo inteiro e todos os dados do volume, não apenas a VM. Exige nome explícito igual à variável, confirmação exata e tag de propriedade. Remover somente a VM não garante remoção do IP público nem dos demais recursos.

```bash
AUTORIZAR_EXCLUSAO=SIM CONFIRMAR_EXCLUSAO="EXCLUIR:$RESOURCE_GROUP" \
  bash azure/limpar.sh --executar "$RESOURCE_GROUP"
```

O script lista os recursos, aguarda a exclusão síncrona, verifica grupo ausente e lista vazia consultando a assinatura e filtrando o nome do grupo. A consulta final não usa o endpoint de um grupo já removido. Falha em confirmar a ausência não é apresentada como sucesso. Não há limpeza automática depois de outro script falhar.

### 7. Validação exclusivamente local

Terminal **local Git Bash**, sem login, rede, instalação, Docker ou chamadas Azure reais:

```bash
for arquivo in azure/*.sh tests/test-azure.sh; do bash -n "$arquivo" || exit; done
bash tests/test-azure.sh
```

Os testes exportam doubles Bash que bloqueiam comandos reais, verificam plano/autorizações incompletas e simulam leituras/escritas Azure, NSG, escopo de assinatura, rejeição de grupo existente, imagem ARM, erro parcial sem limpeza, ciclo de vida, tags e exclusão confirmada. São testes de controle de fluxo, não instalação Linux real, compilação em VM, validação de respostas reais da Azure, persistência em nuvem ou prova de implantação/custo. Esses itens continuam dependentes de autorização e execução no ambiente correspondente.

Referências de sintaxe: [Azure CLI VM](https://learn.microsoft.com/cli/azure/vm), [Azure CLI VNet](https://learn.microsoft.com/cli/azure/network/vnet), [instalação oficial do Docker no Ubuntu](https://docs.docker.com/engine/install/ubuntu/). Consulte também a ajuda da CLI instalada antes de autorizar operações reais.

<a id="arquitetura"></a>

## Arquitetura

### Estado e limites

**Implementado e testado localmente:** imagem Java 25, PostgreSQL 16, Compose, healthchecks, rede, volume, CRUD e SELECT. **Preparado, ainda não implantado:** grupo Azure, IP público estático, NSG e VM Ubuntu. **Externo/proposto:** Flutter e gateway Arduino; este repositório não comprova essas integrações.

```mermaid
flowchart LR
    cliente["Swagger / Postman / navegador"]
    flutter["Flutter — externo, integração a comprovar"]
    gateway["Arduino → USB/Serial → gateway — externo/proposto"]
    internet(("Internet"))
    admin["Administração — SSH / túnel"]
    subgraph azure["Microsoft Azure — implantação pendente"]
      subgraph rg["Grupo de recursos dedicado refugio-*"]
        ip["IP público Standard estático"]
        nsg["NSG associado à NIC\n22: IP administrativo /32\n8080: demonstração pública\n5432: sem regra de entrada"]
        subgraph vm["VM Ubuntu LTS — tamanho/região a validar"]
          ssh["sshd :22"]
          subgraph engine["Docker Engine + Compose"]
            subgraph net["Rede bridge refugio-net"]
              api["refugio-api :8080\nSpring Boot / JRE 25\nUID 10001 não-root"]
              db["refugio-db :5432\nPostgreSQL 16\nprocesso UID 999 postgres"]
            end
            volume[("Volume refugio-pgdata\n/var/lib/postgresql/data")]
          end
          loopback["127.0.0.1:5432\nacesso somente no host"]
        end
      end
    end
    cliente -->|HTTP público: leitura demonstrativa| internet
    flutter -.-> internet
    gateway -.-> internet
    internet --> ip
    ip -->|porta 8080| api
    admin -->|SSH 22 /32| ssh
    ssh -->|túnel para login e escrita| api
    nsg -. associação .-> vm
    api -->|JDBC db:5432 / DNS interno| db
    loopback --> db
    db -->|dados persistentes| volume
```

O NSG é um conjunto de regras associado à NIC, não um servidor intermediário. A única entrada pública da aplicação é 8080. A porta do PostgreSQL é publicada em loopback para satisfazer a configuração de porta sem expor o banco à internet.

A composição local usa os mesmos nomes, mas não contém VM, IP público ou NSG. Os nomes explícitos de container/rede/volume impedem instâncias paralelas com o mesmo nome: não tente subir outro projeto com esses identificadores.

### Persistência

O JAR e a JVM ficam na imagem/contêiner da API. O cache Spring vive em memória e se perde ao reiniciar. As tabelas PostgreSQL ficam no volume `refugio-pgdata`; não ficam na imagem da API. `docker compose down` remove containers e rede, mas mantém o volume. **`docker compose down -v` apaga o volume e seus dados; não executar durante a demonstração.** Deletar o grupo Azure também apaga os discos e, portanto, os dados da VM.

### Imagens para reutilizar

Abra [arquitetura.html](docs/devops/arquitetura.html) no navegador. A visualização corresponde à topologia acima e identifica Azure como pendente. Há versões [SVG](docs/devops/arquitetura.svg) e [PNG](docs/devops/arquitetura.png) para README e gravação.

Para PDF: abra o HTML no Chrome, pressione Ctrl+P, escolha Salvar como PDF, orientação paisagem, sem cabeçalhos/rodapés e habilite gráficos de fundo. Confira a legibilidade antes de anexar. O Mermaid acima também é renderizado pelo GitHub e pode ser exportado pelo Mermaid Live Editor, sem enviar segredos ou identificadores de conta.

Atualize o estado do diagrama somente depois de executar os testes pela URL pública e na VM real.

<a id="evidencias"></a>

## Evidências

### Estado da validação

As verificações abaixo foram executadas na máquina local com Docker Desktop Linux. **Não são evidências de Azure.** O provisionamento pago foi mantido bloqueado, sem executar criação de recursos, instalação remota ou deploy.

| Verificação executada | Resultado observado |
| --- | --- |
| Build Docker com Dockerfile deste repositório | concluído; imagem `refugio-api:devops` |
| API e PostgreSQL | ambos `healthy` |
| Testes Java, `mvnw.cmd -B verify` | 104 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS |
| Testes locais Node de Compose, ambiente e transporte | 3 aprovados |
| Testes de segurança dos scripts Azure | 57 verificações locais no Windows com doubles; nenhuma chamada Azure real |
| Sintaxe dos scripts e consultas | bash -n aprovado; flags principais conferidas em --help da CLI instalada; três consultas JMESPath compiladas offline |
| Fluxo do instalador Ubuntu | simulado em container Ubuntu 24.04 amd64 sem rede; apt, downloads e serviços substituídos por doubles; não é instalação real |
| CRUD HTTP e segurança | 27 verificações aprovadas, incluindo 200/201/204/400/401/403/404/409 |
| Persistência | REF-990 e seu recurso AGUA sobreviveram a `down` sem `-v` seguido de `up` |
| SELECT com JOIN depois da recriação | ponto ID 13, REF-990, nome Persistencia DevOps, recurso ID 22, AGUA disponível |
| Seed antes/depois da recriação | mesmos totais: 10 usuários, 11 pontos, 21 recursos, 10 medições e 10 ocorrências |
| Usuário e diretório da API | `refugio`, UID/GID 10001, `/app`, `app.jar` |
| Ferramenta de saúde na API | `/usr/bin/curl` presente e utilizado no healthcheck |
| Runtime efetivo da imagem testada | Temurin 25.0.5+8; tag 25-jre-jammy pode receber atualizações |
| Swagger UI real no navegador | 17 operações carregadas, sem erros; GET /api/pontos executado com resposta 200 |
| Fluxo manual curl do guia | executado; dois PUTs e dois DELETEs retornaram 204 |
| Processo do PostgreSQL | `docker compose top db` mostrou UID 999; `id postgres` confirmou esse usuário |
| `exec db whoami` | retornou root; é o usuário do exec, não o do servidor PostgreSQL |

Os 11 pontos e 21 recursos incluem um ponto/recurso adicional de persistência. A carga inicial original tem 10 pontos e 20 recursos. Os testes de CRUD excluem seus próprios registros ao terminar. IDs são resultados desta execução, não valores a fixar nos comandos de outro ambiente.

[Captura real do GET 200 no Swagger local](docs/devops/evidencias/swagger-local-get-200.png).

Relatórios não versionados: `target/surefire-reports`, `target/validacao-maven.log` e `target/devops/*.json`. Não publique logs brutos sem revisar segredos.

### Sequência de evidências técnicas

Execute na raiz do projeto; use Git Bash local para os testes locais ou SSH na VM para evidenciar containers na nuvem. Sempre deixe visível o contexto do terminal para não confundir host e VM.

#### 1. Ferramentas e serviços

```bash
docker --version
docker compose version
docker compose up -d --wait --wait-timeout 240
docker compose ps
```

Capture os dois serviços saudáveis. No Azure, mostre antes `hostname` e a saída de `az vm show` a partir de um terminal autenticado, limitando os campos a nome, região, tamanho e estado. Obtenha o IP com o comando documentado em seção Azure deste README.

#### 2. Logs sem segredos

```bash
docker compose logs --tail 40 api
docker compose logs --tail 40 db
```

Mostre inicialização da API e `database system is ready to accept connections` do banco. Não mostre `.env` nem `docker inspect` completo.

#### 3. Acesso, usuário e diretórios

```bash
docker compose exec -T api sh -c 'whoami; id; pwd; ls -la /app'
docker compose exec -T api sh -c 'printf "SERVER_PORT=%s\n" "$SERVER_PORT"; printf "SPRING_DATASOURCE_URL=%s\n" "$SPRING_DATASOURCE_URL"'
docker compose exec -T db sh -c 'id postgres; pwd; ls -ld /var/lib/postgresql/data'
docker compose exec -T --user postgres db id
docker compose top db
docker compose exec -T db sh -c 'printf "POSTGRES_DB=%s\nPOSTGRES_USER=%s\n" "$POSTGRES_DB" "$POSTGRES_USER"'
```

Na fala: o servidor PostgreSQL roda como postgres mesmo que um exec sem `--user` use root. A conta SQL refugio_app não é o usuário Linux postgres. Não afirme que o banco tem conta SQL de menor privilégio: a imagem cria o usuário inicial com privilégios elevados.

#### 4. Volume, rede e dados relacionados

```bash
docker volume inspect refugio-pgdata --format '{{.Name}} {{.Driver}}'
docker network inspect refugio-net --format '{{.Name}} {{.Driver}}'
docker compose exec -T db psql -U refugio_app -d refugio -v ON_ERROR_STOP=1 -c "SELECT p.codigo_publico,p.nome,r.tipo,r.disponivel FROM ponto_refugio p JOIN recurso_ponto r ON r.ponto_id=p.id ORDER BY p.codigo_publico,r.tipo;"
```

O relacionamento físico é `recurso_ponto.ponto_id → ponto_refugio.id`. O SELECT é diretamente no PostgreSQL do container, não uma resposta simulada da API.

#### 5. CRUD completo e controle de acesso

```bash
node scripts/testar-api.mjs http://localhost:8080 crud
```

Para gravação, demonstre também os payloads do fluxo manual do guia ou o Swagger pelo túnel SSH. A automação imprime método, rota e status, nunca o token. Use GESTOR para escrita e CIDADAO para evidenciar 403. Sem token, a escrita retorna 401.

#### 6. Persistência

Use a seção 6 do guia: preparar uma única vez, SELECT antes, `down` **sem `-v`**, `up -d`, verificar novamente e SELECT depois. Se REF-990 já existir, não repita o preparo; execute diretamente `verificar-persistencia`. Confirme os mesmos IDs e valores.

### Evidências de nuvem — ainda pendentes

- [ ] Código revisado e publicado no repositório DevOps, com autorização de push.
- [ ] Região, SKU, quotas e estimativa de VM/disco/IP conferidos.
- [ ] Autorização específica para criar recursos e consumir créditos.
- [ ] VM efetivamente criada por Azure CLI, IP estático e regras NSG conferidas.
- [ ] Docker e Compose instalados explicitamente dentro da VM.
- [ ] API e banco saudáveis dentro da VM, não somente no computador.
- [ ] GET público de saúde e catálogo a partir do computador externo.
- [ ] Swagger carregado e CRUD na aplicação remota, com login/token pelo túnel SSH.
- [ ] SELECT direto no banco da VM e persistência após recriar containers.
- [ ] Reboot real da VM e retorno automático dos containers/serviço Docker.
- [ ] Vídeo não listado em 720p ou mais, narrado, links acessíveis e PDF completo.

Não substitua essas evidências por execução local, teste com doubles, diagrama ou saída esperada.
