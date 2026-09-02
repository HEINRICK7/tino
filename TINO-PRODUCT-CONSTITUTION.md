# TINO — Constituição de Produto e Arquitetura

**Status:** APPROVED / ARCHITECTURAL_BASELINE  
**Data de congelamento:** 2026-08-23  
**Escopo:** princípios obrigatórios para evolução do TINO multi-vertical.

A especificação executável derivada está em TINO-MULTI-VERTICAL-RUNTIME-SPEC.md.

## Cadeia conceitual

BusinessType
→ Preset
→ OperationalPatterns
→ Modules
→ Capabilities
→ ContextResolver
  → Agent Context / Tool Routing
  → HomeConfiguration
  → Quick Queries
  → A2UI Catalog

Os conceitos não são intercambiáveis:

- BusinessType é linguagem de UX e ponto de entrada.
- Preset é uma configuração inicial segura e versionada.
- OperationalPattern descreve como o estabelecimento opera.
- Module agrupa um domínio funcional.
- Capability é uma ação ou leitura executável.
- ContextResolver compõe contexto, regras, ferramentas e representação.

## Invariantes

1. O TINO é um assistente operacional para pequenos negócios; não é um ERP,
   PDV ou suíte especializada completa.
2. O Core universal não conhece nichos específicos.
3. A operação real é composta por patterns combináveis, não por uma árvore
   rígida de verticais.
4. Room/Core é a verdade operacional. Memória ajuda interpretação, mas nunca
   substitui o estado canônico.
5. A IA interpreta; o Core determinístico valida, calcula e persiste.
6. Descoberta não é ativação permanente.
7. Mutações financeiras e transacionais sempre passam por review e confirmação.
8. Histórico confirmado é imutável; correções usam eventos ou ajustes auditáveis.
9. Perguntar é o último recurso, depois de contexto, catálogo e dados confiáveis.
10. A2UI materializa intenção e resultado estruturado; não é decoração nem layout
    inventado pela LLM.
11. A Home é uma projeção contextual, não uma grade universal fixa.
12. Proatividade só usa fatos determinísticos e ações explícitas.
13. Nenhum fluxo pode permanecer em loading indefinido.
14. Toda intenção termina em RESPONDER, PROPOR, PERGUNTAR ou RECUPERAR dentro de
    um deadline.
15. Novos nichos devem ser adicionados prioritariamente por composição.

## Limites congelados

- Não criar RestaurantHome, WorkshopHome ou equivalentes.
- Não espalhar condicionais de BusinessType pela UI ou pelo Agent Runtime.
- Não criar novos vertical packs antes da validação do runtime composicional.
- Não exigir configuração antecipada quando a informação puder ser descoberta
  com segurança no contexto de uso.
- Não ativar permanentemente um pattern ou capability sem consentimento explícito.

Qualquer alteração a estes princípios exige nova decisão arquitetural registrada;
implementações não podem reinterpretá-los silenciosamente.
