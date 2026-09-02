# TINO — Zero-Doubt UX

**Status:** obrigatório  
**Versão:** 1.0  
**Escopo:** Android, voz, WhatsApp e futuras interfaces

Este documento operacionaliza a constituição de produto do TINO. Ele tem
precedência sobre preferências visuais locais quando uma decisão afetar clareza,
segurança ou recuperação do comerciante.

## Princípios não negociáveis

1. A complexidade fica no TINO, nunca no comerciante.
2. Cada tela tem uma tarefa principal e no máximo uma ação visualmente dominante.
3. A ação principal explica o resultado: `RECEBER`, `ANOTAR FIADO`,
   `CADASTRAR PRODUTO`, `CONFIRMAR ENTRADA`.
4. A tela deve responder em até três segundos: onde estou, o que acontece, o
   que posso fazer e qual é o próximo passo.
5. A linguagem é a do comércio: venda, produto, mercadoria, cliente, fiado,
   receber, comprar, pedido e entrega.
6. Reconhecimento vence memorização: consequência, pessoa, produto, valor e
   estoque devem aparecer no contexto da decisão.
7. Nenhuma operação comercial é executada silenciosamente por voz. A sequência
   é transcrever → interpretar → validar → mostrar consequência → confirmar.
8. Offline é um estado de trabalho, não uma falha técnica. A operação local
   continua quando for segura e a pendência fica explícita.

## Gates obrigatórios

Uma tela só pode ser marcada como `DONE` quando todos os gates abaixo passarem:

| Gate | Pergunta de aprovação |
|---|---|
| `ZERO_DOUBT` | Uma pessoa sem experiência entende o próximo toque sem explicação? |
| `VISUAL` | A hierarquia, contraste, toque e branding estão consistentes com a Home? |
| `FUNCTIONAL` | O caminho normal conclui a tarefa com dados reais? |
| `NAVIGATION` | Voltar, cancelar e sair retornam ao contexto sem perder intenção? |
| `STATE` | Normal, vazio, carregando, erro, sucesso e offline estão tratados? |
| `VOICE` | Voz contextual/global foi implementada ou `JUSTIFIED_NA` foi registrado? |
| `OFFLINE` | O comportamento sem internet está claro ou `JUSTIFIED_ONLINE_ONLY` foi registrado? |
| `REAL_DEVICE` | O fluxo foi revisado no Android real em tamanho de uso? |

`DONE` não significa apenas “a tela abriu”. Um gate `FAIL` mantém a tela em
`IN_PROGRESS`.

## Regras de interação

- Botões não usam `OK`, `Enviar`, `Processar`, `Aplicar` ou `Executar` quando
  um resultado concreto puder ser dito.
- Erros dizem o que aconteceu e o que fazer agora, preservando o que já foi
  digitado ou reconhecido.
- Toda confirmação mostra a consequência: pessoa, produtos, valor, estoque ou
  saldo afetado.
- Toda ação mutável tem cancelamento previsível; ações de alto risco exigem
  revisão humana antes da execução.
- Ícone nunca é a única indicação de uma ação importante; texto ou descrição
  acessível acompanha o controle.
- Nenhum dado fictício aparece em fluxo real. Dados de exemplo pertencem apenas
  a previews e testes.
- Telas de referência com dados ilustrativos não entram no roteamento de
  produção até receberem dados, confirmação e estados reais.

## Contrato de voz

Voz é método de entrada transversal. Cada fluxo declara seu contexto:

### Inline Voice Interaction

Quando a voz preenche, pesquisa, filtra, corrige ou executa uma ação da tela
atual, ela acontece inline. O componente `TinoInlineVoiceInput` muda de estado
na própria tela; ele nunca abre uma tela intermediária de transcrição e nunca
usa chevron, seta ou `>` para sugerir navegação.

Estados visíveis obrigatórios:

```text
IDLE          → PREENCHER FALANDO
LISTENING     → ESTOU OUVINDO...
UNDERSTANDING → ORGANIZANDO SUA FALA...
SUCCESS       → DADOS PREENCHIDOS / Confira abaixo
ERROR         → NÃO ENTENDI DIREITO / Fale novamente ou preencha abaixo
```

`PARTIAL` e `REVISED` atualizam a legenda do mesmo componente em tempo real;
`COMMITTED` encerra a captura e inicia a validação. O CTA normal da tela
continua sendo o único caminho para concluir o cadastro ou a operação.

Voz global é diferente: quando o comerciante pede outra tarefa, como “quanto
João deve?”, o TINO pode mudar de contexto e navegar para Fiado. `VOICE INPUT`
não é `NAVIGATION`.

A interface nunca expõe `áudio`, `transcriber`, `transcrição`, `streaming`,
`extração estruturada` ou `Gemma` como instrução ao comerciante. Esses nomes
pertencem à implementação; para o usuário, a ação é `PREENCHER FALANDO`.

```text
ONBOARDING       → store_name, owner_name, phone
PRODUCT_CREATE   → product_name, size, unit, sale_price
STOCK_RECEIPT    → product, boxes, units_per_box, quantity, unit_cost
CUSTOMER_CREATE  → name, phone
SUPPLIER_CREATE  → name, phone
CREDIT_SALE     → customer, products
SALE             → products, payment_method
GLOBAL           → intenção roteada pelo Gemma
```

O modelo não grava banco, calcula saldo, altera estoque ou confirma uma
operação. A saída estruturada passa por allowlist e validação determinística;
dados ausentes ou inválidos retornam correção, nunca mutação silenciosa.

## Auditoria atual

**Score heurístico atual: 6/10 — IN PROGRESS.**

Pontos já aprovados:

- Home, primeiro acesso, venda rápida, fiado e produto têm hierarquia e
  componentes compartilhados.
- Fluxos reais não dependem de dados demonstrativos.
- Voltar, estados vazios e sucesso local estão tratados nos principais fluxos.
- Onboarding por voz já preserva dados válidos, mostra correções e não salva
  automaticamente.

Falhas abertas, em ordem de severidade:

| Severidade | Gap | Próxima correção |
|---|---|---|
| 3 | O motor MediaPipe/Gemma, o modelo `.task` e o ASR Android estão conectados | Validar fala em português, memória e latência no aparelho-alvo |
| 3 | Voz contextual ainda falta em cliente e fornecedor | Integrar os próximos contextos sem duplicar mutação |
| 3 | Telas de fornecedores, pedidos/entrega e alguns estados continuam com tratamento de protótipo | Dar layout, ação e estados próprios por tarefa |
| 2 | Alguns CTAs ainda são genéricos (`CONFIRMAR`, `CRIAR`, `SALVAR`) | Trocar por resultado concreto da operação |
| 2 | Offline, erro e recuperação ainda não têm o mesmo acabamento de Home | Aplicar state completeness por tela |

## Ordem de execução P0

1. Venda rápida → recebimento/fiado.
2. Produto → entrada de mercadoria.
3. Fornecedores → pedidos/entrega.
4. Configurações → offline, erro, sucesso e confirmação.
5. Voz contextual em cliente e fornecedor e voz global com preview/confirm.
6. Gate em aparelho real e revisão com comerciante sem experiência técnica.

Cada item precisa registrar `PASS`, `FAIL` ou `JUSTIFIED_NA` para os oito gates
antes de avançar para o próximo.
