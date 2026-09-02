# G4.4 — Gate físico seguro de mutação

**Data:** 28/08/2026  
**Device:** Samsung SM-A042M / Android 14 (API 34)  
**Serial:** `R9XW2006AWX`

## Resultado

O teste instrumentado `StockEntryMutationPhysicalTest` passou no aparelho:

```text
Starting 1 tests on SM-A042M - 14
Finished 1 tests on SM-A042M - 14
BUILD SUCCESSFUL
tests=1 failures=0 errors=0 skipped=0
```

O cenário atravessou o caminho de produção `CommerceToolDispatcher →
CommerceRepository → Room → MutationSafetyCoordinator →
MutationSafeToolExecutor` e verificou:

- a prévia não altera o estoque;
- execução direta com `confirmed=true` é rejeitada;
- confirmação com o token exato persiste a entrada e altera o saldo de 2 para
  14 unidades;
- a compra é persistida uma única vez;
- replay da mesma confirmação é rejeitado e mantém o saldo em 14.

## Limite de segurança

O banco foi criado com `Room.inMemoryDatabaseBuilder` dentro do teste. Nenhum
produto, fornecedor, estoque ou compra artificial foi criado no banco piloto
instalado no Samsung. Portanto esta evidência fecha o comportamento físico do
gate e da persistência Room, mas não autoriza nem comprova uma mutação
comercial real no banco do usuário.

## Comando e artefatos

```text
gradle :app:connectedDebugAndroidTest --no-daemon --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tino.app.domain.voice.StockEntryMutationPhysicalTest
```

- Teste: `app/src/androidTest/java/com/tino/app/domain/voice/StockEntryMutationPhysicalTest.kt`
- Resultado XML: `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A042M - 14-_app-.xml`
- Relatório: `app/build/reports/androidTests/connected/debug/index.html`
- APK usado no teste G4.4: 586.484.588 bytes, SHA-256
  `7e207c211c6c6897a394ef763c1d50f51ea81a6d7f6635c0116f628ec0d0766f`

## Estado do gate

`PASS_SAFE_PATH`: o caminho de mutação está fisicamente validado com isolamento
de dados. `PENDING_REAL_PILOT_MUTATION`: permanece necessária uma autorização
explícita, produto/fornecedor/quantidade/custo reais e conferência antes de
mutar o banco comercial instalado.
