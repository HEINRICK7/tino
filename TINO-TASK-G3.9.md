# TASK G3.9 — TINO Component Catalog

Status: PASS_FULL  
Next task allowed: YES — G3.10

## Objetivo

Definir a semântica permitida da UI agentic em um catálogo versionado e
extensível. O agente escolhe tipos conhecidos e dados declarativos; não pode
inventar Compose, HTML, JS, navegação ou ações arbitrárias.

```text
CoreCatalog
     + RetailCatalogContributor
     + BakeryCatalogContributor
              ↓
       EffectiveCatalog
              ↓
       schema validation
              ↓
        SurfaceHost
              ↓
        Compose adapter
```

## Implementado

- `TinoComponentDescriptor` com grupo semântico, versão e schema de props;
- grupos `LAYOUT`, `DISPLAY`, `BUSINESS`, `INTELLIGENCE`, `INTERACTION` e
  `OPERATIONS`;
- catálogo core com componentes de layout, display, negócio, inteligência,
  interação e operações;
- `LegacyTinoComponentCatalog` preserva os cards existentes durante a
  migração;
- `TinoComponentCatalogContributor` e `TinoComponentCatalog.effective(...)`
  permitem contribuições verticais sem contaminar o core;
- detecção de colisões entre contribuidores verticais;
- `TinoComponentCatalogValidator` rejeita props não declaradas e mantém tipos
  desconhecidos como fallback inerte;
- `A2uiSurfaceHost` integrado ao catálogo para validar componentes conhecidos;
- renderer do host percorre a lista flat completa, em vez de renderizar apenas
  o primeiro componente;
- update de modelo preserva componentes não alterados;
- tela DEBUG `Mais → A2UI Surface` exercita uma surface com múltiplos grupos.

## Acceptance criteria

- [x] catálogo tipado e versionado;
- [x] allowlist central efetiva;
- [x] descriptor por componente;
- [x] schema de props e bindings por path;
- [x] tipo desconhecido não executa e renderiza fallback seguro;
- [x] props conhecidas não declaradas são rejeitadas;
- [x] registry/contribuidores não dependem de ADK, Room ou Compose;
- [x] pelo menos um componente de cada grupo core está catalogado;
- [x] renderer Compose separado do catálogo;
- [x] surface multi-componente renderizada no device;
- [x] `UpdateDataModel` preserva componentes que não mudaram;
- [x] instalação incremental, Activity estável e Room preservado.

## Evidências de execução

- `TinoComponentCatalogTest`: 7 testes PASS;
- suíte Android: 292 testes, 0 falhas, 0 erros;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS;
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: `Success`;
- device: `69WOBUFENFLFGAJZ`, Xiaomi 2410FPCC5G, Android 16/API 36;
- surface física mostrou três componentes: `Recebido hoje`, `Comparação` e
  `Fonte`;
- update físico alterou somente `Recebido hoje` de `R$ 215,00` para
  `R$ 300,00`; os outros componentes permaneceram visíveis e inalterados;
- `pidof com.tino.app` ativo após a interação;
- logcat sem `FATAL EXCEPTION`, `AndroidRuntime`, `SQLiteException` ou falha de
  startup.

## Regression guards

- Core não importa módulos verticais;
- colisões de tipos entre contribuidores são observáveis;
- allowlist não é substituída por texto vindo do ADK;
- props desconhecidas não viram ações;
- componente desconhecido continua sendo dado inerte;
- G3.10 só começa depois deste documento estar em `PASS_FULL`.
