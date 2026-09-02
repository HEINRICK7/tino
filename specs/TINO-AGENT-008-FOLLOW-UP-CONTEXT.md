# TINO — Agentic Golden Path 008: Continuidade de contexto por voz

**Status:** ACTIVE / próximo item de execução contínua
**Tipo:** checkpoint de execução
**Pré-requisito:** `specs/TINO-AGENT-007-AMBIGUITY.md`
**Objetivo:** fazer o TINO continuar uma conversa curta sem obrigar o comerciante a repetir a intenção.

## Escopo imediato

- aceitar a resolução de uma entidade ambígua mantendo quantidade, preço,
  cliente, produto e intenção já entendidos;
- aceitar correções naturais como “é o Maratá” e “o segundo” quando houver
  opções enumeradas;
- permitir cancelamento falado sem executar a operação;
- levar o mesmo padrão de voz inline para criação de cliente e fornecedor;
- manter toda mutação atrás de preview e confirmação.

## Contrato conversacional

```text
TINO: Encontrei Café Maratá e Café Pilão. Diga o nome completo.
PESSOA: É o Maratá.
TINO: Alterar preço de Café Maratá para R$ 8,75?
PESSOA: Sim.
TINO: Preço alterado.
```

Não repetir a frase original, não criar tela intermediária e não salvar durante
a resolução. Se a pessoa disser “cancela”, o contexto pendente é descartado e a
tela volta ao estado seguro.

## Gates obrigatórios

| Gate | Evidência esperada | Status |
|---|---|---|
| `FOLLOW_UP` | Correção completa a mesma chamada | PASS |
| `CHOICE_LANGUAGE` | “primeiro/segundo” e referência curta funcionam | PASS |
| `CANCEL` | Cancelamento não executa mutação | PASS |
| `INLINE_CUSTOMER` | Cliente pode ser criado por voz na tela atual | PASS |
| `INLINE_SUPPLIER` | Fornecedor pode ser criado por voz na tela atual | PASS |
| `BUILD` | Suite, assemble e lint | PASS |
| `REAL_DEVICE` | APK instalado e smoke launch; fala real ainda pendente | IN_PROGRESS |

## Regra de segurança

Nenhuma continuação pode transformar uma referência incerta em uma entidade por
aproximação silenciosa. Se ainda houver dúvida, o TINO pergunta novamente e não
altera estoque, preço, fiado ou cadastro.

## Próxima ação automática

Os painéis inline de cliente e fornecedor foram conectados às telas existentes,
com nome, celular, estados de escuta/entendimento/erro e persistência local
pelos métodos já existentes do repositório. A próxima ação é validar a fala
real no aparelho e levar o mesmo contrato para venda rápida e venda fiada.
