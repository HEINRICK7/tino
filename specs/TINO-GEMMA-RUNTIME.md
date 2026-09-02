# TINO — Gemma Runtime

**Status:** modelo embutido no APK, ASR Android conectado e feedback de processamento implementado; validação de fala real contínua

## O que foi implementado

- `MediaPipeGemmaStructuredExtractor` usa `LlmInference` para transformar uma fala já confirmada em JSON estruturado.
- O resultado passa por `VoiceExtractionValidator` antes de chegar aos campos da tela.
- A interface mostra a transição `ESTOU OUVINDO...` → `ORGANIZANDO SUA FALA...` → `DADOS PREENCHIDOS`, evitando que o comerciante interprete a espera como falha.
- O botão de captura deixa claro o resultado: `CONCLUIR E PREENCHER`.
- Produto aceita por voz nome, preço e estoque inicial; entrada de mercadoria aceita produto, quantidade, custo e fornecedor.
- O modelo é procurado primeiro no armazenamento privado e, se existir, é
  copiado automaticamente do asset empacotado em `assets/models/`:

  `files/models/gemma3-1b-it-int4.task`

- Se o arquivo não existir, o app permanece seguro e mostra o preenchimento manual.
- O Gemma não salva dados, não executa ações e não acessa banco. Confirmação e mutações continuam nas camadas de domínio.

## O que ainda falta para fechar a validação

1. Validar fala em português em um aparelho com serviço de reconhecimento disponível.
2. Medir memória e tempo da primeira inferência no aparelho-alvo.
3. Avaliar distribuição por asset pack para produção, pois o APK atual fica grande.

O runtime oficial `LlmInference` recebe texto e devolve texto. Portanto, ele é o interpretador/estruturador da fala. O áudio agora passa pelo ASR do Android com preferência por processamento no dispositivo e só o texto confirmado chega ao Gemma.

## Gate de integração

- [x] Dependência MediaPipe Tasks GenAI adicionada.
- [x] Inferência Gemma atrás de uma porta interna.
- [x] JSON limitado ao contexto da tela.
- [x] Validação determinística antes da UI.
- [x] Fallback manual quando modelo ou ASR estão indisponíveis.
- [x] Modelo `.task` empacotado em `assets/models/` e presente no APK.
- [x] ASR Android conectado com preferência offline e permissão de microfone.
- [x] Estado visual de entendimento após o fim da fala.
- [x] Normalização de valores monetários em comandos globais.
- [ ] Teste em aparelho real com fala em português.
