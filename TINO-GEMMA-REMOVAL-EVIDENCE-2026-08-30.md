# TINO — Gemma Removal Evidence

Data: 2026-08-30  
Decisão: `REMOVE`

## Resultado

Gemma foi removido do produto Android e da composição de runtime. Não foi
introduzido outro LLM ou modelo generativo.

O fluxo ativo agora é:

```text
voz/transcrição independente
        ↓
Fast Router / regras / contexto local
        ↓
capabilities determinísticas
        ↓
Room e dados reais
        ↓
resposta, esclarecimento ou entrada manual segura
```

## Remoções

- asset `gemma3-1b-it-int4.task` de 554.661.243 bytes;
- MediaPipe GenAI e bindings de inferência;
- serviço/processo isolado `:gemma`, protocolo Binder/Messenger, model store,
  circuit breaker e warm-up;
- adapters de intenção, extração estruturada, orquestração e crédito ligados
  ao Gemma;
- planner/proposal ADK opcional e parser/prompt/model adapter associados;
- `VoiceViewModel`, `VoiceCommandCoordinator` e tela global legada de voz;
- smoke, testes e spike Koog exclusivos dessa integração;
- declarações de serviço/atividade debug e dependências de build.

Nenhuma migration Room ou contrato de sincronização foi alterado.

## Substituições seguras

- `AgentIntentInterpreter` agora termina explicitamente em
  `UNSUPPORTED_INTENT` quando não há uma operação determinística segura;
- o planner de intelligence é determinístico e reporta `DETERMINISTIC`;
- ASR Android continua disponível independentemente do modelo generativo;
- voz contextual mantém transcrição/edição, mas o preenchimento estruturado
  por modelo foi removido: após a fala, o usuário continua pelo formulário
  manual;
- A2UI, capabilities, Room, learning/correções, mutações protegidas e o
  fluxo agentic determinístico permanecem na composição principal.

Funcionalidades que deixam de existir deliberadamente: interpretação aberta
de frases fora do corpus determinístico e autofill contextual de múltiplos
campos por modelo local. O comportamento nesses casos é esclarecimento,
`UNSUPPORTED` ou entrada manual; nenhuma mutação é executada implicitamente.

## Evidência de build e artefato

| Medição | Antes | Depois |
|---|---:|---:|
| APK Debug | 587.330.463 bytes (~560 MiB) | 67.248.855 bytes (~64 MiB) |
| APK Release unsigned | — | 59.424.814 bytes (~56,7 MiB) |
| modelo `.task` | 554.661.243 bytes | ausente |

Comandos concluídos:

```text
gradle :app:compileDebugKotlin --no-daemon       PASS
gradle :app:testDebugUnitTest --no-daemon        PASS
gradle test --no-daemon                          PASS
gradle :app:lintDebug --no-daemon                PASS
gradle clean :app:assembleDebug --no-daemon      PASS
gradle :app:assembleRelease --no-daemon          PASS
```

Warnings observados: APIs Android/Locale depreciadas já existentes e NDK
local sem `source.properties`; nenhum warning apontou para Gemma ou para a
remoção.

Varreduras adicionais:

- `rg` no código ativo, manifests, build scripts e catálogo de versões: sem
  `Gemma`, `google.adk`, `tasks.genai`, `llminference` ou MediaPipe GenAI;
- listagem ZIP dos APKs Debug e Release: sem modelo, runtime ou nomes GenAI;
- `git diff --check`: PASS;
- `gitleaks`/`trufflehog` não estão instalados neste ambiente; a varredura
  estática local de padrões de segredo foi executada sem achados críticos.

## Evidência física

Dispositivo: `R9XW2006AWX`.

- APK Debug instalado com sucesso por ADB;
- abertura do TINO confirmada;
- somente `com.tino.app` apareceu em `ps`; nenhum processo `:gemma`;
- PSS inicial do app: 107.376 KB (~105 MiB);
- após abrir o smoke de intelligence: 133.947 KB (~131 MiB);
- consulta física `qual produto tem o menor estoque?`: `PASS`, planner
  `deterministic`, resposta `INSUFFICIENT_DATA` sem produtos cadastrados;
- consulta física não mapeada: `PASS`, planner `deterministic`, resposta
  segura `UNSUPPORTED` sem tentativa de ação.

O aparelho não foi usado para uma venda/entrada destrutiva nem havia base
seeded de comércio para validar resultado de estoque; os fluxos de mutação
continuam cobertos pela suíte automatizada existente e permanecem atrás de
preview/confirm/guardrails.

## Estado da worktree

A worktree já continha ampla quantidade de mudanças e documentos de auditoria
anteriores. Esta rodada adicionou a remoção acima, os fallbacks determinísticos
e este checkpoint; mudanças não relacionadas foram preservadas.
