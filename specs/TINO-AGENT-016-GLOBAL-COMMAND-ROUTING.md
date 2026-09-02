# TINO Agent 016 — Roteamento global do sistema

**Status:** IMPLEMENTADO / validado
**Escopo:** entrada global de texto e voz para operações comerciais

## Entrega

O TINO agora possui um `GlobalCommandRouter` compartilhado pelo fallback de
voz e pela entrada agentic da Home. Ele reconhece, quando a frase é clara,
operações nas seguintes áreas:

- vendas à vista e meio de pagamento;
- vendas fiadas e recebimento de fiado;
- consultas de vendas do dia, estoque, produto e saldo de cliente;
- entrada de mercadoria com custo explícito;
- alteração de preço;
- preparação de compras;
- busca de cliente e fornecedor.

O resultado é sempre um `ToolCall`. O roteador não acessa Room, não resolve
IDs e não grava dados.

## Fluxo global

```text
texto/voz committed
  ↓
GlobalCommandRouter ou Gemma
  ↓
ToolCall permitido
  ↓
EntityResolution + dados locais
  ↓
consulta imediata ou preview
  ↓
confirmação humana para mutações
  ↓
CommerceRepository / eventos / outbox
```

Quando o Gemma está indisponível, o roteador determinístico mantém o caminho
global disponível para frases suportadas. Quando o Gemma está disponível, ele
continua sendo o interpretador principal e o domínio continua sendo a fonte da
verdade.

## Proteções

- venda com PIX, dinheiro ou maquininha preserva o método até o domínio;
- entrada de estoque só é roteada com custo informado, sem inventar valor;
- preço é convertido para centavos antes do preview;
- mutações chegam ao dispatcher, nunca diretamente ao banco;
- ambiguidades continuam retornando esclarecimento;
- pagamento sem cliente não cria cliente implícito.

## Validação

- suíte `:app:testDebugUnitTest`: PASS (161 testes);
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS;
- APK instalado no dispositivo Android conectado e `MainActivity` iniciada com
  `pid=7821`, sem `FATAL EXCEPTION` ou ANR.
