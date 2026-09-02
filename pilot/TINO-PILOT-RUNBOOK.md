# TINO — Runbook de Piloto Real

Versão do kit: `0.1.0-pilot.1`

Este documento transforma a Fase 10 em um procedimento observável. O piloto
não é uma demonstração guiada: o comerciante deve trabalhar normalmente e a
equipe registra uso, confiança, correções e falhas sem inventar funcionalidades
durante a sessão.

## Escopo congelado

Validar somente os fluxos já existentes:

`Home`, `Estoque`, `Produto`, `Caderneta`, `Clientes`, `Pagamento`, `Entrada`,
`Fornecedor`, `Mais`, `Offline`, `Fiscal` e `Voz`.

Não prometer cloud produtivo, WhatsApp, RAG, Attention Engine, novos relatórios
ou commit fiscal integrado ao review visual. Essas limitações estão registradas
no status do projeto.

## Build e instalação

```bash
gradle :tino-fiscal-core:test :app:testDebugUnitTest :app:lintDebug \
  :app:assembleRelease :app:assembleDebug --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"

tools/pilot-smoke.sh app/build/outputs/apk/debug/app-debug.apk
```

O smoke instala com `adb install -r`, portanto preserva os dados locais. Não
usar `pm clear` ou desinstalação durante o piloto. Antes de qualquer upgrade
crítico, exportar/restaurar o snapshot disponível e anotar o APK anterior.

## Baseline do aparelho atual

O primeiro device registrado é o Xiaomi `2410FPCC5G`, Android 16/API 36,
resolução `720x1640`, densidade `320 dpi`, aproximadamente `7.5 GiB` de RAM e
`125 GiB` livres em `/data`. A conexão observada no baseline foi Wi‑Fi validado.

Se o aparelho mudar, repetir a coleta e criar uma nova entrada. Não comparar
resultados de devices diferentes como se fossem a mesma sessão.

## Ordem da sessão

1. Abrir o TINO sem explicar a interface e observar a primeira ação.
2. Executar o smoke de telas: Home, Estoque, Produto, Caderneta, Cliente,
   Pagamento, Entrada, Fornecedor, Mais, Offline e Fiscal.
3. Exercitar voz com linguagem natural, sem ensinar frases prontas:
   - “Quanto entrou hoje?”
   - “Quanto a Maria deve?”
   - “Bota dois Maratá pra Maria.”
   - “Mais um açúcar. Quanto ficou? Pode lançar.”
   - “Maria pagou cinquenta no Pix.”
   - “Muda o Maratá pra nove reais.”
4. Observar correção, cancelamento, confirmação, Undo e toque repetido.
5. Colocar o app em background durante um formulário, voz e preview; voltar
   pelo sistema e registrar se o estado crítico foi preservado.
6. Testar perda e retorno de conexão sem apagar dados. Confirmar que a operação
   local continua visível como salva, sem explicar outbox ao comerciante.
7. Com documentos autorizados, testar fiscal: nota boa, longa, inclinada,
   amassada e com luz ruim. Registrar captura, recaptura, correções e qualquer
   tentativa de matching ambíguo.
8. Encerrar com restart do app e conferência de produtos, clientes, saldo,
   estoque, eventos e atividade.

## Registro mínimo por sessão

Preencher [TINO-PILOT-SESSION-TEMPLATE.md](TINO-PILOT-SESSION-TEMPLATE.md).
Registrar somente utterances anonimizadas, por exemplo
`<CLIENTE> pagou cinquenta no Pix`; não salvar nome, telefone, CPF, foto de
nota ou conteúdo fiscal no repositório.

Fricções são classificadas como `LOW`, `MEDIUM`, `HIGH` ou `BLOCKING`. Bugs e
problemas de UX são separados. Também separar transcrição, intenção,
resolução de entidade, capability, domínio, A2UI, sync e fiscal.

## Gates de parada

Parar a expansão do piloto e abrir P0 quando houver:

- crash fatal, perda de dados, saldo/estoque incorreto ou mutação duplicada;
- entidade ambígua escolhida silenciosamente;
- operação financeira errada confirmada;
- item fiscal crítico ou quantidade ambígua commitada automaticamente;
- usuário acreditar que uma operação salva foi perdida.

P1 recorrente inclui voz incompreensível, contexto errado, A2UI confusa,
offline assustador ou fluxo que parece travado sem feedback. P2 visual não
interrompe a janela inicial.

## Critérios de saída

O piloto só avança quando não houver P0 conhecido, P1 crítico resolvido, voz e
multiturn aceitáveis no device-alvo, A2UI compreensível sem explicação,
Undo utilizável quando oferecido, offline sem perda percebida, fiscal sem
commit ambíguo e UX principal sem acompanhamento constante.

## Limitações atuais conhecidas

- O gateway cloud permanece indisponível quando `TINO_SYNC_BASE_URL` está vazio.
- Voz real depende da disponibilidade de reconhecimento on-device e do runtime
  instalado no aparelho.
- O review fiscal visual ainda não está ligado ao commit canônico de estoque.
- R8/minificação não está habilitado; o build release-like compila sem shrink.
- Ainda não existe evidência de uso de um comerciante real; esta é a condição
  que o runbook foi criado para validar.
