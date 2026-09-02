# TINO — Evidência CAP-001: GET_CUSTOMER_CONTACT

**Data:** 2026-08-26  
**Estado:** `IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`  
**Tipo:** leitura local, offline, sem confirmação e sem mutação.

## Caminho integrado

```text
FastIntentRouter / Gemma
  → AgentIntent
  → TinoAgentBoundary
  → EntityResolutionService
  → CustomerRepository / Room
  → DbFirstReadResult.CustomerContact
  → DbFirstReadA2uiMapper
  → customer_contact
```

- A referência textual do cliente é resolvida localmente; o modelo não fornece
  ID, telefone ou qualquer fato operacional.
- O resultado carrega nome, ID e telefone persistidos no Room.
- Nome ambíguo retorna escolha de entidade; nome inexistente retorna mensagem
  explícita.
- Telefone ausente aparece como “Sem telefone” e “Nenhum telefone cadastrado”.
- O status intermediário da consulta identifica contato, sem misturá-lo com a
  caderneta/fiado.
- O catálogo declara `customer.contact`, fonte `CustomerRepository / Room` e
  capability `GET_CUSTOMER_CONTACT`.

## Regressões

- frases como “Qual é o telefone da Maria Lina?” roteiam sem Gemma;
- telefone presente, telefone ausente e nome ambíguo foram testados contra Room
  em memória;
- o contrato canônico aponta para `CUSTOMER_CONTACT`;
- o mapper e o codec A2UI preservam o estado seguro da consulta.

## Validação

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

bash tools/g3-2-smoke.sh
→ G3.2 PASS_FULL — Xiaomi 2410FPCC5G / API 36
```

A consulta por voz ainda não foi exercitada manualmente no aparelho; por isso a
capability permanece pendente de validação física específica, apesar dos gates
automatizados e do smoke de inicialização estarem verdes.
