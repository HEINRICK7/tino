# TINO — Agentic Golden Path 014: Persistência do primeiro acesso

**Status:** IMPLEMENTADO / reteste funcional no aparelho
**Tipo:** correção de produto
**Problema:** `CONTINUAR` apenas navegava para Home; o perfil do comércio não era salvo e o próximo processo voltava ao onboarding.

## Correção

- Criada a tabela Room `store_profile`.
- Adicionada migration `2 → 3`.
- O botão `CONTINUAR` grava comércio, proprietário e telefone.
- Na inicialização, o app aguarda a leitura do banco e abre Home quando existe
  perfil salvo.
- Banco existente é migrado com `MIGRATION_2_3`, sem apagar os dados atuais.

## Gates

| Gate | Evidência | Status |
|---|---|---|
| `SAVE` | Primeiro acesso grava o perfil | PASS |
| `REOPEN` | Perfil sobrevive a fechar/reabrir banco | PASS |
| `MIGRATION` | Banco v2 migra para v3 | PASS |
| `ROUTING` | Perfil existente abre Home | PASS por código/teste |
| `BUILD` | Testes, assemble e lint | PASS |
| `DEVICE_RETEST` | Sair e abrir o app no aparelho | IN_PROGRESS |

## Próxima ação

Repetir no aparelho: preencher, tocar `CONTINUAR`, fechar o app, abrir novamente
e confirmar que a Home aparece sem voltar ao primeiro acesso.
