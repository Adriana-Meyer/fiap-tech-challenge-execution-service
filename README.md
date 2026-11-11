# fiap-tech-challenge-execution-service

Microsserviço de **Execução** (fila de serviços da oficina e tempos de execução) do Tech Challenge — FIAP Pós-Tech, Arquitetura de Software, turma 15SOAT, **Fase 4** (microsserviços + Saga).

Faz parte do sistema de gestão de oficina mecânica, que na Fase 4 foi dividido em quatro serviços independentes (OS, Billing, Execution e Inventory). Cada serviço tem repositório, infraestrutura e banco próprios.

| Repositório | Papel |
|---|---|
| **fiap-tech-challenge-execution-service** (este) | Código do serviço de execução |
| `fiap-tech-challenge-execution-database-infrastructure` | Tabela DynamoDB do serviço (Terraform) |
| `fiap-tech-challenge-execution-infrastructure` | Namespace `execution` e filas SQS do serviço (Terraform) |
| `fiap-tech-challenge-os-service` | Serviço de OS (orquestrador da saga) |
| `fiap-tech-challenge-os-infrastructure` | VPC, EKS compartilhado, New Relic e tópico SNS |

> **Estado atual:** esqueleto do serviço com o domínio, os casos de uso, a persistência em DynamoDB e a API REST usada pelos mecânicos. O **handler do comando `EnqueueExecution` (SQS)** e a publicação do evento `ExecutionCompleted` (SNS) entram no próximo PR; até lá, as ordens só entram na fila pelos testes.

## Responsabilidade

Dono da **fila de execução**: para cada OS aprovada e paga, as tarefas (serviços) a executar, o mecânico que assumiu cada uma e os tempos. Nenhum outro serviço acessa a tabela deste.

Na saga, o Execution executa o passo `[4]` e fecha o fluxo:

| Entrada | Caso de uso | Efeito |
|---|---|---|
| Comando `EnqueueExecution` (SQS `execution-commands`) | `EnqueueExecutionUseCase` | Cria a `ExecutionOrder` (`QUEUED`) com uma tarefa por serviço da OS. Repetir o comando devolve a ordem existente, sem apagar o progresso. |
| `PATCH …/tasks/{taskId}/start` | `StartTaskUseCase` | Mecânico assume a tarefa (`IN_PROGRESS`); a ordem passa a `IN_PROGRESS`. |
| `PATCH …/tasks/{taskId}/finish` | `FinishTaskUseCase` | Conclui a tarefa. Quando a última termina, a ordem vira `COMPLETED` e o tempo total é publicado no New Relic (`ServiceOrderStatusDuration`, status `EXECUTION`). |

Regras de consistência:

- **Uma ordem por OS** (chave de partição `serviceOrderId`). O `id` de cada tarefa é o id do item na OS, e nome e tipo do serviço chegam como **snapshot**: o Execution nunca consulta o catálogo.
- **Transições estritas:** só inicia tarefa `PENDING` e só conclui tarefa `IN_PROGRESS`. O monólito aceitava concluir sem iniciar, o que quebrava a média de tempo; aqui isso vira `409 Conflict`.
- **Concorrência:** dois mecânicos podem concluir tarefas da mesma OS ao mesmo tempo. Cada gravação usa um atributo de versão (lock otimista do DynamoDB); quem chega com a versão antiga recebe `409` e repete, sem sobrescrever a alteração do outro.
- **Tempo de execução da OS** = da entrada na fila (OS em `IN_EXECUTION`) até a conclusão da última tarefa.

## Arquitetura

Clean Architecture em quatro camadas, com a regra de dependência `domain ← application ← infrastructure / interfaces` **verificada no build** por ArchUnit (`ArchitectureTest`).

```
src/main/java/com/fiap/workshop/execution/
├── domain/            ExecutionOrder, ExecutionTask, status, MetricsPublisher (port),
│                      ExecutionOrderRepository (interface). Sem Spring/AWS.
├── application/       Use cases (um por operação), ports de entrada, DTOs.
├── infrastructure/    Adaptadores: DynamoDB (AWS SDK v2 Enhanced Client), segurança JWT,
│                      New Relic, OpenAPI.
└── interfaces/rest/   ExecutionController (fino) e GlobalExceptionHandler.
```

Decisões:

- **DynamoDB, um item por OS:** a ordem e suas tarefas ficam no mesmo item (tabela `execution-orders`, chave de partição `serviceOrderId`, tipo `S`). Uma OS tem poucas tarefas, o item fica muito abaixo do limite de 400 KB, e ler ou gravar a OS inteira é uma operação só.
- **Listagens com `Scan`:** a fila e a média de tempo por serviço varrem a tabela. São leituras pequenas e pouco frequentes (uma oficina); um índice por status seria o passo seguinte se o volume justificasse.
- **Tabela criada pelo Terraform na AWS:** o serviço só cria a tabela sozinho quando `DYNAMODB_AUTO_CREATE_TABLE=true` (docker-compose e testes). Em produção essa variável fica desligada.
- **Sem segredo de banco:** os pods acessam o DynamoDB pela role IAM do node; o único segredo do serviço é o `JWT_SECRET` (e a chave do New Relic).
- **Autenticação:** o token JWT é emitido pelo OS Service; aqui ele só é **validado** (HMAC com o `JWT_SECRET` compartilhado, o mesmo do OS). O token precisa das claims `sub` e `role`; sem `role` a requisição é tratada como não autenticada (401). O `sub` do token é gravado como mecânico da tarefa.
- **Observabilidade:** agente New Relic anexado no `Dockerfile` (`NEW_RELIC_APP_NAME=workshop-execution-service`), logs em JSON com `trace.id`/`span.id`.

## API

Swagger UI: `http://<host>/swagger-ui.html` · OpenAPI: `/v3/api-docs`. As rotas de `/api/v1` exigem `Authorization: Bearer <jwt>`.

| Método | Rota | Perfis |
|---|---|---|
| `GET` | `/api/v1/executions` (fila: ordens `QUEUED` e `IN_PROGRESS`, mais antigas primeiro) | CONSULTANT, MECHANIC, ADMIN |
| `GET` | `/api/v1/executions/{serviceOrderId}` | CONSULTANT, MECHANIC, ADMIN |
| `PATCH` | `/api/v1/executions/{serviceOrderId}/tasks/{taskId}/start` | MECHANIC, ADMIN |
| `PATCH` | `/api/v1/executions/{serviceOrderId}/tasks/{taskId}/finish` | MECHANIC, ADMIN |
| `GET` | `/api/v1/executions/analytics/avg-execution-time` (tempo médio por serviço) | ADMIN |
| `GET` | `/actuator/health` | público |

A análise de tempo médio, que no monólito ficava em `/service-orders/analytics`, mudou para cá: só o Execution tem os tempos das tarefas.

## Como rodar localmente

```bash
docker compose up --build        # DynamoDB Local + serviço em http://localhost:8083
```

Para chamar a API é preciso um JWT assinado com o mesmo `JWT_SECRET`, contendo a claim `role` (o segredo de desenvolvimento está em `application.yml`). Sem o handler SQS ainda não há como enfileirar uma ordem fora dos testes.

## Testes e qualidade

```bash
mvn verify        # testes unitários + integração (Testcontainers/DynamoDB Local, requer Docker) + gate JaCoCo >= 80%
```

- Cobertura atual: ~96% de instruções (gate do build: 80%). Relatório em `target/site/jacoco/`.
- `ArchitectureTest` falha o build se uma camada violar a regra de dependência.
- `ExecutionApiIntegrationTest` sobe o serviço inteiro contra um DynamoDB Local (a tabela é criada pelo próprio serviço) e cobre segurança, REST e persistência: fila, ordem concluída do início ao fim, transições inválidas, idempotência do enfileiramento e a detecção de gravações concorrentes.
- Análise estática e quality gate no **SonarQube Cloud** (step do CI, ativo quando o secret `SONAR_TOKEN` existe).

## CI/CD

Workflows em [`.github/workflows/`](.github/workflows/):

- `ci-cd.yml` — job `build-and-test` (`mvn verify` + Sonar) em todo push/PR; em `develop`/`main` também publica a imagem `adrianameyer/workshop-execution-service` no Docker Hub.
- `deploy-aws.yml` — deploy/destroy no EKS compartilhado (namespace `execution`), disparado manualmente (`workflow_dispatch`). Também roda após um CI verde na `main` **se** a variável de repositório `AUTO_DEPLOY` for `true`.

Secrets do repositório: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` (renovados a cada sessão do Lab), `JWT_SECRET` (igual ao do OS), `NEW_RELIC_LICENSE_KEY` e `SONAR_TOKEN`. Não há secrets de banco.

Ordem de subida no Lab: `os-infrastructure` → `execution-infrastructure` → `execution-database-infrastructure` (tabela) → deploy deste serviço. Na destruição, a ordem inversa (rodar `destroy` aqui primeiro libera o NLB).

Contrato com o `execution-database-infrastructure`: tabela `execution-orders` (variável `DYNAMODB_TABLE`), chave de partição `serviceOrderId` do tipo `S`, sem chave de ordenação, cobrança `PAY_PER_REQUEST`. A role IAM do node precisa de `dynamodb:GetItem`, `dynamodb:UpdateItem` e `dynamodb:Scan` nessa tabela.
