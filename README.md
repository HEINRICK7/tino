# TINO

Primeiro incremento Android do TINO, seguindo `specs/PRD.md` e
`specs/ADR-001.md`.

Execução contínua e gates: [`TINO-CONTINUOUS-EXECUTION.md`](TINO-CONTINUOUS-EXECUTION.md).
O contrato de sync está em
[`TINO-BACKEND-002-sync-contracts.md`](specs/TINO-BACKEND-002-sync-contracts.md).
O próximo milestone de projeções está em
[`TINO-BACKEND-003-commerce-projections.md`](specs/TINO-BACKEND-003-commerce-projections.md).
O intake fiscal está especificado em
[`TINO-BACKEND-004-fiscal-intake.md`](specs/TINO-BACKEND-004-fiscal-intake.md).
Pedidos via WhatsApp estão especificados em
[`TINO-BACKEND-005-whatsapp-orders.md`](specs/TINO-BACKEND-005-whatsapp-orders.md).
O orquestrador do TINO está especificado em
[`TINO-BACKEND-006-tino-orchestrator.md`](specs/TINO-BACKEND-006-tino-orchestrator.md).
Inteligência, features e recomendações estão especificadas em
[`TINO-BACKEND-007-intelligence-data.md`](specs/TINO-BACKEND-007-intelligence-data.md).
Hardening de produção, restore, observabilidade e gates de release estão
especificados em
[`TINO-BACKEND-008-production-hardening.md`](specs/TINO-BACKEND-008-production-hardening.md).
O procedimento de piloto real está em
[`pilot/TINO-PILOT-RUNBOOK.md`](pilot/TINO-PILOT-RUNBOOK.md).
A arquitetura real, com grafos Mermaid, está em
[`TINO-ARCHITECTURE.md`](TINO-ARCHITECTURE.md).

## Executar

O ambiente precisa apontar para o Android SDK:

```bash
ANDROID_HOME=/home/carlos-henrique/Android/Sdk \
ANDROID_SDK_ROOT=/home/carlos-henrique/Android/Sdk \
gradle :app:assembleDebug
```

Testes e lint:

```bash
ANDROID_HOME=/home/carlos-henrique/Android/Sdk \
ANDROID_SDK_ROOT=/home/carlos-henrique/Android/Sdk \
gradle :app:testDebugUnitTest :app:lintDebug
```

Validação release-like e guardrails arquiteturais:

```bash
gradle :app:testDebugUnitTest :tino-fiscal-core:test :app:lintDebug :app:assembleRelease
```

O build release-like continua sem minificação nesta fase porque o APK ainda
depende de runtimes locais (CameraX, ML Kit, Room, Hilt e MediaPipe) que devem
ganhar regras de shrink/keep específicas antes de ativar R8. Isso evita mascarar
classes removidas como se fossem uma validação de hardening.

Em uma compilação limpa, se o processo Gradle padrão de 2 GiB não for
suficiente para comprimir os assets locais, acrescente:

```bash
-Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

O arquivo local `app/src/main/assets/models/gemma3-1b-it-int4.task` não é
versionado por ser um modelo de aproximadamente 529 MB. Para executar o
fallback Gemma local, disponibilize esse asset separadamente no ambiente de
build/device.

O APK de piloto atual é identificado como `0.1.0-pilot.1` e possui
`versionCode=2`, permitindo upgrade sobre o primeiro APK sem apagar o banco.

## Implementado e testado

- Room/SQLite local-first com migration explícita.
- Produtos, estoque por movimentos, vendas, clientes, fiado por ledger,
  pagamentos, fornecedores e recebimento de mercadoria.
- Domain Events + Outbox, UUIDv7, estados de sync, retry, cursor de pull,
  snapshot e restauração de dispositivo.
- Sync REST v1 documentado em `specs/SYNC-API.md`, com HTTPS obrigatório,
  idempotência por `event_id` e double de cloud para testes.
- Contrato detalhado de eventos, schemas, retry, cursor, conflitos e
  reprocessamento em `specs/TINO-BACKEND-002-sync-contracts.md`.
- Eventos de transcrição `partial/revised/committed`, Tool Calling e
  confirmação humana.
- Roteamento global de texto/voz para vendas, estoque, preços, compras,
  produtos, clientes, fornecedores e fiado, com fallback determinístico quando
  o Gemma estiver indisponível.
- Transcrição direcionada ao runtime Gemma por `GemmaLiveTranscriber`, sem
  transcriber paralelo; fallback explícito enquanto o runtime não estiver no
  APK.
- Parser seguro de NF-e XML, drafts de pedido/WhatsApp, retirada/entrega e
  recomendações heurísticas sem mutação do domínio.
- Auditoria redigida e armazenamento de tokens via Android Keystore.
- Sync com timeout, backoff exponencial, limite de resposta, request-id e
  circuit breaker process-local contra retry storm.
- Teste de aceitação: 20 vendas, 5 fiados, 3 pagamentos, 2 entradas offline,
  reabertura e reconstrução em segundo dispositivo.

## Integrações externas

O código expõe portas para Live Transcriber, Gemma runtime, Fiscal Provider,
WhatsApp e cloud. O app usa um parser determinístico de piloto para voz e um
gateway indisponível quando `TINO_SYNC_BASE_URL` está vazio; isso mantém retry
seguro em desenvolvimento e evita declarar uma operação como sincronizada sem
um backend real.
