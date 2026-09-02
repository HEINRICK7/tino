# TINO-BACKEND-008 — Production Hardening

**Status:** Draft implementável; hardening seguro do cliente aplicado
**Pré-requisitos:** B001, B002 e B003
**Objetivo:** definir os controles que tornam o TINO operável sob falhas de
rede, indisponibilidade cloud, crescimento de dados, restauração e releases.

Este documento não declara a cloud pronta. O workspace contém o cliente
Android e contratos; a implementação de API, banco cloud, observabilidade
central e pipeline de deploy continuam dependências externas.

## 1. Princípios operacionais

- local-first continua sendo o caminho normal do comércio;
- uma falha cloud não pode apagar nem confirmar falsamente uma operação local;
- toda integração remota tem limite, timeout, retry controlado e correlação;
- deploy e ativação de funcionalidade são operações separáveis;
- backup só é considerado válido depois de um restore exercitado;
- logs operacionais não carregam token, transcript, telefone, endereço ou XML
  fiscal bruto.

## 2. Threat model e segurança

### Ativos

```text
tokens de sessão
identidade store_id/device_id
eventos de venda, estoque e fiado
dados de cliente/fornecedor
documentos fiscais e pedidos
```

### Controles obrigatórios

- HTTPS obrigatório no `RestSyncGateway`; rejeitar `http://` e URL vazia em
  builds de produção;
- token em Android Keystore, nunca em log ou payload de auditoria;
- `Authorization: Bearer` somente quando houver sessão válida;
- `event_id` como chave de idempotência no servidor;
- autorização por `store_id` no servidor, sem confiar em `device_id` enviado pelo
  cliente;
- validação de schema, tamanho, timestamp e tipos no gateway;
- rate limit por loja/dispositivo para push, pull, webhook e comandos de voz;
- XML fiscal e mídia recebida armazenados com criptografia, retenção limitada e
  acesso auditado;
- backup Android desabilitado para não copiar dados comerciais sem controle.

O cliente já aplica Keystore, `allowBackup=false`, HTTPS obrigatório e auditoria
com chaves permitidas. A autenticação real depende de um fluxo de sessão e da
API cloud; o adapter apenas anexa o bearer disponível, sem inventar credenciais.

## 3. Limites, timeout e retry

### Cliente Android

- connect timeout: 10 s;
- read timeout: 15 s;
- pull limitado a 100 eventos por lote;
- resposta limitada a 1 MB antes do parse;
- `X-Request-Id` novo por chamada;
- WorkManager com rede conectada e backoff exponencial inicial de 30 s;
- `ExistingWorkPolicy.KEEP` evita duplicar workers concorrentes;
- falha temporária mantém o evento `FAILED` para recuperação posterior;
- rejeição permanente fica `REJECTED` e não entra em loop;
- evento remoto desconhecido fica `BLOCKED`, enquanto o cursor avança.

### Cloud obrigatória

- deadline propagado entre gateway, API e banco;
- retry somente para erros transitórios, com jitter e orçamento por loja;
- circuit breaker por dependência crítica;
- bulkhead para separar sync, webhook/WhatsApp, fiscal e tarefas analíticas;
- limite de paginação e tamanho de payload em todos os endpoints;
- fila com DLQ para mensagens que excederem tentativas;
- `Retry-After` respeitado quando o servidor limitar carga.

Não fazer retry automático de `401`, `403`, schema inválido, conflito não
resolvido ou payload excedendo limite.

## 4. Observabilidade

### Eventos estruturados

Cada tentativa deve poder ser correlacionada por:

```text
request_id
trace_id (quando houver cloud)
store_id_hash
device_id_hash
operation
event_count
duration_ms
result
error_code
```

Nunca registrar token, nome/telefone de pessoa, texto de voz, endereço,
conteúdo de XML ou payload comercial completo.

### Métricas mínimas

```text
sync_push_rate / sync_pull_rate
sync_success_rate / sync_failure_rate
sync_latency_p50_p95_p99
outbox_pending_count / outbox_oldest_age
events_rejected_count / events_blocked_count
cursor_lag
webhook_duplicate_rate
fiscal_parse_failure_rate
```

Alertar por sintomas do usuário: erro/latência, outbox envelhecida, cursor
parado e crescimento da DLQ. CPU e memória são sinais auxiliares, não o único
critério de saúde.

### Health checks cloud

- shallow: processo responde;
- deep: banco, fila, armazenamento fiscal e dependências críticas respondem;
- readiness não deve receber tráfego quando o banco ou o event store estiverem
  indisponíveis;
- health check não pode mutar dados de negócio.

## 5. Dados, retenção e restore

- eventos são append-only no event store;
- outbox local sincronizada pode ser retida conforme política de produto, mas
  eventos não sincronizados nunca são purgados automaticamente;
- eventos `SYNCED` antigos podem ser compactados apenas após snapshot verificável;
- `FAILED`, `REJECTED`, `BLOCKED` e `CONFLICT` têm retenção operacional e ação
  explícita de reprocessar, corrigir ou arquivar;
- snapshots precisam conter posição de cursor e versão de schema;
- restore deve reconstruir projeções e validar contagens, saldos, estoque e
  idempotência;
- backup cifrado, com acesso mínimo, teste periódico e registro do resultado;
- apagar dados pessoais exige regra de retenção/apagamento compatível com o
  contrato legal do produto, sem destruir evidência financeira necessária.

## 6. Deploy, migração e rollback

- usar migração expand-contract: adicionar compatibilidade, migrar/backfill,
  mudar consumidores e só então remover legado;
- deploy cloud rolling, blue-green ou canary com readiness profundo;
- ativar funcionalidades por flag quando houver risco operacional;
- rollback de aplicação precisa ser mais rápido que roll-forward;
- schema de eventos é compatível por versão; não reutilizar significado de um
  `event_type` existente;
- qualquer mudança de contrato atualiza B002 e cria teste de compatibilidade;
- release Android deve publicar APK assinado, versão, hash e resultado dos
  gates.

## 7. Testes de resiliência

Antes de produção, executar em ambiente controlado:

1. carga esperada, pico de 2x e soak;
2. cloud lenta, timeout, 429, 401, 403 e 5xx;
3. duplicação de push/webhook e reprocessamento de pull;
4. perda de processo, reinício durante transação e falta de espaço;
5. restore a partir de backup e reconstrução de projeções;
6. evento desconhecido, schema inválido e conflito entre dispositivos;
7. experimento de falha autorizado, com hipótese, blast radius e rollback.

## 8. Quick diagnostic Release It!

Situação atual do conjunto Android + contratos: **2/8**.

| Controle | Estado | Próxima ação |
|---|---|---|
| timeout outbound | PASS | manter 10/15 s e testar p95 |
| circuit breaker | PASS parcial | cliente possui breaker process-local; cloud ainda precisa de breaker por dependência |
| bulkhead | PENDENTE | separar pools/filas por dependência na cloud |
| deploy sem downtime | PENDENTE | pipeline rolling/blue-green/canary |
| deep health check | PENDENTE | endpoints de readiness cloud |
| telemetry correlacionada | PARCIAL | cliente envia request id; centralizar métricas/traces |
| carga além do pico | PENDENTE | executar carga 2x e soak com evidência |
| failure injection | PENDENTE | GameDay controlado em staging |

## 9. Gate de aceite do B008

- [x] HTTPS, Keystore, backup Android desabilitado e auditoria redigida;
- [x] timeouts, limite de resposta, paginação, request id e backoff no cliente;
- [x] falhas/rejeições/quarentena têm estados persistidos;
- [ ] autenticação cloud real e autorização por loja;
- [x] circuit breaker process-local no adapter Android;
- [ ] circuit breaker cloud por dependência, bulkhead, DLQ e health checks;
- [ ] política aprovada de retenção, backup cifrado e restore exercitado;
- [ ] observabilidade central com SLI/SLO e alertas;
- [ ] carga, soak e falha injetada em ambiente controlado;
- [ ] deploy/rollback e migração expand-contract documentados e testados.

**Conclusão atual:** hardening do adapter Android aplicado e validado; B008
permanece aberto até a infraestrutura cloud e os gates operacionais existirem.

Validação executada em 2026-08-17:

```text
gradle :app:testDebugUnitTest :app:assembleDebug  PASS
gradle :app:lintDebug                              PASS
adb install -r app/build/outputs/apk/debug/app-debug.apk PASS
```

O teste `SyncCircuitBreakerTest` valida a transição closed → open → half-open
→ closed e impede probes concorrentes durante a recuperação.
