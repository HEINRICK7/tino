# TINO — Evidências G5: Long-Term Business Memory

**Data:** 20/08/2026  
**Device:** Xiaomi 2410FPCC5G, API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Pacote:** `com.tino.app`

## Validação automatizada

Comando executado:

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.

- 326 testes unitários do app passaram;
- `lintDebug` passou;
- `assembleDebug` passou;
- APK gerado em `app/build/outputs/apk/debug/app-debug.apk`;
- testes focados de domínio e persistência G5 passaram;
- migration 13→14 incluída no build.

## Smoke físico

Instalação incremental:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
Success
```

Fluxo observado na tela de debug G5:

| Ação | Evidência observada |
|---|---|
| abrir G5 | `Memória restaurada do Room.` |
| registrar correção | `CANDIDATE`, `evidências 1`, `Café Maratá` |
| confirmar duas vezes | `TRUSTED`, `evidências 3` |
| contradizer | aprendizado antigo `DEMOTED`; `Café Maratá Tradicional` `CANDIDATE` |
| matar/reabrir app | registros anteriores restaurados do Room |
| remover memória | mensagem `Memória removida e mantida como histórico não resolvível.` |
| recarregar do Room | ambos os registros continuam `REMOVED` |

Após a remoção, a tela exibiu:

```text
entity alias product maraca → Café Maratá
REMOVED · confiança 0.9 · evidências 4

entity alias product maraca → Café Maratá Tradicional
REMOVED · confiança 0.9 · evidências 1
```

O quarto registro de evidência do primeiro alias veio de um toque adicional no
harness durante a navegação; isso não altera a propriedade validada: a remoção
foi persistida e os registros permaneceram não resolvíveis.

## Integridade do processo

- `adb shell pidof com.tino.app` retornou processo ativo;
- logcat final não encontrou `FATAL EXCEPTION`, `ANR in`, `SQLiteException`,
  `no such table`, `database is locked` ou erro de migration;
- nenhum dado comercial foi usado como memória durável no harness;
- a tela de debug usa apenas uma operação determinística de correção de alias.

## Resultado do gate

`G5 = PASS_FULL`.

G6 — Predictive Tools / ML — permanece `NOT_STARTED` e não foi iniciado nesta
rodada. A próxima pendência arquitetural imediata é tornar o escopo de negócio
configurável para multi-store antes de ampliar a memória persistente.
