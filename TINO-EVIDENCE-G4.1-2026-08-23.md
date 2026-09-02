# TINO — Evidência G4.1: rota financeira encaminhada para ProductPicker

**Data:** 23/08/2026  
**Estado:** `FAIL_MANUAL_REPRODUCED / BLOCKED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1`  
**Evidência complementar:** `/tmp/tino-g4-1-20260823-physical/`

## Cenário

Entrada financeira solicitada:

```text
Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?
```

O card de revisão exibiu a transcrição financeira, com referências a “recebi
hoje”, “Pix”, “dinheiro” e “receber”. Não havia nome de produto na frase.

## Resultado observado

Após o envio, o TINO exibiu:

```text
Qual produto?
Escolha uma opção.
```

com as opções:

- Leite em pó LeiteBom;
- Leite em pó Piracanjuba;
- Café Maratá.

Nenhum produto foi selecionado e a operação foi interrompida.

## Resultado esperado

Executar `READ_FINANCIAL_SUMMARY`/consulta financeira, apresentando os valores
recebidos hoje por Pix e dinheiro e o valor ainda a receber. A operação não
deveria iniciar resolução de entidade `Product`.

## Evidência do runtime

O logcat registra, na execução física:

```text
ROUTING_COMPLETED route=global fast_path=false
ENTITY_RESOLUTION_STARTED entity_type=product candidate_count=7
ENTITY_RESOLUTION_AMBIGUOUS entity_type=product candidate_count=3 match_strategy=fuzzy
QUERY_COMPLETED duration_ms=378..486 fast_path=false
RENDERED
```

Portanto, a resposta foi rápida, porém semanticamente incorreta: o runtime
consultou produtos e preparou `ProductPicker` em vez de encaminhar a intenção
financeira para `READ_FINANCIAL_SUMMARY`. O problema reproduzido é de
classificação/roteamento de intenção e capability, não de disponibilidade do
Room nem de timeout.

## Decisão do gate

- `G4.1` não pode ser promovido a `PASS_FULL`;
- os demais cenários manuais foram interrompidos conforme o protocolo;
- nenhum produto foi selecionado para mascarar ou continuar a falha;
- não houve alteração de código nesta execução;
- Multi-Vertical, M1–M8 e G6 continuam bloqueados.

## Próxima ação autorizada

Corrigir especificamente a rota da consulta financeira, adicionar regressão
para a frase acima e retestar no device. Não iniciar refactor geral nem outros
gates antes de o cenário corrigido passar.
