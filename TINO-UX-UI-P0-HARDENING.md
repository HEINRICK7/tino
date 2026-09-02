# TINO — UX/UI P0 Hardening

**Status:** próximo checkpoint oficial  
**Escopo:** somente UX/UI e segurança de navegação  
**Fora do escopo:** domínio, banco, sync, contratos e novas funcionalidades  
**Referência:** [STATUS-UX-UI.md](STATUS-UX-UI.md)

## Objetivo

Fechar as telas secundárias do TINO com o mesmo nível de intenção visual e de segurança de navegação da Home.

Nenhuma tela deve continuar parecendo um placeholder ou uma variação automática de `InfoFlowScreen`.

## Registro de execução

### P0.8 — Remoção de dados fictícios do fluxo real

Implementado nesta rodada:

- Home inicia com métricas reais em zero, sem vendas, fiado ou alertas inventados;
- conta, recebimento e confirmação de fiado não criam cliente ou compra falsa;
- restauração sem backup mostra estado vazio honesto;
- novo produto inicia com campos vazios e placeholders orientativos;
- detalhe de produto não exibe custo ou previsão de estoque sem dados reais;
- voz não simula transcrição quando o runtime Gemma ainda não está conectado;
- dados de demonstração permanecem somente nos previews Compose.
- controles sem implementação foram removidos: câmera, código de barras,
  edição falsa de preço e seleção de pagamento não persistida;
- ajuste manual de estoque agora encaminha para entrada de mercadoria, sem
  simular uma correção que não seria salva;
- fornecedores sem detalhe implementado não parecem clicáveis.
- primeiro acesso não avança com comércio, nome ou celular vazios; a mensagem
  lista exatamente os campos pendentes.
- a tela de voz agora usa estados reais do transcriber Gemma: escuta,
  transcrição, preview, confirmação, erro e fallback manual.
- o onboarding ganhou voz contextual com contexto explícito e preenchimento
  revisável, sem salvar automaticamente.
- a saída estruturada do Gemma agora passa por allowlist e validação
  determinística; campos válidos são preservados e campos incompletos ou
  inválidos pedem correção explícita.
- cadastro de produto e entrada de mercadoria receberam voz contextual com
  contexto próprio; a edição manual continua disponível como caminho de
  recuperação.

Gates atuais:

- `visual fidelity`: aprovado no estado sem dados;
- `navigation safety`: aprovado para retornos de fiado, restauração e voz;
- `state completeness`: parcial; integrações de voz e restauração continuam
  aguardando implementação externa.

### P0.1 — Venda rápida: em andamento

Implementado nesta rodada:

- ação `Venda rápida` exposta na Home;
- busca de produto com ação explícita de adicionar;
- linhas de produto com preço e ação explícita de adicionar;
- carrinho com quantidade, itens, total e estado vazio;
- CTA desabilitado quando o carrinho está vazio;
- resumo da venda preservado ao avançar para pagamento;
- pagamento com dinheiro, PIX e fiado visíveis como escolhas distintas;
- retorno da tela de pagamento para a venda.
- produtos fictícios removidos do fluxo real de venda;
- produto sem estoque e limite de quantidade tratados visualmente;
- confirmação contextualizada por meio de pagamento;
- tela de sucesso mantida até o usuário tocar em `PRONTO`.

Gates atuais:

- `visual fidelity`: aprovado na validação manual do dispositivo;
- `navigation safety`: aprovado para Home → venda → pagamento → retorno;
- `state completeness`: parcial; produto indisponível e conclusão contextual estão cobertos; faltam erro de registro e offline.

P0.1 ainda aguarda os estados de erro, offline e conclusão contextualizada; o trabalho de P0.2 foi iniciado sem alterar o domínio.

### P0.2 — Recebimento e fiado: em andamento

Implementado nesta rodada:

- seleção de cliente preservada entre lista, conta e confirmação;
- estado vazio real quando não há clientes ou quando a busca não encontra resultado;
- total em aberto baseado nos dados atuais, sem valor demonstrativo forçado;
- histórico demonstrativo removido da conta do cliente;
- resumo de recebimento calculado a partir do valor digitado;
- confirmação de recebimento desabilitada para valor inválido ou vazio;
- retorno da conta para a lista de fiado e da seleção para o pagamento.
- confirmação de pagamento com sucesso contextualizado;
- quantidade escolhida no carrinho preservada ao anotar fiado.

Gates atuais:

- `visual fidelity`: aprovado na validação manual da lista e conta;
- `navigation safety`: aprovado para selecionar cliente e abrir a conta correta;
- `state completeness`: parcial; valor inválido e sucesso contextual estão cobertos; falta tratamento offline e erro recuperável dedicado.

### P0.3 — Produto: em andamento

Implementado nesta rodada:

- lista sem dados fictícios, com estado vazio acionável;
- venda rápida orientando o cadastro quando não há produto;
- seleção do produto preservada entre lista, detalhe e ajuste de estoque;
- detalhe e ajuste com saída segura quando nenhum produto foi selecionado;
- produto sem estoque exibido como indisponível para adicionar;
- criação de produto real validada no dispositivo;
- validação inline de preço e estoque inicial;
- entrada de mercadoria com formulário próprio e campos essenciais.

Gates atuais:

- `visual fidelity`: aprovado na validação manual da lista, criação e detalhe;
- `navigation safety`: aprovado para lista → detalhe → ajuste e retorno;
- `state completeness`: parcial; falta erro recuperável e sucesso contextual da operação de produto.

### P0.4 — Fornecedores e compras: em andamento

Implementado nesta rodada:

- fornecedores reais vindos do armazenamento local;
- estado vazio acionável para cadastro de fornecedor;
- sugestões de compra sem dados fictícios, com encaminhamento para produtos;
- pedido sem rascunho com saída segura para sugestões;
- cadastro de fornecedor mantido separado das ações de compra.

Gates atuais:

- `visual fidelity`: aprovado na validação do estado vazio e da lista;
- `navigation safety`: aprovado para Mais → fornecedores e retorno;
- `state completeness`: parcial; falta pedido real, envio, erro de envio e sucesso contextual.

### P0.5 — Pedidos e entrega: em andamento

Implementado nesta rodada:

- pedidos demonstrativos removidos da lista real;
- estado vazio com retorno claro para `Mais`;
- detalhe, separação e entrega sem pedido selecionado agora têm saída segura;
- cada etapa explica o que falta para continuar.

Gates atuais:

- `visual fidelity`: aprovado no estado vazio e nas etapas sem contexto;
- `navigation safety`: aprovado para retornar entre lista, detalhe, separação e entrega;
- `state completeness`: parcial; falta integrar pedidos reais, status, erro e conclusão.

### P0.6/P0.7 — Configurações e estados globais: em andamento

Implementado nesta rodada:

- configurações separadas por categorias, com rotas reais para sync, offline e notas;
- resumo do dia calculado com vendas e fiado atuais;
- insights e notificações baseados no estoque local;
- sincronização exibindo a quantidade real de eventos pendentes;
- modo offline sem número demonstrativo;
- erro de voz com retorno explícito para tentar novamente ou operar manualmente;
- ambiguidade de produto com saídas específicas, sem lista fictícia.

Gates atuais:

- `visual fidelity`: aprovado nos layouts específicos e estados vazios;
- `navigation safety`: aprovado nas rotas de configurações, offline, voz e avisos;
- `state completeness`: parcial; falta integrar estados de carregamento, sincronização real e sucesso/erro de cada integração externa.

## Ordem obrigatória de execução

### P0.1 — Venda rápida

Telas:

- `QuickSaleScreen`
- `ReceiveSaleScreen`

Entrega esperada:

- seleção de produtos com hierarquia de preço, quantidade e subtotal;
- carrinho sempre visível quando houver itens;
- CTA único para receber;
- estados sem produto, carrinho vazio e produto indisponível;
- retorno seguro sem perder a intenção do usuário.

### P0.2 — Recebimento e fiado

Telas:

- `CreditListScreen`
- `CustomerAccountScreen`
- `ReceivePaymentScreen`
- `SelectCustomerScreen`
- `ConfirmCreditScreen`

Entrega esperada:

- saldo em aberto como informação principal;
- distinção clara entre anotar fiado e receber pagamento;
- confirmação com resumo antes da mutação;
- cancelamento e retorno em todas as etapas;
- estados sem clientes, cliente sem dívida, valor inválido, offline e sucesso.

### P0.3 — Produto

Telas:

- `ProductsScreen`
- `ProductDetailScreen`
- `NewProductScreen`
- `AdjustStockScreen`
- `StockEntryScreen`

Entrega esperada:

- estoque e status como primeira leitura;
- ações separadas por intenção: criar, editar, ajustar e registrar entrada;
- formulário com validação inline;
- confirmação de ajuste com motivo explícito;
- estados sem produtos, estoque zerado, erro de leitura e operação concluída.

### P0.4 — Fornecedores

Telas:

- `SuppliersScreen`
- `PurchaseSuggestionsScreen`
- `SupplierOrderScreen`

Entrega esperada:

- sugestão de compra separada de cadastro de fornecedor;
- itens agrupados por pedido e fornecedor;
- ação de preparar pedido distinta da ação de enviar;
- estado sem fornecedor e sem sugestão;
- falha de envio com recuperação clara.

### P0.5 — Pedidos e entrega

Telas:

- `OrdersScreen`
- `OrderDetailScreen`
- `PickingScreen`
- `DeliveryScreen`

Entrega esperada:

- status do pedido visível sem abrir o detalhe;
- sequência operacional clara: abrir → separar → pronto → entregar;
- endereço, pagamento e itens com hierarquia própria;
- saída segura em cada etapa;
- estados vazio, pedido incompleto, erro e conclusão.

### P0.6 — Configurações

Telas:

- `SettingsScreen`

Entrega esperada:

- categorias reconhecíveis, não uma lista plana genérica;
- itens que parecem configurações devem abrir uma configuração real ou indicar indisponibilidade;
- ajuda e acessibilidade encontráveis;
- retorno para `Mais` consistente.

### P0.7 — Offline, erro, sucesso e confirmação

Telas:

- `OfflineScreen`
- `VoiceErrorScreen`
- `AmbiguityScreen`
- `NotificationScreen`
- `CompletedScreen`
- confirmações e estados vazios compartilhados.

Entrega esperada:

- explicar o que aconteceu;
- explicar o que o usuário pode fazer agora;
- preservar dados digitados;
- oferecer uma saída principal e uma alternativa compreensível;
- não usar mensagens genéricas como “erro” ou “tente novamente” sem contexto.

## Gates obrigatórios por tela

Cada tela só pode ser considerada concluída quando passar pelos três gates abaixo.

### 1. Visual fidelity

- Parece parte do TINO quando comparada à Home.
- Usa os mesmos tokens de cor, tipografia, espaçamento, raio e elevação.
- Tem uma hierarquia própria para a tarefa, sem card genérico com lista de textos.
- Tem uma ação principal evidente.
- Não usa cor para compensar falta de hierarquia.
- Funciona no tamanho real de aparelho menor.

### 2. Navigation safety

- O usuário sabe onde está.
- O título corresponde ao item que abriu a tela.
- Voltar sempre retorna ao contexto correto.
- Cancelar não executa uma ação acidental.
- Nenhuma tela fica sem saída.
- Fluxos com dados digitados não perdem a intenção sem aviso.

### 3. State completeness

Cada tela deve ser verificada nos estados:

- normal com dados;
- vazio;
- carregando, quando aplicável;
- erro recuperável;
- sem internet, quando aplicável;
- sucesso após ação;
- confirmação antes de ação irreversível.

## Método de implementação

Para cada tela:

1. Registrar o objetivo da tela em uma frase.
2. Remover o uso de `InfoFlowScreen` se ele esconder a hierarquia da tarefa.
3. Definir a ação principal e a saída segura.
4. Criar o layout específico usando componentes compartilhados.
5. Implementar estados vazios, erro, sucesso e offline necessários.
6. Validar no dispositivo real.
7. Registrar screenshot e resultado dos três gates.
8. Só então avançar para a próxima tela.

## Critério de aceite da rodada P0

A rodada só termina quando:

- todas as telas da ordem P0 tiverem layout específico;
- nenhuma tela principal depender de `InfoFlowScreen` como apresentação final;
- os três gates estiverem aprovados em todas as telas;
- Home, Produtos, Fiado e Mais parecerem o mesmo produto;
- os fluxos de voltar, cancelar e concluir forem testados no dispositivo;
- `assembleDebug`, testes unitários e lint forem executados;
- nenhum domínio, banco ou contrato de sync tiver sido alterado.

## Evidências esperadas

Para cada grupo P0, guardar:

- screenshot do estado normal;
- screenshot do estado vazio ou erro mais importante;
- confirmação de navegação de volta;
- resultado dos gates `visual fidelity`, `navigation safety` e `state completeness`;
- comando de build executado e resultado.
