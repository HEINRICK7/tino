# TINO — Evidências G4 — 20/08/2026

## Build automatizado

Comando executado:

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.

- testes app: 321 casos XML executados;
- lint: PASS;
- APK: `app/build/outputs/apk/debug/app-debug.apk`;
- instalação incremental: `adb install -r ...` → `Success`.

## Device

- device: Xiaomi 2410FPCC5G;
- serial: `69WOBUFENFLFGAJZ`;
- API: 36;
- processo após smoke: `com.tino.app` ativo;
- Activity: `com.tino.app/.MainActivity` em foco;
- dados não foram apagados.

## Harness físico G4

### Multi-tool + replan

```text
Cenário: Multi-tool + replan
Status: ANSWERED
Terminal: ANSWERED
Turnos: 2   Planos executados: 2   Replans: 1
Trace: T1 PLAN → T1 EXECUTE_READ → T1 OBSERVE → T1 REPLAN
       → T2 PLAN → T2 EXECUTE_READ → T2 OBSERVE → T2 FINAL
```

### Clarificação

```text
Cenário: Clarificação
Status: REQUEST_CLARIFICATION
Terminal: REQUEST_CLARIFICATION
Mensagem: Encontrei dois produtos Maratá. Escolha um para continuar.
```

### Proteção de loop

```text
Cenário: Proteção de loop
Status: PROTEGIDO
Terminal: TOOL_FAILURE
Turnos: 3   Planos executados: 2   Replans: 2
Mensagem: Interrompi o ciclo para proteger a operação.
```

## Logs

Após a instalação, abertura e execução dos três cenários, a verificação do
logcat não encontrou:

```text
FATAL EXCEPTION
ANR in
SQLiteException
no such table
database is locked
```

## Conclusão

G4 está `PASS_FULL`. O loop pode replanejar, pedir clarificação e interromper
repetições, sempre preservando validator/executor e sem tocar em mutações
comerciais. Nenhum gate posterior foi iniciado nesta rodada.

