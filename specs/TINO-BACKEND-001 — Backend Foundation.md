# TINO-BACKEND-001 — Backend Foundation

**Status:** READY FOR IMPLEMENTATION
**Projeto:** TINO
**Escopo:** Backend Cloud
**Prioridade:** P0
**Tipo:** Engineering Specification
**Data:** 2026-08-16

---

# 1. Objetivo

Criar a primeira fundação real do backend cloud do TINO.

Esta etapa NÃO implementa ainda:

* inteligência;
* Gemma;
* recomendações;
* ML;
* WhatsApp;
* emissão fiscal;
* fornecedores;
* pedidos;
* relatórios avançados.

O objetivo desta rodada é criar a infraestrutura mínima capaz de:

```text
Android TINO
    ↓
autenticar
    ↓
identificar comércio
    ↓
identificar dispositivo
    ↓
receber eventos locais
    ↓
armazenar com segurança
    ↓
retornar alterações
    ↓
permitir recuperação em outro celular
```

---

# 2. Princípio central

O backend cloud NÃO é o backend operacional primário do comerciante.

O Android continua:

```text
LOCAL-FIRST
```

A cloud é responsável por:

```text
durabilidade
sincronização
recuperação
integrações externas
inteligência futura
```

Portanto:

> O comércio continua funcionando sem a cloud.

Mas:

> Perder o aparelho não pode significar perder o comércio.

---

# 3. Arquitetura

```text
                  TINO ANDROID
                       │
                       │ HTTPS
                       ▼
                ┌──────────────┐
                │  TINO API    │
                └──────┬───────┘
                       │
        ┌──────────────┼───────────────┐
        ▼              ▼               ▼
     Identity        Sync          Recovery
        │              │               │
        └──────────────┼───────────────┘
                       ▼
                  PostgreSQL
                       │
              ┌────────┴────────┐
              ▼                 ▼
           Events            Snapshots
```

---

# 4. Responsabilidades do backend

Backend P0 será responsável por:

## Identity

* usuários;
* estabelecimentos;
* memberships;
* dispositivos;
* sessões.

## Sync

* ingestão de eventos;
* idempotência;
* ordenação;
* cursor;
* pull de mudanças.

## Recovery

* snapshots;
* bootstrap de aparelho;
* recuperação de dados.

## Observability

* health;
* logs;
* métricas;
* tracing básico.

---

# 5. Não responsabilidades

Backend NÃO deve:

* impedir venda porque está offline;
* calcular estoque em tempo real para o Android;
* substituir Room;
* executar SQL enviado pelo cliente;
* confiar cegamente em payload recebido;
* executar ações propostas por LLM;
* armazenar senha em texto puro;
* utilizar `last-write-wins` indiscriminadamente;
* depender de Gemma.

---

# 6. Stack proposta

Backend deve nascer simples.

```text
Application
    ↓
HTTP API
    ↓
Application Services
    ↓
Domain
    ↓
Ports
    ↓
Adapters
    ↓
PostgreSQL
```

Requisitos tecnológicos:

* linguagem fortemente tipada;
* API HTTP;
* PostgreSQL;
* migrations versionadas;
* Docker;
* testes automatizados;
* OpenAPI;
* structured logging;
* health endpoint.

Não introduzir:

* Kubernetes;
* Kafka;
* Redis;
* Elasticsearch;
* microservices;
* service mesh.

Sem necessidade comprovada.

---

# 7. Arquitetura lógica

```text
src/
│
├── domain/
│   ├── identity/
│   ├── store/
│   ├── device/
│   └── sync/
│
├── application/
│   ├── auth/
│   ├── device/
│   ├── sync/
│   └── recovery/
│
├── infrastructure/
│   ├── persistence/
│   ├── security/
│   ├── observability/
│   └── clock/
│
├── presentation/
│   └── http/
│
└── bootstrap/
```

---

# 8. Regra de dependência

```text
presentation
     ↓
application
     ↓
domain
     ↑
infrastructure
```

Domain NÃO conhece:

* HTTP;
* PostgreSQL;
* JWT;
* framework;
* Android;
* Room;
* Gemma.

---

# 9. Entidades iniciais

## User

```text
User
- id
- name
- phone
- status
- created_at
```

---

## Store

Representa o comércio.

```text
Store
- id
- name
- status
- created_at
```

---

## StoreMembership

Relaciona pessoa ao estabelecimento.

```text
StoreMembership
- id
- user_id
- store_id
- role
- status
```

Não assumir:

```text
1 usuário = 1 comércio
```

---

# 10. Device

Cada instalação Android deve possuir identidade própria.

```text
Device
- id
- store_id
- user_id
- installation_id
- platform
- app_version
- status
- registered_at
- last_seen_at
```

Status:

```text
ACTIVE
REVOKED
LOST
REPLACED
```

---

# 11. IDs

Nenhuma entidade sincronizável deve depender do banco para gerar ID.

Preferência:

```text
UUIDv7
```

IDs devem poder nascer no Android offline.

Exemplos:

```text
store_id
user_id
device_id

sale_id
customer_id
product_id

event_id
```

---

# 12. Autenticação

MVP deve possuir autenticação simples e segura.

Fluxo conceitual:

```text
celular
  ↓
identificação
  ↓
verificação
  ↓
session
  ↓
access token
```

Backend deve possuir abstração:

```text
AuthPort
```

Não espalhar autenticação diretamente pelos controllers.

---

# 13. Device Registration

Após autenticação:

```text
Android
 ↓
POST /devices
 ↓
Device registrado
 ↓
device_id
```

Cada requisição sincronizada deve identificar:

```text
user
store
device
```

---

# 14. Multi-device desde a modelagem

Mesmo que o piloto utilize um único telefone:

```text
Store
 ├── Device A
 ├── Device B
 └── Device C
```

deve ser permitido pelo domínio.

Não implementar restrição:

```text
store.device_id
```

---

# 15. Sync Contract

O Android produz eventos localmente.

Exemplo:

```json
{
  "event_id": "019...",
  "store_id": "019...",
  "device_id": "019...",
  "aggregate_id": "019...",
  "type": "stock.received",
  "schema_version": 1,
  "occurred_at": "2026-08-16T21:10:00-03:00",
  "payload": {}
}
```

---

# 16. Event Envelope

Todo evento sincronizado deve possuir envelope padrão.

Campos obrigatórios:

```text
event_id
store_id
device_id

aggregate_id
aggregate_type

event_type
schema_version

occurred_at
received_at

payload
```

---

# 17. Proveniência

Cloud deve preservar:

```text
quem
qual dispositivo
qual comércio
quando ocorreu
quando chegou
qual origem
```

Não sobrescrever proveniência original.

---

# 18. Idempotência

Regra crítica.

Se Android enviar:

```text
event_id = ABC
```

10 vezes:

backend produz efeito:

```text
1 vez
```

Banco deve garantir unicidade por `event_id`.

---

# 19. Sync Push

Endpoint conceitual:

```text
POST /v1/sync/push
```

Request:

```json
{
  "device_id": "...",
  "events": []
}
```

Response:

```json
{
  "accepted": [],
  "already_processed": [],
  "rejected": [],
  "server_cursor": "..."
}
```

---

# 20. Batch

Não fazer uma chamada HTTP para cada evento.

Permitir batch.

Exemplo:

```text
1–100 eventos
```

por chamada inicialmente.

Tamanho final deve ser configurável.

---

# 21. Atomicidade

Cada evento individual precisa ser processado atomicamente.

Um evento inválido não deve necessariamente impedir processamento dos demais eventos do batch.

Resposta precisa indicar resultado individual.

---

# 22. Sync Pull

Endpoint:

```text
GET /v1/sync/changes
```

Parâmetros:

```text
store_id
cursor
limit
```

Resposta:

```json
{
  "changes": [],
  "next_cursor": "...",
  "has_more": false
}
```

---

# 23. Cursor

Não utilizar somente timestamp como cursor.

Backend deve manter cursor monotônico próprio.

Exemplo conceitual:

```text
server_sequence
```

Cada mudança persistida recebe sequência.

Assim:

```text
cursor=105
```

significa:

> envie mudanças posteriores à sequência 105.

---

# 24. Não depender do relógio do celular

`occurred_at` representa quando o dispositivo acredita que a operação aconteceu.

Mas ordenação de sync não pode depender exclusivamente desse horário.

Cloud adiciona:

```text
received_at
server_sequence
```

---

# 25. Persistência de eventos

Tabela conceitual:

```text
sync_events
```

Campos:

```text
event_id
store_id
device_id

aggregate_id
aggregate_type

event_type
schema_version

occurred_at
received_at

server_sequence

payload

processing_status
```

---

# 26. Payload

Payload pode ser armazenado inicialmente como:

```text
JSONB
```

Mas isso NÃO elimina validação de schema.

Cada `event_type + schema_version` deve possuir contrato conhecido.

---

# 27. Schema Registry interno

Criar mecanismo simples de validação:

```text
sale.created:v1
stock.received:v1
credit.sale.created:v1
...
```

Evento desconhecido:

```text
REJECTED
```

Não salvar silenciosamente qualquer JSON.

---

# 28. Versionamento

Nunca mudar significado de:

```text
sale.created:v1
```

Depois de publicado.

Mudança incompatível:

```text
sale.created:v2
```

---

# 29. Eventos iniciais suportados

P0 deve preparar suporte para:

```text
product.created
product.updated
product.price.changed

customer.created
customer.updated

supplier.created
supplier.updated

sale.created
sale.cancelled

stock.received
stock.adjusted
stock.sold

credit.sale.created
credit.payment.received
```

Não é necessário implementar toda projeção cloud nesta primeira entrega.

Mas contratos precisam estar versionados.

---

# 30. Estoque

Backend NÃO deve sincronizar simplesmente:

```text
stock.quantity = 12
```

Fonte canônica:

```text
StockMovement
```

Exemplos:

```text
+24 RECEIPT
-2 SALE
-1 DAMAGE
+5 ADJUSTMENT
```

---

# 31. Fiado

Mesma regra.

Não sincronizar:

```text
customer.balance = 120
```

Fonte:

```text
CreditEntry
```

Exemplo:

```text
+40 CREDIT_SALE
-20 PAYMENT
```

---

# 32. Projeções

Backend pode manter projeções para consulta.

Exemplo:

```text
current_stock
current_credit_balance
```

Mas são:

```text
DERIVED DATA
```

não fonte histórica.

---

# 33. Snapshot

Depois que o histórico crescer, não será eficiente reconstruir novo aparelho desde o evento 1.

Precisamos de snapshot.

```text
StoreSnapshot
- id
- store_id
- version
- cursor
- created_at
- payload
```

---

# 34. Bootstrap

Novo aparelho chama:

```text
GET /v1/bootstrap
```

Backend retorna:

```text
store
+
latest snapshot
+
cursor
```

Depois Android busca mudanças posteriores.

---

# 35. Device Recovery

Fluxo:

```text
Novo Android
      ↓
Autenticação
      ↓
Store identificado
      ↓
Device registrado
      ↓
Bootstrap
      ↓
Snapshot
      ↓
Pull changes
      ↓
Room reconstruído
      ↓
Operação normal
```

---

# 36. Requisito crítico de recuperação

Cenário obrigatório:

```text
Device A
 ↓
opera normalmente
 ↓
sync
 ↓
é destruído
 ↓
Device B
 ↓
login
 ↓
restore
```

Resultado:

```text
dados equivalentes
```

Sem operações financeiras duplicadas.

---

# 37. Device Revocation

Endpoint conceitual:

```text
POST /v1/devices/{id}/revoke
```

Um aparelho perdido deve deixar de sincronizar.

---

# 38. Segurança multi-tenant

Toda query precisa respeitar:

```text
store_id
```

Nunca permitir:

```text
Store A
 ↓
dados Store B
```

Mesmo conhecendo IDs válidos.

---

# 39. Authorization

Não confiar em:

```text
store_id
```

apenas porque veio no body.

Validar membership autenticado.

---

# 40. API inicial

Endpoints P0:

```text
GET  /health
GET  /ready

POST /v1/auth/...
POST /v1/auth/refresh

GET  /v1/me

GET  /v1/stores
GET  /v1/stores/{id}

POST /v1/devices
GET  /v1/devices
POST /v1/devices/{id}/revoke

POST /v1/sync/push
GET  /v1/sync/changes

GET  /v1/bootstrap
```

---

# 41. Health

```text
GET /health
```

Não deve depender de PostgreSQL.

Indica:

```text
process alive
```

---

# 42. Readiness

```text
GET /ready
```

Verifica dependências necessárias.

Exemplo:

```text
database
```

---

# 43. API Error Contract

Todos os erros devem usar formato consistente.

Exemplo:

```json
{
  "error": {
    "code": "SYNC_EVENT_INVALID",
    "message": "The sync event is invalid.",
    "request_id": "..."
  }
}
```

Não expor:

* stack trace;
* SQL;
* secrets;
* detalhes internos.

---

# 44. Request ID

Toda requisição recebe:

```text
request_id
```

Propagar em logs.

---

# 45. Logging

Structured JSON logs.

Campos mínimos:

```text
timestamp
level
service
request_id
user_id
store_id
device_id
operation
result
duration_ms
```

Nunca registrar:

* token;
* senha;
* documento fiscal completo;
* dados desnecessários de clientes.

---

# 46. Audit

Operações importantes precisam produzir audit trail.

Exemplos:

```text
device.registered
device.revoked

sync.event.accepted
sync.event.rejected

snapshot.created

store.restored
```

---

# 47. Métricas iniciais

```text
http_requests_total
http_request_duration

sync_events_received
sync_events_duplicate
sync_events_rejected

sync_batch_size

devices_active

bootstrap_duration
snapshot_duration
```

---

# 48. Database

PostgreSQL.

Não usar SQLite no backend cloud.

Room/SQLite continua exclusivo do Android.

---

# 49. Migrations

Todas alterações de schema devem utilizar migrations.

Proibido:

```text
auto-create production schema
```

Sem migrations versionadas.

---

# 50. Tabelas iniciais

```text
users
stores
store_memberships

devices

sync_events
sync_cursors

store_snapshots

audit_log
```

---

# 51. Constraints

Banco deve proteger invariantes importantes.

Exemplos:

```text
UNIQUE(event_id)

UNIQUE(device.installation_id)

NOT NULL

foreign keys
```

Não confiar somente no application layer.

---

# 52. Transactions

Ingestão de evento precisa usar transação.

Conceitualmente:

```text
BEGIN

check idempotency
validate tenant
insert event
assign sequence
update projection if applicable

COMMIT
```

---

# 53. Concurrency

Dois dispositivos podem enviar eventos simultaneamente.

Backend deve assumir isso desde o primeiro dia.

Não assumir:

```text
single writer
```

---

# 54. Clock

Criar abstração:

```text
Clock
```

Evitar chamadas diretas espalhadas:

```text
now()
```

Facilita testes determinísticos.

---

# 55. Security

Obrigatório:

* TLS em produção;
* password hashing forte quando senha existir;
* token expiration;
* refresh token seguro;
* rate limiting posteriormente no boundary público;
* secrets via ambiente/secret store;
* nenhuma credencial no repositório.

---

# 56. Docker

Backend deve rodar por:

```bash
docker compose up
```

Ambiente local mínimo:

```text
api
postgres
```

Nada além disso nesta primeira etapa sem necessidade comprovada.

---

# 57. Configuração

Utilizar environment variables.

Exemplo:

```text
APP_ENV
DATABASE_URL
JWT_SECRET
ACCESS_TOKEN_TTL
REFRESH_TOKEN_TTL
LOG_LEVEL
```

Validar configuração durante bootstrap.

Configuração inválida:

```text
fail fast
```

---

# 58. OpenAPI

API deve gerar documentação OpenAPI.

Contratos de:

```text
request
response
error
```

devem ser explícitos.

---

# 59. Testes

## Unit

Domain + Application.

## Repository

PostgreSQL real/test container quando possível.

## HTTP

Endpoints.

## Sync

Idempotência.

## Tenant isolation

Store A não acessa Store B.

## Recovery

Bootstrap.

---

# 60. Teste obrigatório — Idempotência

Enviar:

```text
event A
event A
event A
```

Esperado:

```text
1 persisted event
1 effect
```

---

# 61. Teste obrigatório — Batch

Enviar:

```text
A valid
B valid
C invalid
D duplicate
```

Esperado:

```text
A ACCEPTED
B ACCEPTED
C REJECTED
D ALREADY_PROCESSED
```

---

# 62. Teste obrigatório — Isolation

Usuário de Store A tenta buscar dados de Store B.

Esperado:

```text
DENIED
```

---

# 63. Teste obrigatório — Offline Recovery

Simular:

```text
Android local
 ↓
100 eventos
 ↓
sync
 ↓
novo device
 ↓
bootstrap
```

Estado reconstruído corretamente.

---

# 64. Teste obrigatório — Retry

Simular timeout depois que servidor processou evento, mas antes do Android receber ACK.

Android reenvia.

Servidor:

```text
ALREADY_PROCESSED
```

Nenhuma duplicação.

Este caso é obrigatório.

---

# 65. Contrato Android ↔ Backend

Backend não pode depender de classes internas do Android.

Contratos HTTP devem ser independentes.

Não compartilhar diretamente:

```text
RoomEntity
DAO
ViewModel DTO
```

com API.

---

# 66. Contracts

Criar modelos explícitos:

```text
SyncPushRequest
SyncPushResponse

SyncEventEnvelope

SyncPullResponse

BootstrapResponse

DeviceRegistrationRequest
DeviceResponse
```

---

# 67. Compatibilidade

Android antigo pode continuar em produção enquanto backend evolui.

Portanto:

* contratos versionados;
* campos novos preferencialmente opcionais quando compatível;
* breaking changes exigem versão nova.

---

# 68. API Version

Base:

```text
/v1/
```

Não criar `/v2` sem mudança incompatível real.

---

# 69. Gemma

Gemma NÃO entra nesta entrega.

Arquitetura futura:

```text
TINO Android / WhatsApp
       ↓
TINO Orchestrator
       ↓
Commerce Runtime
```

Mas Gemma não deve participar da infraestrutura de sincronização.

---

# 70. Live Transcriber

Live Transcriber NÃO faz parte deste backend.

Ele permanece serviço independente.

Backend TINO não deve:

* incorporar código STT;
* conhecer engine específico;
* assumir provider específico.

---

# 71. Fiscal

Fiscal NÃO entra neste milestone.

Apenas reservar bounded context futuro:

```text
fiscal/
```

Não criar implementação vazia desnecessariamente.

---

# 72. WhatsApp

Também fica fora deste milestone.

Sync precisa estar estável primeiro.

---

# 73. ML

Não implementar ML nesta etapa.

Mas preservar Domain Events porque eles serão fonte futura para:

```text
forecast
replenishment
stockout prediction
basket analysis
```

---

# 74. Definition of Done

TINO-BACKEND-001 somente recebe PASS quando:

```text
API inicia
PostgreSQL conecta
migrations aplicam

health PASS
ready PASS

auth básico PASS
store isolation PASS

device registration PASS
device revoke PASS

sync push PASS
idempotency PASS
sync pull PASS

bootstrap PASS
recovery PASS

tests PASS
Docker PASS
OpenAPI disponível
```

---

# 75. Gate B1 — Foundation

```text
GATE B1
```

Critérios:

```text
Architecture        PASS
Database            PASS
Identity            PASS
Device              PASS
Tenant Isolation    PASS
```

Qualquer FAIL:

```text
B1 = FAIL
```

---

# 76. Gate B2 — Sync

Critérios:

```text
Push                PASS
Pull                PASS
Cursor              PASS
Batch               PASS
Idempotency         PASS
Retry Safety        PASS
```

Qualquer FAIL:

```text
B2 = FAIL
```

---

# 77. Gate B3 — Recovery

Teste:

```text
Device A
   ↓
eventos
   ↓
cloud
   ↓
Device A desaparece
   ↓
Device B
   ↓
bootstrap
```

Todos os dados recuperáveis.

Resultado:

```text
B3 = PASS
```

Se algum dado operacional for perdido:

```text
B3 = FAIL
```

---

# 78. Regra de parada

NÃO começar:

```text
WhatsApp
Fiscal
Gemma Cloud
ML
Analytics avançado
```

antes de:

```text
B1 PASS
B2 PASS
B3 PASS
```

---

# 79. Entrega esperada do agente

Ao concluir, reportar:

```text
1. estrutura criada;
2. stack efetivamente utilizada;
3. migrations;
4. tabelas;
5. endpoints;
6. contratos;
7. testes;
8. resultado de idempotência;
9. resultado de isolation;
10. resultado de recovery;
11. cobertura;
12. issues/debts encontrados;
13. B1;
14. B2;
15. B3.
```

Não declarar sucesso apenas porque:

```text
server starts
```

---

# 80. Regra final

O primeiro backend do TINO não existe para colocar lógica do comércio na cloud.

Ele existe para garantir:

> **O comerciante pode trabalhar localmente sem depender da internet.**

e ao mesmo tempo:

> **Se o celular quebrar hoje, amanhã o TINO consegue reconstruir o negócio em outro aparelho.**

Essa é a primeira responsabilidade real do backend.
