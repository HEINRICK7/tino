# TINO — Evidence: Multi-Vertical Foundation

**Data:** 2026-08-24  
**Estado:** `FOUNDATION_IMPLEMENTED / PACKS_BLOCKED`  
**Pré-condição:** `M8 = PASS`

## Escopo executado

Foi implementada a fundação composicional do runtime, sem criar novos vertical
packs e sem alterar a verdade operacional do Room/Core.

## Evidências de implementação

- `BusinessProfile` mantém `BusinessVertical` como compatibilidade e passa a
  expor `OperationalPattern` e capacidades permanentes;
- `StoreProfileEntity` persiste padrões e capacidades com migration aditiva
  `15 → 16`;
- `DefaultBusinessContextResolver` deriva módulos, capabilities, vocabulário,
  analytics, ativações válidas e componentes A2UI permitidos;
- `HomeConfiguration`, Agentic Shell e navegação usam capabilities resolvidas;
- ativação `EPHEMERAL` expira por deadline e não grava alteração no perfil;
- edição de configuração preserva padrões e capacidades já persistidos.
- Home Summary agora oculta métricas de vendas/fiado quando os módulos
  correspondentes não estão ativos; Quick Queries já usa a mesma filtragem de
  capabilities.

## Validação automatizada

Comando executado:

```text
gradle :app:testDebugUnitTest --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.

## Composição de Quick Queries

O catálogo agora passa por `availableQuickQueries(allowedCapabilities)`, uma
fronteira única de filtragem antes da renderização. Isso impede que a tela
ofereça consultas de estoque, vendas ou fiado quando a capability não está no
contexto resolvido.

Regressões adicionadas:

- perfil com `LIST_CUSTOMERS` expõe somente `Meus clientes`;
- `LIST_PRODUCTS` expõe somente `Meus produtos`, sem inventar consultas de
  reposição que exigiriam `REPLENISHMENT_QUERY`.

Validação:

- `gradle :app:testDebugUnitTest --tests com.tino.app.TinoQuickQueriesTest --no-daemon`: `BUILD SUCCESSFUL`;
- `gradle :app:assembleDebug --no-daemon`: `BUILD SUCCESSFUL`;
- APK instalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com `APP_START`, sem `FATAL EXCEPTION`.

## Navegação composicional

A navegação inferior agora usa `visibleNavigationDestinations(activeCapabilities)`
como única regra de composição:

- `Hoje` e `Mais` permanecem sempre disponíveis;
- `Produtos` só aparece com `LIST_PRODUCTS`;
- `Fiado` só aparece com `LIST_RECEIVABLES`.

Regressões adicionadas em `TinoNavigationTest` cobrem perfil somente de
Clientes, capability de estoque e capability de recebíveis.

Validação adicional:

- `gradle :app:testDebugUnitTest --tests com.tino.app.TinoNavigationTest --no-daemon`: `BUILD SUCCESSFUL`;
- `gradle :app:assembleDebug --no-daemon`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com novo `APP_START`, sem `FATAL EXCEPTION`.

## Ações principais da Home

A ação `Vender` passou a exigir `NAVIGATE`, a mesma capability usada para
autorizar sua rota no Agentic Shell. Antes, ela era derivada apenas de
`READ_FINANCIAL_SUMMARY`, o que podia renderizar um card sem permitir a
navegação correspondente.

Regressão adicionada:

- perfil com apenas `READ_FINANCIAL_SUMMARY` não mostra `Vender`;
- a leitura financeira continua permitida;
- `NAVIGATE` continua ausente nesse perfil.

Validação adicional:

- `gradle :app:testDebugUnitTest --tests com.tino.app.domain.profile.BusinessProfileTest --no-daemon`: `BUILD SUCCESSFUL`;
- `gradle :app:assembleDebug --no-daemon`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com novo `APP_START`, sem `FATAL EXCEPTION`.

## Autorização de navegação do Agentic Shell

`FastNavigationTarget.requiredCapability()` passou a ser a fonte única para
autorizar rotas determinísticas de voz:

- venda exige `NAVIGATE`;
- clientes exige `LIST_CUSTOMERS`;
- produtos exige `LIST_PRODUCTS`;
- fiado exige `LIST_RECEIVABLES`;
- entrada exige `REGISTER_STOCK_ENTRY`.

Isso removeu a exceção anterior que permitia abrir Venda sem verificar o
contexto ativo. A regressão cobre o mapeamento completo dos cinco destinos.

Validação adicional:

- `gradle :app:testDebugUnitTest --tests com.tino.app.domain.agent.FastIntentRouterTest --no-daemon`: `BUILD SUCCESSFUL`;
- `gradle :app:assembleDebug --no-daemon`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com novo `APP_START`, sem `FATAL EXCEPTION`.

Validação de instalação:

- device: Xiaomi 2410FPCC5G, serial `69WOBUFENFLFGAJZ`, Android API 36;
- APK debug instalado com `adb install -r`: `Success`;
- cold start confirmado com processo `com.tino.app` ativo;
- nenhum `FATAL EXCEPTION` ou `Process: com.tino.app` foi encontrado após a inicialização.

Regressões adicionadas:

- derivação de padrão, capability e componente A2UI por perfil;
- capability temporária dentro do prazo;
- expiração da capability temporária sem mutar o perfil;
- persistência do padrão derivado no perfil do estabelecimento.

Incremento adicional validado:

- composição da Home não renderiza resumo de módulo desativado;
- `testDebugUnitTest`, `lintDebug` e `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36, cold start com processo ativo e
  sem `FATAL EXCEPTION`.

## Incremento — Catálogo de tools limitado pelo perfil

O contexto de capabilities ativas agora é encaminhado ao interpretador Gemma
quando o fallback local é necessário. O prompt e o contrato de tools são
filtrados antes da inferência:

- perfil somente de Clientes recebe `LIST_CUSTOMERS` e `customers.list`;
- tools de Produtos e Reposição não são apresentadas nesse contexto;
- o catálogo completo continua disponível apenas quando não há contexto de
  perfil, preservando o comportamento de testes/diagnósticos isolados;
- `READ_FINANCIAL_SUMMARY` passou a ter vínculo explícito com sua tool local.

Validação:

- `MediaPipeGemmaAgentIntentAdapterTest`: `BUILD SUCCESSFUL`;
- regressão confirma a ausência de `products.list` e
  `inventory.replenishment` no perfil de Clientes;
- `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com `MainActivity` em estado `Resumed` e processo ativo
  (`pid 592`).

## Incremento — Fast path respeita `NAVIGATE`

O fast path de voz para `Vender` deixou de ter uma exceção local que ignorava a
capability exigida. Ele agora consulta o mesmo contrato
`FastNavigationTarget.requiredCapability()` usado pelo restante do runtime.

Com isso, um perfil sem `NAVIGATE` recebe `CAPABILITY_DISABLED` também quando a
entrada chega pelo comando determinístico, em vez de abrir Venda por acidente.

Validação:

- `FastIntentRouterTest`: `BUILD SUCCESSFUL`;
- `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com `MainActivity` em estado `Resumed` e processo ativo
  (`pid 31492`).

## Incremento — Autorização única de navegação

A autorização de rotas foi centralizada em `TinoScreen.requiredCapability()`.
Agora a navegação direta usa a mesma declaração de capability já utilizada pelo
runtime agentic:

- venda exige `NAVIGATE`;
- produtos e detalhes exigem `LIST_PRODUCTS`;
- entrada exige `REGISTER_STOCK_ENTRY`;
- fiado e recebimentos exigem `LIST_RECEIVABLES`;
- clientes e detalhes exigem `LIST_CUSTOMERS`.

Isso elimina a lista paralela de condicionais da Activity e reduz o risco de a
Home, a voz e uma rota interna autorizarem telas diferentes para o mesmo perfil.

Validação:

- `TinoNavigationTest`: `BUILD SUCCESSFUL`;
- `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com `MainActivity` em estado `Resumed` e processo ativo
  (`pid 28162`).

## Incremento — Validação consistente no onboarding

A mesma validação de dependências foi aplicada à personalização do primeiro
acesso. O onboarding agora:

- rejeita imediatamente a remoção de `INVENTORY` enquanto `STOCK_ENTRY` estiver
  ativo;
- rejeita imediatamente a remoção de `CUSTOMERS` enquanto `CREDIT` estiver
  ativo;
- valida o perfil antes de navegar para a Home;
- mostra uma orientação acionável quando a combinação escolhida é inválida.

Validação adicional:

- `BusinessProfileTest`: `BUILD SUCCESSFUL`;
- `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36: `Success`;
- cold start confirmado com processo `com.tino.app` ativo (`pid 26814`).

## Incremento — Configuração sem falha silenciosa

A tela `Meu negócio` agora comunica erros de configuração em vez de ignorar
silenciosamente uma tentativa inválida. Isso cobre:

- remoção de `INVENTORY` enquanto `STOCK_ENTRY` ainda depende dele;
- remoção de `CUSTOMERS` enquanto `CREDIT` ainda depende dele;
- falha de validação ao salvar o perfil.

A regra continua no domínio; a UI apenas traduz a causa para uma orientação
acionável, sem alterar dados operacionais nem criar um caminho alternativo.

Validação:

- `BusinessProfileTest`: `BUILD SUCCESSFUL`;
- `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36;
- cold start confirmado com `com.tino.app/.MainActivity` em estado `Resumed` e
  processo ativo (`pidof com.tino.app`), sem `FATAL EXCEPTION` nos logs recentes.

## Limites preservados

- nenhum pack Bakery, Restaurant, Oficina, Salão ou Serviços foi criado;
- nenhum dado de produtos, clientes, estoque, vendas ou financeiro é alterado;
- a epic ainda não é `PASS`: falta validar a matriz completa de UI/Room/device e
  um fluxo integrado não varejista antes de liberar packs.

## Incremento — Recovery de capability EPHEMERAL

- capability desativada pode ser concedida somente para a sessão atual;
- a tentativa usa o mesmo Agent Runtime e não altera `BusinessProfile`/Room;
- a concessão é removida em sucesso, falha, cancelamento ou troca de tela;
- a UI apresenta `USAR UMA VEZ` quando a causa é capability inativa;
- `ATIVAR SEMPRE` só aparece para queries/navegação; mutações são recusadas
  pela política de recovery;
- regressão cobre concessão temporária e limpeza após falha.

## Incremento — Invariantes e observabilidade

- `BusinessProfileValidator` rejeita capabilities permanentes desconhecidas;
- capabilities de mutação são rejeitadas mesmo quando tentam entrar por código
  ou persistência direta;
- eventos de perfil registram padrões, contagem de capabilities e concessões
  permanentes;
- recovery `EPHEMERAL` registra capability e modo concedido no audit logger.
- capability permanente e padrão operacional sobrevivem ao fechamento e
  reabertura do Room, mantendo os módulos comerciais originais.

Validação adicional:

- `testDebugUnitTest`, `lintDebug` e `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36, cold start com processo ativo e
  sem `FATAL EXCEPTION`.

## Validação física — ativação permanente e recuperação

No Xiaomi 2410FPCC5G/API 36, sem apagar dados comerciais:

1. O perfil foi alterado temporariamente para `OTHER`, com `CORE,CUSTOMERS`.
2. A consulta de estoque foi bloqueada de forma explícita com
   `CAPABILITY_DISABLED`, oferecendo `USAR UMA VEZ` e `ATIVAR SEMPRE`.
3. `ATIVAR SEMPRE` persistiu `LIST_PRODUCTS` no perfil; o log registrou
   `profile_action=updated` e o recarregamento registrou `capability_count=3`.
4. Após force-stop e cold start, `OTHER`, `CORE,CUSTOMERS`, `GENERAL` e
   `LIST_PRODUCTS` foram restaurados do Room. A mesma consulta deixou de cair
   no bloqueio e navegou para `PRODUCTS`.
5. A nova opção de configuração `DESATIVAR LIST PRODUCTS` foi usada para
   remover a ativação permanente. O perfil do device foi restaurado para
   `RETAIL`, com os sete módulos originais, padrão `TURNOVER_COMMERCE` e
   nenhuma capability permanente.

Resultado: recuperação EPHEMERAL/PERMANENT e persistência após restart foram
comprovadas fisicamente; a remoção explícita também foi integrada para evitar
permissão permanente sem ciclo de vida reversível.

## Regressão automatizada do ciclo de vida

Foi adicionada uma regressão de domínio confirmando que:

- `OTHER + CORE + CUSTOMERS` não recebe estoque por acidente;
- `LIST_PRODUCTS` permanente compõe a capability mesmo sem o módulo INVENTORY;
- remover a capability permanente devolve o contexto ao estado original;
- CUSTOMERS permanece ativo e INVENTORY continua desativado durante a remoção.

Comando executado:

```text
gradle :app:testDebugUnitTest --tests com.tino.app.domain.profile.BusinessProfileTest --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.

Validação adicional:

- `testDebugUnitTest`, `lintDebug` e `assembleDebug`: `BUILD SUCCESSFUL`;
- APK reinstalado no Xiaomi 2410FPCC5G/API 36, cold start com processo ativo e
  sem `FATAL EXCEPTION`.
