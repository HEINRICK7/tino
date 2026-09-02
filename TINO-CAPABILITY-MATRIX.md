# TINO — Capability Matrix

**Status:** AUDIT / SOURCE OF TRUTH  
**Data:** 2026-08-20  
**Regra máxima:** `NO FACT WITHOUT TOOL`

**Atualização do checkpoint — 2026-08-26:** o caminho DB-first canônico já
possui integração automatizada para produtos, estoque, preço, clientes,
contato de cliente, recebíveis, atrasados, fornecedores e pagamento de fiado.
Nesta rodada,
`GET_CUSTOMER_CONTACT` foi promovida com `CustomerRepository / Room`, resolução
local, resultado estruturado, `customer_contact`, fast router, contrato Gemma
filtrado e regressões. A evidência está em
[TINO-EVIDENCE-CAP-001-CUSTOMER-CONTACT-2026-08-26.md](TINO-EVIDENCE-CAP-001-CUSTOMER-CONTACT-2026-08-26.md).
O checkpoint anterior de fornecedores permanece em
[TINO-EVIDENCE-CAP-001-LIST-SUPPLIERS-2026-08-26.md](TINO-EVIDENCE-CAP-001-LIST-SUPPLIERS-2026-08-26.md).
O pagamento de fiado está documentado em
[TINO-EVIDENCE-CAP-001-CREDIT-PAYMENT-2026-08-26.md](TINO-EVIDENCE-CAP-001-CREDIT-PAYMENT-2026-08-26.md).
As tabelas históricas abaixo ainda preservam a auditoria original; o estado
atual da capability deve ser lido junto desta atualização.

## Atualização — Unificação Agentic 001

Em 19/08/2026 foi criada a primeira camada de contratos canônicos. A matriz
abaixo passa a ser a referência de migração entre UI, Voice, Agent e adapters
legados. O `TinoCapabilityRegistry` declara tipo, risco, slots, offline,
confirmação, `operationId` e A2UI. Os use cases ficam em
`domain/usecase/CommerceQueries.kt`.

| Capability | Tipo | Use case canônico | Source of Truth | Risk | Offline | Confirmação | A2UI | Estado |
|---|---|---|---|---|---|---|---|---|
| `LIST_PRODUCTS` | QUERY | `ListProductsUseCase` | ProductRepository / Room | LOW | YES | NO | `product_list` | CANÔNICA |
| `GET_PRODUCT_STOCK` | QUERY | `GetProductStockUseCase` | Stock movements / InventoryProjection | LOW | YES | NO | `stock_status` | CANÔNICA |
| `GET_PRODUCT_PRICE` | QUERY | `GetProductPriceUseCase` | ProductRepository / Room | LOW | YES | NO | `product_price` | CANÔNICA |
| `LIST_RECEIVABLES` | QUERY | `ListReceivablesUseCase` | CreditProjection / Room | LOW | YES | NO | `receivables_list` | CANÔNICA |
| `LIST_OVERDUE` | QUERY | `ListOverdueUseCase` | TemporalCredit / CreditProjection | LOW | YES | NO | `overdue_list` | CANÔNICA |
| `REGISTER_CREDIT_PAYMENT` | MUTATION | `RegisterCreditPaymentUseCase` | Credit Domain / CreditLedger | HIGH | YES | YES | `payment_preview` | CANÔNICA |

Nesta primeira migração:

- o Agent Boundary de leitura usa os use cases canônicos através de
  `DbFirstReadCapabilityService`;
- a lista de produtos da UI usa `ObserveProductsUseCase`;
- o pagamento de fiado da UI e do dispatcher legado usa
  `RegisterCreditPaymentUseCase`;
- o dispatcher continua existindo somente como adapter de transição;
- as demais mutações da UI ainda precisam ser migradas gradualmente;
- `READ_RECEIVABLES` e outros IDs históricos continuam registrados para
  compatibilidade, mas não devem receber novas integrações.

**Nota de auditoria:** o topo desta matriz registra contratos canônicos
criados, enquanto as seções de implementação ainda marcam algumas dessas
capabilities como UI-only, legacy ou GAP. Essa divergência é uma pendência de
CAP-001: contrato publicado não equivale a caminho integrado validado. Até a
convergência dos dois registros, a capability deve ser tratada como `PARTIAL`,
não como pronta.

## 1. Regra arquitetural

Nenhum fato operacional pode ser mostrado ao comerciante a partir da memória ou
da resposta do Gemma.

```text
linguagem natural
    ↓
Fast Intent Router ou Gemma
    ↓
intent estruturada
    ↓
Tool Catalog
    ↓
capability segura
    ↓
Repository / Projection / Domain Service
    ↓
resultado estruturado real
    ↓
A2UI
    ↓
Compose
```

Gemma interpreta linguagem e escolhe uma capability. Ele não acessa Room, SQL,
repositories concretos, IDs, preços, estoque, saldos ou fatos do comércio.

Uma resposta factual sem uma capability que leu o estado local é inválida e
deve ser descartada.

## 2. Contratos de execução

### READ

```text
Fast Router ou Gemma
    ↓
Tool
    ↓
Repository / Projection / Domain Service
    ↓
resultado real
    ↓
A2UI
```

Não exige confirmação, não muta dados e funciona offline.

### WRITE

```text
Gemma
    ↓
intent de tool
    ↓
Entity Resolution
    ↓
preview com dados atuais do banco
    ↓
confirmação humana
    ↓
domain mutation
```

Partial transcript nunca pode criar preview definitivo nem executar mutação.
Nenhuma escrita acontece antes da confirmação.

## 3. Capabilities canônicas já implementadas

| Pergunta/ação | Capability | Fonte real | Surface | Roteamento | Estado |
|---|---|---|---|---|---|
| Quanto entrou hoje? | `READ_FINANCIAL_SUMMARY` | `FinancialProjectionRepository` | `FinancialSummaryCard` A2UI | Fast Router | IMPLEMENTED |
| Quanto entrou no PIX? | `READ_FINANCIAL_SUMMARY` | `FinancialProjectionRepository` | `FinancialSummaryCard` A2UI | Fast Router | IMPLEMENTED |
| Quanto tenho para receber? | `READ_FINANCIAL_SUMMARY` + `RECEIVABLE` | `FinancialProjectionRepository` | `FinancialSummaryCard` A2UI | Fast Router | IMPLEMENTED |
| Quanto Maria deve? | `GET_CUSTOMER_BALANCE` | `EntityResolutionService` + `TemporalCreditService` | `CustomerBalanceCard` A2UI | Fast Router | IMPLEMENTED |
| Mostra a conta da Maria | `GET_CUSTOMER_TIMELINE` | `EntityResolutionService` + `TemporalCreditService` | `CustomerTimelineCard` A2UI | Fast Router | IMPLEMENTED |
| Adicionar Café Maratá ao fiado de Maria | `ADD_CREDIT_ITEM` | `EntityResolutionService` + `CommerceToolDispatcher` + domínio de crédito | `ActionPreview` A2UI | Gemma | PREVIEW/CONFIRM IMPLEMENTED; DEVICE PROOF PENDING |

Essas capabilities são as únicas atualmente publicadas pelo
`TinoToolCatalog` canônico.

## 4. Capabilities de leitura — gaps identificados

| Pergunta | Capability proposta | Fonte real existente | Estado atual | Próximo contrato |
|---|---|---|---|---|
| O que temos de produtos cadastrados? | `LIST_PRODUCTS` | `ProductDao.observeAll()` via `CommerceRepository.observeProducts()` | UI-only | `ProductListResult` + `ProductListCard` A2UI |
| Quanto tenho de Café Maratá? | `GET_PRODUCT_STOCK` | `StockMovementDao.balance()` via `CommerceRepository.stockBalance()` | Legacy dispatcher (`CHECK_STOCK`) | resolver produto + resultado real + A2UI |
| Quanto custa Café Maratá? | `GET_PRODUCT_PRICE` | `ProductDao.findByName()` / `ProductEntity.priceCents` | Legacy dispatcher (`SEARCH_PRODUCT`) | resolver produto + preço real + A2UI |
| Quais clientes tenho? | `LIST_CUSTOMERS` | `CustomerDao.observeAll()` via `CommerceRepository.observeCustomers()` | UI-only | `CustomerListResult` + `CustomerListCard` A2UI |
| Quem está me devendo? | `LIST_RECEIVABLES` | `CreditDao.observeBalances()` / `TemporalCreditService.allCustomerTimelines()` | UI-only | projeção explícita + `ReceivablesListCard` A2UI |
| Quais clientes estão atrasados? | `LIST_OVERDUE` | `TemporalCreditService` já calcula `OVERDUE` por timeline | service disponível, tool inexistente | query por todos os clientes + `OverdueListCard` A2UI |
| Qual telefone da Maria? | `GET_CUSTOMER_CONTACT` | `EntityResolutionService` / `CustomerRepository / Room` | capability canônica, leitura local | `CustomerContactResult` + `customer_contact`, sem dado inventado |
| Quais fornecedores tenho? | `LIST_SUPPLIERS` | `SupplierDao.observeAll()` via `CommerceRepository.observeSuppliers()` | UI-only / legacy `FIND_SUPPLIER` | lista/query canônica + A2UI |

## 5. Capabilities de escrita — estado do domínio

| Ação | Capability proposta | Fato/domínio existente | Confirmação | Estado |
|---|---|---|---|---|
| Anotar item fiado | `ADD_CREDIT_ITEM` | `CreditEntry SALE` + estoque | obrigatória | canônica; prova física pendente |
| Anotar fiado por valor | `ADD_CREDIT_AMOUNT` | conceito ainda não publicado no Agent Boundary | obrigatória | GAP; não inventar `Product` |
| Registrar pagamento de fiado | `REGISTER_CREDIT_PAYMENT` | `RegisterCreditPaymentUseCase` → `CreditEntry PAYMENT` + projeção financeira | obrigatória | capability canônica; prova de voz física pendente |
| Criar cliente | `CREATE_CUSTOMER` | `CreateCustomerUseCase` → `CommerceRepository` / Room + `customer.created` | obrigatória | capability canônica; preview/confirm implementados; prova física de voz pendente |
| Alterar preço | `UPDATE_PRODUCT_PRICE` | `UpdateProductPriceUseCase` → `CommerceRepository` / Room + `product.price.changed` | obrigatória | capability canônica; preview/confirm implementados; prova física de voz pendente |
| Registrar entrada de estoque | `REGISTER_STOCK_ENTRY` | `CommerceRepository.registerStockReceipt()` | obrigatória | legacy `REGISTER_STOCK_RECEIPT`; A2UI canônica GAP |
| Registrar venda detalhada | `REGISTER_SALE` | `Sale` + `SaleItem` + estoque | obrigatória | domínio existente; fora da prioridade agentic atual |

## 6. Fontes de verdade

| Fato | Fonte autorizada |
|---|---|
| Produto, preço e unidade | `ProductDao` / `CommerceRepository` |
| Estoque | soma de `StockMovementDao` |
| Cliente e telefone | `CustomerDao` / `CommerceRepository` |
| Saldo do cliente | `CreditDao` / `TemporalCreditService` |
| Linha do tempo do fiado | `TemporalCreditService` |
| Recebido, PIX, dinheiro e maquininha | `FinancialProjectionRepository` |
| Fiado criado | projeção sobre `CreditEntry SALE` |
| Fiado recebido | projeção sobre `CreditEntry PAYMENT` |
| Fornecedor | `SupplierDao` / `CommerceRepository` |

O Gemma não é fonte de nenhum item desta tabela.

## 7. Catálogos e dívida arquitetural

### Catálogo canônico

`TinoToolCatalog` atualmente publica:

- `financial.summary`;
- `customer.balance`;
- `customer.timeline`;
- `credit.add`.

Esse é o catálogo que o Agent Boundary, o Fast Router e o futuro ADK devem
consumir.

### Dispatcher legado

`CommerceToolName` ainda contém ferramentas como `SEARCH_PRODUCT`,
`CHECK_STOCK`, `GET_TODAY_SALES`, `FIND_SUPPLIER` e mutations antigas. Elas
possuem acesso a dados reais, mas formam um adapter legado e não devem ganhar
novas regras paralelas.

Quando uma capability legada for promovida, ela deve receber:

1. identidade no `TinoToolCatalog`;
2. contrato de argumentos;
3. fonte real explícita;
4. resultado estruturado;
5. surface A2UI allowlisted;
6. teste read-only ou preview/confirm;
7. teste offline e sem hallucination.

## 8. Invariantes obrigatórias

- O LLM não fornece IDs.
- O LLM não fornece preço, saldo ou estoque.
- O LLM não responde fato operacional diretamente.
- Toda referência textual passa por Entity Resolution quando necessário.
- READ não muta banco.
- WRITE nunca executa sem confirmação.
- Partial transcript nunca executa WRITE.
- Ausência de dado gera empty state verdadeiro.
- Ambiguidade gera escolha explícita, nunca escolha inventada.
- Falha de tool não pode ser apresentada como dado factual.
- A2UI recebe somente resultado estruturado da capability.
- Queries da Home e do Agent devem reutilizar a mesma fonte de verdade.

## 9. Ordem aprovada para expansão

1. Validar fisicamente `ADD_CREDIT_ITEM` nas três frases naturais.
2. Fechar diagnóstico de latência STT.
3. Implementar `LIST_PRODUCTS` com resultado real e A2UI.
4. Implementar `GET_PRODUCT_STOCK` e `GET_PRODUCT_PRICE` canônicos.
5. Implementar `LIST_RECEIVABLES` e `LIST_OVERDUE`.
6. Promover `REGISTER_CREDIT_PAYMENT` para o Agent Boundary.
7. Promover `ADD_CREDIT_AMOUNT` somente quando o contrato de domínio for validado.
8. Avaliar ADK como orquestrador do catálogo existente.
9. Avaliar RAG somente para memória textual/histórica que não seja uma consulta estruturada.

## 10. Ficha obrigatória de qualquer capability nova

Nenhuma capability pode entrar no catálogo sem declarar explicitamente:

```text
CAPABILITY:
TYPE: READ | WRITE
SOURCE_OF_TRUTH:
RISK: LOW | MEDIUM | HIGH
ENTITY_RESOLUTION: NONE | CUSTOMER | PRODUCT | SUPPLIER | MULTIPLE
GEMMA_ALLOWED_FACTS:
GEMMA_FORBIDDEN_FACTS:
OUTPUT_RESULT:
A2UI_COMPONENT:
OFFLINE:
MUTATION: NONE | PREVIEW_ONLY | CONFIRMATION_REQUIRED
```

### Exemplo — `LIST_PRODUCTS`

```text
CAPABILITY: LIST_PRODUCTS
TYPE: READ
SOURCE_OF_TRUTH: ProductRepository / ProductDao / Room
RISK: LOW
ENTITY_RESOLUTION: NONE
GEMMA_ALLOWED_FACTS: intent e filtros textuais, se existirem
GEMMA_FORBIDDEN_FACTS: nomes, preços, unidades e estoque
OUTPUT_RESULT: ProductListResult
A2UI_COMPONENT: product_list
OFFLINE: FULL
MUTATION: NONE
```

### Exemplo — `UPDATE_PRODUCT_PRICE`

```text
CAPABILITY: UPDATE_PRODUCT_PRICE
TYPE: WRITE
SOURCE_OF_TRUTH: ProductRepository / CommerceRepository / Domain
RISK: HIGH
ENTITY_RESOLUTION: PRODUCT
GEMMA_ALLOWED_FACTS: product_ref e novo preço solicitado pelo usuário
GEMMA_FORBIDDEN_FACTS: product_id, preço atual, estoque e qualquer valor calculado
OUTPUT_RESULT: ProductPriceChangePreview / ProductPriceChangeResult
A2UI_COMPONENT: price_change_confirmation / price_change_success
OFFLINE: FULL
MUTATION: CONFIRMATION_REQUIRED
```

O `source of truth` deve ser uma fonte implementada e testável; não basta
declarar que a informação virá de um repository futuro. A ficha também deve
indicar como ausência, ambiguidade, erro de leitura e indisponibilidade offline
serão apresentados.

## 11. Gate de qualquer capability nova

Uma capability só pode ser publicada quando demonstrar:

```text
CAPABILITY_CONTRACT             PASS
REAL_SOURCE_IDENTIFIED          PASS
NO_LLM_FACTS                    PASS
ENTITY_RESOLUTION               PASS, quando aplicável
READ_ONLY ou PREVIEW_CONFIRM    PASS
A2UI_ALLOWLIST                  PASS
EMPTY_STATE                     PASS
AMBIGUITY                       PASS
OFFLINE                         PASS
NO_MUTATION_ON_FAILURE          PASS
NO_DOUBLE_COUNTING              PASS, quando financeiro
TESTS                           PASS
ASSEMBLE                        PASS
LINT                            PASS
```

## 12. Regra de encerramento

> Gemma pede ao TINO para consultar ou agir. O banco e o domínio dizem o que é
> verdade.

```text
NO FACT WITHOUT TOOL
```
