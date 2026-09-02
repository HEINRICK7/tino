# TINO — Continuous Development Execution Mode

**Status:** ativo
**Escopo:** Android, backend, contratos, UX/UI e documentação técnica do TINO
**Checkpoint executável:** G4.1 `PASS_FULL`; G3.2 está em
`PASS_FULL` após o smoke físico — ver `TINO-TASK-G3.2.md`,
`TINO-TASK-G3.3.md` e `TINO-INCOMPLETE-VALIDATION-BACKLOG.md`.
**Device de validação:** qualquer device autorizado e disponível; cada sessão
deve registrar o aparelho usado.
O protocolo manual reproduziu e corrigiu falhas de roteamento, continuidade,
learning e composição A2UI; as falhas originais e os retestes físicos estão preservados em
`TINO-EVIDENCE-G4.1-2026-08-23.md` e
`TINO-EVIDENCE-G4.1-2026-08-23-FIX-READY.md`/
`TINO-EVIDENCE-G4.1-2026-08-23-RETEST.md`,
`TINO-EVIDENCE-G4.1-2026-08-23-LEARNING-FIX-READY.md` e
`TINO-EVIDENCE-G4.1-2026-08-23-FINAL-PASS.md`. G4.1 está fechado; M1 aguarda
autorização explícita e não é iniciado automaticamente.

O ciclo posterior está congelado em [TINO-AGENTIC-RUNTIME-MODULES.md](TINO-AGENTIC-RUNTIME-MODULES.md).
Com autorização explícita, M1, M2, M3, M4, M5, M6, M7 e M8 foram fechados como
`PASS`; a fundação Multi-Vertical está em execução composicional. Novos packs e
G6 permanecem bloqueados até os critérios dessa fundação serem comprovados.

**Atualização do checkpoint — 2026-08-26:** a implementação de G6.1 —
Predictive Replenishment Baseline — foi iniciada e recebeu persistência Room,
decisão na Home, métricas de outcome, controle de qualidade das features e
expiração. Os gates automatizados e o smoke de startup passaram; a evidência
está em [TINO-EVIDENCE-G6.1-2026-08-26.md](TINO-EVIDENCE-G6.1-2026-08-26.md).
G6.1 ainda não é `PASS_FULL`: falta validar no aparelho a interação de
aceitar/rejeitar recomendações e seus outcomes. As menções posteriores a
“G6 não iniciada” neste documento são checkpoints históricos anteriores a esta
atualização.
**Fonte de verdade:** documentos oficiais do projeto, nesta ordem:

1. `specs/PRD.md`
2. `specs/ADR-001.md`
3. `specs/TINO-ARCHITECTURE.md`
4. especificações de backend
5. `specs/TINO-ZERO-DOUBT-UX.md`
6. `specs/ux/ui.md`
7. `AGENTS.md`, quando existir
8. código existente
9. testes existentes

## Regra de execução

Trabalhar em modo contínuo enquanto houver trabalho definido nos documentos,
a próxima ação não exigir uma decisão de produto não documentada, não houver
risco de perda de dados, não houver conflito de requisitos e os gates técnicos
continuarem passando.

O fluxo padrão é:

```text
ler escopo
  ↓
planejar milestone
  ↓
inspecionar implementação
  ↓
implementar
  ↓
testar
  ↓
corrigir regressões
  ↓
validar gates
  ↓
documentar resultado
  ↓
seguir para o próximo item elegível
```

Não parar para pedir confirmação entre pequenas decisões já cobertas pelos
documentos. A conclusão de um item não encerra automaticamente o trabalho se
existir outro item elegível e seguro na sequência.

Quando o checkpoint atual estiver `FAIL`, nenhuma task posterior pode ser
iniciada. Ausência temporária de device deve ser registrada como
`PENDING_DEVICE_VALIDATION` quando todos os critérios automatizáveis estiverem
verdes; ela não bloqueia o desenvolvimento das tasks seguintes.

## Autonomia permitida

O agente pode decidir sem confirmação adicional:

- nomes internos e organização de arquivos dentro da arquitetura aprovada;
- extração de componentes e pequenos refactors;
- testes, fixtures, mocks e dados de preview;
- ajustes de lint, typing e tratamento de erros;
- logs e documentação técnica;
- migrations necessárias que preservem os dados existentes;
- correções encontradas durante a execução;
- escolha entre implementações equivalentes que não alterem o contrato.

## Incremento concluído — G3.11 Mutation Safety & Confirmation

- operações `PENDING`, `EXECUTING` e `COMMITTED` são persistidas no Room pela
  migration 12→13;
- `confirm_operation` A2UI atravessa `MutationConfirmationPort` e nunca acessa
  Room pelo renderer;
- o harness físico comprovou confirmação, cancelamento, replay, token cruzado,
  stale fingerprint e restauração após restart no Xiaomi/API 36;
- 314 testes do app, lint, APK e startup físico passaram;
- G3.11 está em `PASS_FULL`; G3.12 também foi fechada em `PASS_FULL`.

## Incremento concluído — G3.12 Working & Session Memory

- Working Memory e Session Memory têm contratos e TTLs independentes;
- Room restaura entidade, objetivo, surface, rascunho e clarificação sem
  substituir dados comerciais;
- texto, voz e A2UI compartilham o `TinoAgentSession`;
- o harness físico comprovou seed, restart e limpeza seletiva no Xiaomi;
- 318 testes do app, lint, APK e smoke físico passaram;
- evidências em [TINO-EVIDENCE-G3.12-2026-08-20.md](TINO-EVIDENCE-G3.12-2026-08-20.md).

## Incremento concluído — G4 ADK Autonomous Loop

- `AdkAgentRuntime` coordena o ciclo usando `PlannerPort`, sem entregar
  execução ao ADK;
- `PlanValidator`, `PlanExecutor`, Mutation Safety, Room, memória e A2UI
  permaneceram nas fronteiras existentes;
- limites de tools, replans, repetição e timeout são observáveis;
- 321 testes app, lint, APK e smoke físico passaram;
- o Xiaomi comprovou multi-tool + replan, clarificação e proteção de loop;
- evidências em [TINO-EVIDENCE-G4-2026-08-20.md](TINO-EVIDENCE-G4-2026-08-20.md).

## Incremento concluído — G5 Long-Term Business Memory

- Business Memory tem porta de domínio, policy, provenance, confidence e
  lifecycle explícitos;
- a policy rejeita saldo, estoque, preço, Pix, pagamento e outros fatos
  transacionais;
- Room 13→14 persiste candidatos, aprendizados, contradições e remoções;
- `CommerceContextMemory` restaura apenas aliases `LEARNED`/`TRUSTED` para
  auxiliar interpretação;
- 326 testes app, lint, APK e smoke físico passaram;
- Xiaomi/API 36 comprovou promoção, contradição, restart, remoção e recarga;
- evidências em [TINO-EVIDENCE-G5-2026-08-20.md](TINO-EVIDENCE-G5-2026-08-20.md).

G6 — Predictive Tools / ML permanece `NOT_STARTED`. Não iniciar outro gate sem
uma task e critérios de pronto explícitos.

## Incremento extraordinário — G4.1 Voice Reliability & Crash Recovery

- `TranscriptCommitGate` separa parcial/revisada de committed;
- somente `TranscriptReview` com ação explícita `Enviar` entra no agente;
- editor, continuação, cancelamento e captura de correção foram adicionados;
- 329 testes, lint e APK passaram;
- `tools/g4-1-crash-capture.sh` prepara logcat/exit-info para reprodução;
- o Xiaomi executou historicamente uma chamada Gemma real pelo processo
  isolado e retornou `GENERATED OK`; no Samsung, a indisponibilidade do modelo
  e o fallback após queda controlada foram comprovados sem crash novo; as
  falhas históricas de inventário global, continuidade de venda e navegação de
  “Abrir clientes” têm correções aplicadas; fast paths, continuidade, timeout e
  instrumentação ponta a ponta (`VOICE_COMMITTED` até `RENDERED`) foram
  corrigidos e aguardam reteste físico;
- G4.1 está em `FIX_READY_FOR_DEVICE_RETEST`: a falha física de learning foi
  corrigida com materialização de `CorrectionEvent` somente após execução
  bem-sucedida; regressões positivas e negativas passaram. O mesmo cenário
  `Maracá → Maratá` ainda precisa ser retestado fisicamente.
- G6 permanece bloqueada até a validação física e o diagnóstico do crash.

## HUMAN GATE

Parar e solicitar decisão apenas quando ocorrer uma destas situações:

1. dois requisitos oficiais entrarem em conflito;
2. a operação puder apagar, sobrescrever ou perder dados reais;
3. for necessário escolher fornecedor pago ou serviço externo não aprovado;
4. surgir decisão fiscal ou legal sem regra definida;
5. for necessária uma credencial indisponível;
6. houver mudança incompatível de contrato público;
7. a próxima ação ampliar o escopo do produto;
8. existir falha estrutural que exija mudar uma decisão arquitetural;
9. um gate obrigatório continuar `FAIL` após tentativas razoáveis de correção.

Warnings, lint corrigível, testes quebrados pela própria alteração, refactors,
criação de arquivos, decisões de nomenclatura e pequenas diferenças visuais
cobertas pela especificação não são HUMAN GATE.

## Gates por milestone

### Gate técnico

- build da área alterada passa;
- testes relacionados passam;
- lint passa;
- nenhum contrato público ou migração é alterado sem documentação;
- operações local-first continuam escrevendo localmente antes do sync.

### Gate de contrato

- payloads seguem a especificação vigente;
- eventos são versionados;
- idempotência e reprocessamento estão definidos;
- eventos desconhecidos não quebram o lote inteiro;
- falhas são observáveis e recuperáveis.

### Gate UX/UI

- visual fidelity;
- navigation safety;
- state completeness.

Uma tela não é `DONE` apenas porque abriu ou porque o happy path funcionou.

### Gate de produto

- estado normal e vazio tratados;
- erro recuperável e offline tratados quando aplicável;
- sucesso contextualizado;
- confirmação antes de mutação irreversível;
- retorno previsível sem perda de intenção.

## Ordem atual de execução

### Backend

```text
TINO-BACKEND-001 — Foundation
  ↓
TINO-BACKEND-002 — Sync Contracts
  ↓
TINO-BACKEND-003 — Commerce Projections
  ↓
TINO-BACKEND-004 — Fiscal Intake
  ↓
TINO-BACKEND-005 — WhatsApp Orders
  ↓
TINO-BACKEND-006 — TINO Orchestrator
  ↓
TINO-BACKEND-007 — Intelligence Data
  ↓
TINO-BACKEND-008 — Production Hardening
```

O checkpoint atual é `TINO-BACKEND-008-production-hardening.md`; o hardening
Android está validado e os gates de infraestrutura cloud permanecem abertos.

### Checkpoint atual

- G5: `PASS_FULL`. Não iniciar G6 nem ampliar a inteligência nesta rodada sem
  task formal e critérios próprios.

- B001: aceito e documentado em `specs/TINO-BACKEND-001 — Backend Foundation.md`.
- B002 contrato: documentado e refletido no adapter Android.
- B002 Android gate: `testDebugUnitTest`, `assembleDebug` e `lintDebug` passaram.
- B002 backend gate: pendente até existir uma implementação cloud executável;
  não bloquear o Android local-first nem declarar sync cloud concluído.
- B003: contrato de projeções comerciais documentado em
  `specs/TINO-BACKEND-003-commerce-projections.md`.
- B003 Android gate: o aplicador remoto aceita eventos legados de uma linha e
  eventos B002 multi-item para vendas e compras; testes e lint passaram.
- B004: intake fiscal documentado em
  `specs/TINO-BACKEND-004-fiscal-intake.md`, mantendo nota encontrada separada
  de mercadoria confirmada.
- B005: pedidos via WhatsApp documentados em
  `specs/TINO-BACKEND-005-whatsapp-orders.md`, com idempotência, confirmação e
  lifecycle operacional.
- B006: orquestrador documentado em
  `specs/TINO-BACKEND-006-tino-orchestrator.md`, mantendo Gemma atrás de porta,
  allowlist de tools e confirmação humana; o transcriber padrão agora é
  `GemmaLiveTranscriber`, com runtime Android conectado, modelo `.task` embutido
  no APK e falha explícita quando ASR/modelo não estiverem disponíveis.
- Agent 001: consulta local de vendas de hoje implementada em
  `specs/TINO-AGENT-001-FIRST-QUERY.md`; consultas read-only não exigem
  confirmação e mutações continuam protegidas por preview/confirm.
- Agent 002/003: consultas locais de fiado e estoque documentadas nos
  checkpoints `TINO-AGENT-002-CREDIT-QUERY.md` e
  `TINO-AGENT-003-STOCK-QUERY.md`, com respostas nomeadas e sem confirmação.
- Agent 004: alteração de preço com preço atual, preview, confirmação e evento
  `product.price.changed` implementada em
  `specs/TINO-AGENT-004-PRICE-COMMAND.md`.
- Agent 005: venda fiada com validação de estoque, saldo antes/depois, preview e
  confirmação documentada em `specs/TINO-AGENT-005-CREDIT-COMMAND.md`.
- Agent 006: entrada de mercadoria com quantidade, custo, estoque e fornecedor
  no preview documentada em `specs/TINO-AGENT-006-STOCK-RECEIPT.md`.
- Agent 007: resolução segura de produto, cliente e fornecedor diferencia
  `Resolved`, `NotFound` e `Ambiguous`; nenhuma busca escolhe o primeiro
  resultado silenciosamente. O comando pendente pode ser retomado com o nome
  completo em `specs/TINO-AGENT-007-AMBIGUITY.md`.
- Agent 008: continuidade de contexto está aberta em
  `specs/TINO-AGENT-008-FOLLOW-UP-CONTEXT.md`; seleção por índice, cancelamento
  falado e voz inline para cliente/fornecedor já passaram nos testes. A última
  validação Android passou com `pid=23165`, sem fatal
  exception e com o modelo Gemma presente no APK.
- Agent 009: venda rápida por voz implementada em
  `specs/TINO-AGENT-009-SALE-VOICE.md`; produto único e quantidade entram no
  carrinho, com revisão antes do pagamento e sem auto-save. Venda fiada por voz
  é o próximo checkpoint.
- Agent 010: seleção de cliente por voz na etapa de fiado implementada em
  `specs/TINO-AGENT-010-CREDIT-VOICE.md`; saldo, estoque, preview e confirmação
  permanecem protegidos. O parser também normaliza quantidade falada. A fala
  completa no aparelho continua como gate aberto; último smoke launch: `pid=29375`,
  sem fatal exception e com Gemma no APK.
- Agent 011: confirmação e cancelamento por voz implementados em
  `specs/TINO-AGENT-011-VOICE-CONFIRMATION.md`; “sim” executa somente a chamada
  já revisada e “cancela” limpa o contexto. Último smoke launch: `pid=30567`,
  sem fatal exception e com Gemma no APK.
- Agent 012: gate de fala real aberto em
  `specs/TINO-AGENT-012-REAL-SPEECH-GATE.md`; o APK está instalado e inicia,
  mas as sequências com microfone ainda precisam ser exercitadas no aparelho.
- Agent 013: falha de preenchimento do onboarding corrigida em
  `specs/TINO-AGENT-013-ONBOARDING-FILL.md`; parser com aliases, prompt mais
  explícito e fallback local para comércio/nome/telefone. O exemplo que causava
  invenção foi removido e a transcrição virou fonte de verdade. APK corrigido
  instalado com `pid=17641`, sem fatal exception e com Gemma no APK. O pipeline
  completo também tem teste automatizado com a frase real do aparelho.
- Agent 014: persistência do primeiro acesso implementada em
  `specs/TINO-AGENT-014-ONBOARDING-PERSISTENCE.md`; `CONTINUAR` grava o perfil
  no Room, a base migra da versão 2 para 3 e o app roteia perfis existentes para
  Home. APK instalado com `pid=22561`, sem fatal exception e com Gemma no APK.
- Agent 015: roteamento seguro de baixa do fiado implementado em
  `specs/TINO-AGENT-015-CREDIT-PAYMENT-ROUTING.md`; pagamentos com valor e forma
  de recebimento são convertidos em intenção sem mutação, forma de pagamento
  não vira nome de cliente e baixa sem cliente permanece bloqueada. A allowlist
  também rejeita valores financeiros cruzados em consultas. `testDebugUnitTest`
  (155 testes), `lintDebug` e `assembleDebug` passaram; APK instalado e iniciado
  no dispositivo conectado sem fatal exception ou ANR.
- Agent 016: roteamento global implementado em
  `specs/TINO-AGENT-016-GLOBAL-COMMAND-ROUTING.md`; a entrada agentic da Home e o
  fallback global de voz cobrem venda, pagamento, estoque, preço, compras,
  produto, cliente e fornecedor. Mutações continuam em preview/confirm e
  consultas usam execução local. `testDebugUnitTest` (161 testes), `lintDebug` e
  `assembleDebug` passaram; APK instalado e iniciado com `pid=7821` sem fatal
  exception ou ANR.
- B007: inteligência e dados documentados em
  `specs/TINO-BACKEND-007-intelligence-data.md`, com baseline heurístico,
  evidência, privacidade e decisão humana.
- B008: hardening de produção documentado em
  `specs/TINO-BACKEND-008-production-hardening.md`; cliente Android com
  timeouts, limite de resposta, request id, bearer opcional, backoff de
  recuperação e circuit breaker process-local; `testDebugUnitTest`,
  `assembleDebug` e `lintDebug` passaram; gates cloud ainda pendentes.

### Android

Continuar respeitando:

- local-first;
- Room e SQLite;
- Outbox e eventos de domínio;
- sync assíncrono e recovery;
- portrait-first;
- especificações de UX/UI do TINO;
- [TINO-ZERO-DOUBT-UX.md](specs/TINO-ZERO-DOUBT-UX.md) como constituição
  operacional de clareza, confirmação, voz e estados;
- nenhuma regra de negócio dentro do LLM;
- Live Transcriber externo atrás de porta;
- Gemma atrás de porta;
- nenhuma operação normal dependente da cloud.

### UX/UI

```text
tela priorizada
  ↓
implementação específica
  ↓
screenshot no aparelho
  ↓
visual gate
  ↓
correção
  ↓
navigation/state gates
  ↓
PASS
  ↓
próxima tela
```

Não acumular telas com gate `FAIL`.

## Critério de conclusão

Um milestone só pode ser marcado como `DONE` quando o comportamento, os
estados, os gates e a documentação correspondente estiverem verificados.
Compilar, iniciar o servidor, abrir uma tela ou receber HTTP 200 isoladamente
não é evidência suficiente de conclusão.

## Fechamento do dia — 2026-08-24

### Entregas concluídas

- Fundação Multi-Vertical avançada sem criar novos packs:
  - erros de dependência de módulos passaram a ser visíveis em Configurações;
  - o onboarding passou a validar combinações de módulos antes de salvar;
  - a autorização de navegação foi centralizada em
    `TinoScreen.requiredCapability()`;
  - o fast path de voz para Venda passou a respeitar `NAVIGATE`;
  - o catálogo de tools do fallback Gemma passou a ser filtrado pelas
    capabilities do perfil ativo;
  - confirmações HITL são bloqueadas se a capability deixar de estar ativa
    antes da execução.
- Regressões automatizadas adicionadas para navegação, onboarding, catálogo
  de tools e perfil ativo.
- APK debug compilado, instalado no Xiaomi 2410FPCC5G/API 36 e iniciado em
  cold start diversas vezes com `MainActivity` em `Resumed` e processo ativo.
- Modelo Gemma local de aproximadamente 529 MB excluído do Git por tamanho;
  `README.md` documenta a disponibilização separada do asset.
- Repositório Git inicializado e enviado para
  `github.com/HEINRICK7/tino`, branch `main`, commit `de21570`.
- Somente `README.md` foi enviado entre os documentos Markdown; os demais
  documentos permanecem locais.

### Estado ao encerrar

- `G4.1 = PASS_FULL`.
- `M1–M8 = PASS`.
- `Multi-Vertical Foundation = FOUNDATION_IMPLEMENTED / PACKS_BLOCKED`.
- Novos vertical packs e G6 permanecem bloqueados até os critérios já
  definidos serem retomados.
- Nenhuma nova alteração funcional deve ser iniciada nesta sessão.
