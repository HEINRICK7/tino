# TINO — Evidência M6: Incremental A2UI

**Data:** 2026-08-24  
**Estado:** `M6 = PASS`  
**Próximo gate:** `M7 = BLOCKED_BY_M6`  
**Escopo:** envelope incremental A2UI, host, renderer e terminalidade; M7–M8 não foram iniciados nesta execução.

## Resultado

M6 foi fechado com a cadeia:

```text
SurfaceMessage versionada
  → validação de envelope/catalogo
  → aplicação monotônica por surface
  → atualização seletiva de componentes/modelo
  → renderer determinístico
  → evento final explícito
```

## Proteções comprovadas

- schema e versão continuam obrigatórios;
- `messageId` repetido é idempotente;
- revisões explícitas antigas ou repetidas são rejeitadas;
- mensagens legadas com `sequence=0` recebem revisão interna monotônica sem
  colidir com o primeiro patch explicitamente numerado;
- `UPDATE_COMPONENTS` preserva componentes não alterados;
- `UPDATE_DATA_MODEL` atualiza dados sem recriar a árvore de componentes;
- `isFinal=true` marca a surface como terminal e patches posteriores são
  rejeitados;
- exclusão continua permitida após terminalidade;
- codec JSON preserva o marcador final;
- patch inválido não apaga a última surface válida na camada Compose;
- componentes fora do catálogo permanecem inertes e renderizam fallback seguro;
- o renderer não executa ações: apenas encaminha eventos declarativos.

## Testes executados

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.interfaceadapter.a2ui.A2uiSurfaceProtocolTest \
  --tests com.tino.app.domain.agent.AgentRuntimeModulesTest \
  --no-daemon
→ BUILD SUCCESSFUL

gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
→ BUILD SUCCESSFUL
```

APK e startup físico:

```text
adb -s 69WOBUFENFLFGAJZ install -r app/build/outputs/apk/debug/app-debug.apk
→ Success

Device: Xiaomi 2410FPCC5G / API 36
Processo com.tino.app após cold start: ativo
FATAL EXCEPTION / AndroidRuntime: nenhum registro
```

## Decisão de gate

`M6 = PASS` com contrato incremental, host, renderer, codec, regressões,
suíte completa, lint, APK e cold start verdes. `M7` permanece bloqueado e não
foi iniciado.
