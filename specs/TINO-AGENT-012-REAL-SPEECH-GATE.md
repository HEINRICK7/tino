# TINO — Agentic Golden Path 012: Gate de fala real

**Status:** ACTIVE / validação no aparelho
**Tipo:** checkpoint de execução
**Pré-requisito:** `specs/TINO-AGENT-011-VOICE-CONFIRMATION.md`
**Objetivo:** validar a cadeia completa com microfone real sem confundir smoke launch com sucesso de voz.

## Sequências obrigatórias

1. Abrir venda rápida, dizer produto e quantidade, revisar carrinho.
2. Abrir comando global, dizer uma consulta read-only e conferir a resposta.
3. Dizer uma mutação, revisar preview, dizer “sim” e conferir conclusão.
4. Repetir uma mutação, dizer “cancela” e conferir que estoque/saldo/preço não mudaram.
5. Dizer um produto ambíguo, escolher “o segundo” e conferir que a intenção foi preservada.
6. Abrir fiado, dizer o cliente, revisar saldo e concluir somente após confirmação.

## Evidência exigida

- transcript parcial e committed visíveis;
- estado `ORGANIZANDO SUA FALA...` durante Gemma;
- campos/carrinho/preview atualizados;
- nenhuma operação sem confirmação;
- log sem crash;
- screenshot ou gravação de cada estado relevante.

## Limite atual

O APK já instala, inicia e contém o modelo Gemma. Isso é apenas smoke técnico.
O gate `REAL_SPEECH` permanece aberto até as sequências acima serem exercitadas
com microfone no aparelho.

## Próxima ação automática

Executar as sequências no aparelho conectado e registrar cada resultado antes de
alterar o contrato de voz novamente.
