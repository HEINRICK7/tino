# TASK G3.8 — A2UI Surface Protocol

Status: PASS_FULL  
Next task allowed: YES — G3.9

## Objetivo

Separar a decisão semântica da UI do ciclo de vida de surfaces. O composer
traduz `UiDecision` em mensagens declarativas; o host aplica mensagens
incrementalmente; o renderer Compose permanece um adapter externo.

```text
GroundedResult
    ↓
UiPlannerPort
    ↓
UiDecision
    ↓
A2uiComposerPort
    ↓
A2uiSurfaceMessage
    ↓
A2uiSurfaceHost
    ↓
Compose renderer
```

## Implementado

- `A2uiSurfaceMessage` versionada com `schema`, `version`, `messageId` e
  `surfaceId`;
- operações `CREATE_SURFACE`, `UPDATE_COMPONENTS`, `UPDATE_DATA_MODEL` e
  `DELETE_SURFACE`;
- lista plana de `A2uiSurfaceComponent` com `componentId`, `type`, `props` e
  bindings relativos ao modelo;
- `A2uiSurfaceValidator` com envelope, IDs, lifecycle e binding checks;
- `A2uiSurfaceHost` in-memory, que aplica updates sem recriar a surface;
- `A2uiComposerPort` e `DeterministicA2uiComposer` fora do domínio;
- `TinoA2UiSurfaceJsonCodec` com round-trip e fallback inerte para payload
  inválido;
- compatibilidade entre mensagens A2UI existentes e o novo surface host;
- resultado de inteligência conectado ao `TinoA2UiSurfaceHost`, reutilizando
  `surfaceId` entre resultados;
- entrada DEBUG `Mais → A2UI Surface` para validar o protocolo no aparelho sem
  depender do reconhecimento de voz;
- nenhuma ação arbitrária, navegação, mutation, Room, ADK ou Compose dentro do
  contrato/composer.

## Acceptance criteria

- [x] `CreateSurface` cria uma surface com componente flat;
- [x] `UpdateComponents` altera somente componentes identificados;
- [x] `UpdateDataModel` altera valores preservando componentes e bindings;
- [x] `DeleteSurface` remove o estado da surface;
- [x] `surfaceId` e `componentId` são estáveis e validados;
- [x] schema/version são validados antes do host aplicar a mensagem;
- [x] componente desconhecido vira fallback inerte;
- [x] codec preserva mensagens e texto escapado;
- [x] composer não importa Compose, Room, ADK, tools ou renderer;
- [x] testes unitários, lint e build verdes;
- [x] instalação incremental no device sem apagar dados;
- [x] render real de `CreateSurface` no Xiaomi 2410FPCC5G/API 36;
- [x] update incremental real via `UpdateDataModel` no mesmo `surfaceId`;
- [x] Activity permaneceu aberta sem crash fatal e Room não foi perdido.

## Evidências de execução

- `A2uiSurfaceProtocolTest`: 8 testes PASS;
- suíte Android: 285 testes, 0 falhas, 0 erros;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS;
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: `Success`;
- device: `69WOBUFENFLFGAJZ`, Xiaomi 2410FPCC5G, Android 16/API 36;
- `pidof com.tino.app` permaneceu ativo após abrir a Activity e navegar pela
  surface;
- logcat sem `FATAL EXCEPTION`, `AndroidRuntime`, `SQLiteException` ou falha de
  startup;
- evidência visual no device: surface exibiu `Entraram R$ 215,00 hoje.` e,
  após toque em `ATUALIZAR MODELO`, exibiu `Entraram R$ 300,00 hoje.` mantendo
  o layout.

## Regression guards

- `UiPlanner` continua sem produzir JSON ou conhecer renderer;
- o composer não executa ações e não acessa fatos comerciais;
- `A2uiSurfaceHost` rejeita lifecycle inválido antes de alterar estado;
- componente desconhecido nunca entra na allowlist nem vira instrução executável;
- compatibilidade com `A2uiMessage` existente permanece coberta;
- G3.9 só começa depois deste documento estar em `PASS_FULL`.
