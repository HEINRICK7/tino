# ADR-001 — Local-First e Sincronização do TINO

**Status:** Accepted
**Projeto:** TINO
**Data:** 2026-08-16
**Categoria:** Architecture / Data / Sync
**Decisão:** Local-first com sincronização resiliente para cloud

---

# 1. Contexto

O TINO é um assistente inteligente para pequenos comerciantes, inicialmente focado em mercadinhos e mercearias.

O dispositivo principal será um **Android**, frequentemente sem computador adicional.

O sistema deve continuar funcionando mesmo quando:

* a internet estiver indisponível;
* a conexão estiver instável;
* o servidor estiver temporariamente indisponível;
* o aparelho estiver offline durante parte do dia.

Ao mesmo tempo, não é aceitável que todo o histórico do negócio exista somente no aparelho.

Cenários reais incluem:

* celular quebrado;
* celular perdido;
* aparelho roubado;
* troca de celular;
* corrupção de dados;
* necessidade futura de múltiplos dispositivos;
* acesso remoto;
* WhatsApp;
* recuperação histórica;
* analytics e ML.

Portanto, o TINO será:

> **Local-first, mas nunca local-only.**

---

# 2. Decisão

O banco local será a fonte operacional imediata do dispositivo.

A cloud manterá uma réplica sincronizada dos dados importantes do estabelecimento.

Toda operação seguirá:

```text
AÇÃO DO USUÁRIO
      ↓
VALIDAÇÃO LOCAL
      ↓
TRANSAÇÃO LOCAL
      ↓
DOMAIN EVENT
      ↓
UI CONFIRMA
      ↓
SYNC QUEUE
      ↓
CLOUD
```

Nenhuma operação normal deve depender de uma resposta da cloud para ser considerada concluída localmente.

---

# 3. Regra fundamental

## Local Write First

Nunca:

```text
UI
 ↓
API
 ↓
espera servidor
 ↓
salva
 ↓
responde usuário
```

Sempre:

```text
UI
 ↓
Commerce Runtime
 ↓
SQLite / Room
 ↓
Domain Event
 ↓
responde usuário
 ↓
sincronização assíncrona
```

---

# 4. Banco local

Tecnologia principal:

```text
Room
  ↓
SQLite
```

O banco local deverá conter, inicialmente:

```text
Product
Customer
Supplier

Sale
SaleItem

InventoryItem
StockMovement

CreditAccount
CreditEntry

Purchase
PurchaseItem

FiscalDocument

DomainEvent

SyncCursor
SyncState

Device
Store
```

---

# 5. Source of Truth

A arquitetura distingue duas autoridades.

## Autoridade operacional

O dispositivo local.

Exemplo:

> João levou R$20 fiado.

A operação deve aparecer imediatamente no dispositivo.

---

## Autoridade distribuída

A cloud.

Depois da sincronização, ela mantém a versão durável e consolidada do estado.

Isso permite:

* recuperação;
* replicação;
* analytics;
* WhatsApp;
* dispositivos adicionais.

---

# 6. Domain Events

Toda mudança relevante deve produzir um evento.

Exemplos:

```text
sale.created
sale.cancelled

credit.sale.created
credit.payment.received

stock.received
stock.adjusted
stock.sold

product.created
product.updated

purchase.created

customer.created

supplier.created
```

Estrutura mínima:

```json
{
  "event_id": "uuid",
  "store_id": "uuid",
  "device_id": "uuid",
  "aggregate_id": "uuid",
  "type": "stock.received",
  "schema_version": 1,
  "occurred_at": "2026-08-16T18:00:00-03:00",
  "payload": {},
  "sync_status": "pending"
}
```

---

# 7. Identificadores

IDs nunca devem depender do servidor.

Usar IDs gerados localmente.

Preferência:

```text
UUIDv7
```

Objetivos:

* criação offline;
* ordenação temporal;
* evitar colisões;
* permitir sincronização posterior.

Entidades principais possuirão IDs globais.

Exemplo:

```text
sale_id
customer_id
product_id
stock_movement_id
credit_entry_id
event_id
device_id
store_id
```

---

# 8. Idempotência

Sincronização deve ser idempotente.

Se:

```text
evt_123
```

for enviado:

```text
1 vez
2 vezes
10 vezes
```

o efeito no servidor deve ocorrer **uma única vez**.

O servidor deve registrar:

```text
event_id
```

como chave idempotente.

---

# 9. Outbox Pattern

Alteração de domínio e criação do evento precisam ocorrer na mesma transação local.

Exemplo:

```text
BEGIN

UPDATE stock

INSERT stock_movement

INSERT domain_event

COMMIT
```

Nunca:

```text
UPDATE stock
COMMIT

↓

tentar criar evento
```

Isso poderia gerar estado local impossível de sincronizar.

---

# 10. Sync Worker

A sincronização será executada em background.

Android:

```text
WorkManager
```

Fluxo:

```text
WorkManager
    ↓
buscar eventos PENDING
    ↓
batch
    ↓
enviar
    ↓
cloud confirma
    ↓
marcar SYNCED
```

---

# 11. Estados de Sync

Cada evento deve possuir:

```text
PENDING
SYNCING
SYNCED
FAILED
CONFLICT
```

Opcionalmente:

```text
REJECTED
```

quando o servidor recusar definitivamente uma operação inválida.

---

# 12. Retry

Falhas temporárias usam retry automático.

Exemplo:

```text
tentativa
 ↓
falhou
 ↓
backoff
 ↓
nova tentativa
```

Usar exponential backoff.

Nunca bloquear operação local porque a sincronização falhou.

---

# 13. Estado visível ao usuário

A sincronização deve ser praticamente invisível.

Normal:

```text
✓ Tudo salvo
```

Offline:

```text
Sem internet
Suas informações estão salvas neste aparelho.
```

Pendente:

```text
Sincronização pendente
```

Problema grave:

```text
Não foi possível fazer backup de alguns dados.
Toque para tentar novamente.
```

Evitar terminologia técnica.

---

# 14. Pull Sync

Sincronização não será apenas:

```text
device → cloud
```

Também haverá:

```text
cloud → device
```

O dispositivo deve manter um cursor:

```text
last_sync_cursor
```

Exemplo:

```text
GET changes?cursor=abc123
```

Resposta:

```text
changes[]
next_cursor
```

O cursor somente avança após aplicação bem-sucedida das alterações.

---

# 15. Bootstrap de dispositivo

Novo dispositivo:

```text
LOGIN
 ↓
selecionar estabelecimento
 ↓
bootstrap
 ↓
snapshot
 ↓
eventos posteriores
 ↓
criar banco local
 ↓
TINO pronto
```

O dispositivo não deve baixar todo o event log histórico indefinidamente.

Preferência:

```text
SNAPSHOT
+
eventos posteriores
```

---

# 16. Recuperação

Cenário:

> celular principal quebrou.

Novo aparelho:

```text
Instala TINO
 ↓
Autentica
 ↓
Store identificado
 ↓
Baixa snapshot
 ↓
Baixa alterações recentes
 ↓
Reconstrói estado local
 ↓
Operação retomada
```

Objetivo:

> O comerciante não perde o negócio porque perdeu o aparelho.

---

# 17. Multi-device

O MVP pode começar com apenas um dispositivo principal.

Entretanto, a arquitetura deve suportar futuramente:

```text
            CLOUD
              │
       ┌──────┼──────┐
       ↓      ↓      ↓
    celular tablet celular
      dono    caixa   filho
```

Portanto nenhuma entidade deve assumir:

```text
1 store = 1 device
```

Modelo:

```text
Store
  └── Devices[]
```

---

# 18. Conflitos

Conflitos são inevitáveis em local-first multi-device.

Não utilizar cegamente:

```text
last-write-wins
```

para todos os domínios.

Cada domínio deve possuir sua própria estratégia.

---

# 19. Vendas

Venda criada é um evento imutável.

Evitar atualizar venda arbitrariamente.

Preferência:

```text
sale.created

ou

sale.cancelled
```

Cancelamento produz nova operação.

---

# 20. Estoque

Estoque não deve ser sincronizado como:

```text
quantity = 42
```

Preferência:

```text
StockMovement
```

Exemplos:

```text
+24 stock.received
-2 sale
-1 damaged
+3 adjustment
```

Estado:

```text
SUM(stock_movements)
```

Isso reduz conflitos entre dispositivos.

---

# 21. Fiado

Fiado também deve ser ledger-based.

Nunca sincronizar somente:

```text
João.balance = 120
```

Usar:

```text
+30 credit.sale
-50 credit.payment
+20 credit.sale
```

Saldo:

```text
SUM(entries)
```

Assim múltiplos dispositivos podem registrar operações sem sobrescrever saldo.

---

# 22. Compras

Compra deve possuir lifecycle próprio:

```text
draft
 ↓
ordered
 ↓
received
 ↓
completed
```

Recebimento gera:

```text
stock.received
```

Separadamente.

---

# 23. Produtos

Produto pode sofrer edição concorrente.

Campos:

```text
name
barcode
price
unit
package
```

Para MVP:

```text
server_version
updated_at
device_id
```

Conflitos raros podem ser apresentados para resolução.

---

# 24. Preço

Mudança de preço deve gerar histórico.

Não sobrescrever silenciosamente:

```text
ProductPriceChanged
```

Manter:

```text
old_price
new_price
changed_at
```

---

# 25. Cloud Backend

Backend cloud é responsável por:

* autenticação;
* identidade do estabelecimento;
* identidade do dispositivo;
* recebimento de eventos;
* idempotência;
* replicação;
* snapshots;
* recuperação;
* WhatsApp;
* integrações externas;
* analytics;
* ML central;
* administração.

---

# 26. Cloud não contém UX-critical dependency

Fluxos críticos não dependem dela.

Devem continuar offline:

```text
registrar venda
registrar fiado
receber pagamento
dar entrada em mercadoria
consultar estoque
consultar cliente
consultar fiado
consultar vendas locais
```

---

# 27. Funções que exigem conexão

Naturalmente cloud-dependent:

```text
WhatsApp
backup remoto
sincronização
consulta fiscal externa
emissão fiscal online quando necessária
atualizações
analytics global
modelos cloud
```

O aplicativo deve comunicar isso claramente.

---

# 28. Live Transcriber

Speech-to-Text pertence ao serviço Live Transcriber.

TINO consome:

```text
TranscriptEvent
```

Não duplicar STT no domínio.

---

# 29. Voz offline

A arquitetura deve permitir futuramente:

```text
Speech Runtime local
```

porém isso não é requisito obrigatório para o primeiro piloto.

Quando o Transcriber exigir conexão, funcionalidades manuais essenciais continuam disponíveis.

---

# 30. Gemma

Gemma não possui acesso direto ao banco.

Fluxo:

```text
Transcript
 ↓
Gemma
 ↓
ToolCall
 ↓
Application Service
 ↓
Domain
 ↓
Repository
 ↓
SQLite
```

Nunca:

```text
Gemma → SQL
```

---

# 31. ML

Machine Learning deve consumir dados de eventos.

Exemplo:

```text
Domain Events
     ↓
Feature Pipeline
     ↓
ML
```

Inicialmente:

```text
demand forecasting
stockout prediction
replenishment
basket analysis
customer recurrence
slow-moving inventory
```

ML não modifica domínio diretamente.

Produz:

```text
Recommendation
```

Usuário ou Runtime decide a ação.

---

# 32. Segurança local

Dados locais precisam ser protegidos.

Requisitos:

* Android Keystore;
* tokens nunca em SharedPreferences sem proteção;
* autenticação local;
* database encryption quando aplicável;
* bloquear acesso após revogação;
* minimizar dados sensíveis;
* logs sem informações financeiras desnecessárias.

---

# 33. Device Identity

Cada instalação possui:

```text
device_id
```

Cloud associa:

```text
device_id
 ↓
store_id
 ↓
user_id
```

Dispositivo pode ser:

```text
ACTIVE
REVOKED
LOST
REPLACED
```

---

# 34. Perda ou roubo

Usuário deve conseguir revogar dispositivo.

Cloud:

```text
device = REVOKED
```

Na próxima conexão:

```text
session invalid
 ↓
logout
 ↓
dados protegidos
```

---

# 35. Backup

Sincronização não deve ser confundida com backup.

Precisamos dos dois.

```text
SYNC
=
estado operacional distribuído

BACKUP
=
capacidade de recuperação histórica
```

Cloud deve manter backups independentes.

---

# 36. Schema Versioning

Eventos sempre possuem:

```text
schema_version
```

Exemplo:

```json
{
  "type": "sale.created",
  "schema_version": 1
}
```

Nunca alterar significado de evento já publicado.

Criar nova versão quando necessário.

---

# 37. Database Migrations

Room migrations precisam ser explícitas.

Nunca usar:

```text
fallbackToDestructiveMigration
```

em produção.

Perder banco local não pode ser uma estratégia de migration.

---

# 38. Observabilidade

Métricas mínimas:

```text
sync_pending_count
sync_failed_count
sync_latency
sync_conflicts
last_successful_sync
device_last_seen

events_uploaded
events_downloaded

bootstrap_duration
recovery_duration
```

---

# 39. Testes obrigatórios

## Local

```text
vender offline
registrar fiado offline
entrada de estoque offline
reiniciar aplicativo
dados continuam presentes
```

## Sync

```text
offline → online
eventos sincronizam
```

## Retry

```text
falha HTTP
 ↓
retry
 ↓
sem duplicação
```

## Idempotência

Enviar mesmo evento várias vezes.

Resultado deve ser único.

## Device Recovery

```text
device A
 ↓
dados
 ↓
cloud
 ↓
device B
```

Estado reconstruído corretamente.

## Conflict

Dois dispositivos alterando mesmo agregado.

Resultado precisa respeitar política do domínio.

---

# 40. Teste destrutivo obrigatório

Antes do piloto ser considerado válido:

1. operar o TINO normalmente;
2. registrar produtos;
3. fazer vendas;
4. registrar fiado;
5. receber mercadorias;
6. sincronizar;
7. apagar completamente o aplicativo;
8. reinstalar;
9. autenticar;
10. restaurar estabelecimento.

Resultado esperado:

> Estado operacional reconstruído corretamente.

Se falhar:

**pilot gate = FAIL.**

---

# 41. Não objetivos iniciais

Não implementar inicialmente:

* CRDT genérico;
* event sourcing completo em todos os aggregates;
* blockchain;
* Kafka;
* sincronização peer-to-peer;
* multi-region;
* infraestrutura distribuída complexa.

O sistema deve ser robusto sem ser superengenheirado.

---

# 42. Estratégia pragmática

MVP:

```text
Room
+
Outbox
+
Domain Events
+
WorkManager
+
REST Sync API
+
Cloud Database
+
Snapshots
```

Essa combinação deve ser suficiente para o primeiro estágio do TINO.

---

# 43. Regras arquiteturais

## Regra 1

UI nunca chama banco diretamente.

## Regra 2

Gemma nunca chama banco diretamente.

## Regra 3

Cloud nunca é necessária para concluir uma operação local normal.

## Regra 4

Toda operação sincronizável possui ID global.

## Regra 5

Toda mudança relevante produz Domain Event.

## Regra 6

Sync precisa ser idempotente.

## Regra 7

Estoque é baseado em movimentos.

## Regra 8

Fiado é baseado em ledger.

## Regra 9

Perda do dispositivo não pode significar perda do negócio.

## Regra 10

Falha de sincronização nunca pode resultar em duplicação financeira.

---

# 44. Critério de aceite arquitetural

A arquitetura Local-First estará validada quando o seguinte cenário funcionar:

```text
Internet desligada

↓

// operação normal por horas

20 vendas
5 fiados
3 pagamentos
2 entradas de mercadoria

↓

fechar aplicativo

↓

abrir novamente

↓

todos os dados permanecem

↓

internet retorna

↓

sincronização ocorre

↓

outro dispositivo é restaurado

↓

estado é equivalente
```

Sem duplicação.

Sem perda.

Sem intervenção técnica.

---

# 45. Consequências

## Positivas

* operação resiliente;
* excelente UX;
* funciona com internet instável;
* recuperação de dispositivo;
* prepara multi-device;
* dados adequados para ML;
* baixa latência;
* reduz dependência de infraestrutura cloud.

## Negativas

* sincronização é mais complexa;
* conflitos precisam ser tratados;
* migrations tornam-se críticas;
* testes offline são obrigatórios;
* domain events precisam de disciplina.

Aceitamos essa complexidade porque confiabilidade é requisito central do TINO.

---

# 46. Decisão final

O TINO adotará:

```text
ANDROID LOCAL-FIRST
        +
ROOM / SQLITE
        +
DOMAIN EVENTS
        +
OUTBOX
        +
WORKMANAGER
        +
CLOUD SYNC
        +
SNAPSHOTS
        +
BACKUP
```

O princípio operacional será:

> **Primeiro salva no mercadinho. Depois sincroniza com o mundo.**

E o princípio de segurança será:

> **Se o celular desaparecer amanhã, o negócio continua existindo.**
