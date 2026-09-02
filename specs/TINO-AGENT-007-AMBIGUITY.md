# TINO — Agentic Golden Path 007: Esclarecimento de ambiguidade

**Status:** IMPLEMENTADO / validação real contínua
**Tipo:** checkpoint de execução
**Pré-requisito:** `specs/TINO-AGENT-006-STOCK-RECEIPT.md`
**Objetivo:** nunca escolher silenciosamente um produto ou cliente incerto

## Problema

Frases como:

```text
"Muda o Maratá para oito e setenta e cinco."
"João levou dois cafés fiado."
```

podem não identificar uma única entidade. O TINO deve pedir somente o dado que
falta, preservar a intenção e não reiniciar a operação.

## Fluxo esperado

```text
fala
  ↓
intent + referência parcial
  ↓
busca local
  ↓
0 resultados → oferecer cadastro/fallback
1 resultado  → continuar
2+ resultados → AMBIGUOUS
  ↓
mostrar opções com nome e detalhe útil
  ↓
usuário escolhe/fala a correção
  ↓
retomar a mesma operação
```

## Regras

- Nunca usar o primeiro resultado arbitrariamente.
- Não enviar JSON, nome de tool ou detalhes internos ao comerciante.
- Preservar produto, cliente, quantidade e intenção já reconhecidos.
- Perguntar somente: `Qual deles?` ou equivalente concreto.
- Cancelar a operação deve limpar o contexto pendente.
- A confirmação continua obrigatória depois que a entidade for resolvida.

## Gates

| Gate | Evidência esperada | Status |
|---|---|---|
| `NO_SILENT_MATCH` | Nenhum primeiro resultado arbitrário | PASS |
| `AMBIGUOUS_SURFACE` | Opções encontradas aparecem em linguagem do comerciante | PASS |
| `CONTEXT_PRESERVED` | A intenção original e o argumento ambíguo ficam pendentes | PASS |
| `RECOVERY` | “DIZER O NOME COMPLETO” retoma a mesma operação | PASS |
| `BUILD` | `testDebugUnitTest`, `assembleDebug` e `lintDebug` | PASS |
| `REAL_DEVICE` | Instalação e smoke launch | PASS |

## Próximo passo

Boundary de resolução criado em `EntityResolution.kt`, cobrindo produto,
cliente e fornecedor sem alterar as entidades nem o contrato de sincronização.
O coordenador guarda a chamada pendente, substitui somente a referência
esclarecida e volta ao preview/resultado correto. O próximo checkpoint é ampliar
esse contexto para correções faladas como “o primeiro”, cancelamento explícito e
criação por voz de cliente/fornecedor.
