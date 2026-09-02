# TINO — Evidência CAP-001: LIST_SUPPLIERS

**Data:** 2026-08-26  
**Estado:** `IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`  
**Tipo:** leitura local, offline, sem confirmação e sem mutação.

## Caminho integrado

```text
FastIntentRouter / Gemma
  → AgentIntent
  → TinoAgentBoundary
  → ListSuppliersUseCase
  → CommerceRepository / Room
  → DbFirstReadResult.Suppliers
  → DbFirstReadA2uiMapper
  → supplier_summary
```

- O catálogo declara `suppliers.list`, fonte `SupplierRepository / Room` e
  capability `LIST_SUPPLIERS`.
- O resultado carrega somente `id`, nome e telefone vindos do Room.
- Estado vazio retorna “Nenhum fornecedor cadastrado.” sem dados fictícios.
- O telefone ausente aparece como “Sem telefone”; nenhum contato é inventado.
- A capability é disponibilizada apenas quando o perfil possui o módulo que
  oferece fornecedores.

## Regressões

- frases como “Quais fornecedores tenho?” e “Lista de fornecedores” roteiam
  sem Gemma;
- leitura real e estado vazio foram testados contra Room em memória;
- o contrato canônico aponta para `LIST_SUPPLIERS`;
- o mapper A2UI preserva nome, contato ausente, status e `actionId`;
- o codec aceita `supplier_summary`.

## Validação

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

bash tools/g3-2-smoke.sh
→ G3.2 PASS_FULL — Xiaomi 2410FPCC5G / API 36
```

A consulta de fornecedores por voz ainda não foi exercitada manualmente no
aparelho; por isso a capability não é promovida a `PASS_FULL` nesta evidência.
