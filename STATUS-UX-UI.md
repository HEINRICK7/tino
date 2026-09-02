# TINO — Status de UX/UI

**Atualizado em:** 17/08/2026  
**Escopo:** Android, Jetpack Compose, fluxo de primeiro acesso e shell principal.

## Resumo atual

O app já possui uma base funcional local-first e uma linguagem visual compartilhada. A identidade usa o verde da marca TINO, com laranja apenas em estados de atenção e acentos. O APK mais recente foi compilado e instalado em um dispositivo Android real. A rodada P0 já avançou em venda rápida, fiado e produto, mas ainda não está concluída.

O produto ainda **não deve ser considerado final em UX/UI**. As telas principais já têm uma direção consistente, mas alguns fluxos secundários continuam com aparência de protótipo e precisam de tratamento específico.

Este documento é o **checkpoint oficial de UX/UI** do TINO. A execução da próxima rodada está detalhada em [TINO-UX-UI-P0-HARDENING.md](TINO-UX-UI-P0-HARDENING.md). O modo geral de execução contínua e seus HUMAN GATEs estão em [TINO-CONTINUOUS-EXECUTION.md](TINO-CONTINUOUS-EXECUTION.md).
Os critérios obrigatórios de clareza e conclusão estão em [TINO-ZERO-DOUBT-UX.md](specs/TINO-ZERO-DOUBT-UX.md).

## O que já temos

### Base técnica e produto

- Aplicativo Android em Jetpack Compose.
- Persistência local com Room/SQLite.
- Produtos, estoque, vendas, clientes, fiado, pagamentos, fornecedores e pedidos.
- Fluxos de entrada de mercadoria, venda rápida, recebimento e entrega.
- Estados de sincronização e modo offline.
- Orientação do app bloqueada em modo retrato.
- Componentes compartilhados para campos, botões, cards, navegação e estados.
- APK debug gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### Direção visual aplicada

- Paleta baseada na logo TINO:
  - verde principal para ações;
  - verde profundo para hierarquia e cabeçalhos;
  - verde claro para superfícies e seleção;
  - laranja para atenção;
  - vermelho para erros;
  - cinzas levemente esverdeados para textos secundários.
- Fundo claro tonalizado, evitando branco puro em toda a interface.
- Tipografia centralizada em tokens de tema.
- Escala de espaçamento e tamanhos compartilhada.
- Alvos de toque mínimos de 48dp.
- Sombras e elevações reduzidas para não transformar tudo em card flutuante.
- Navegação inferior com item atual destacado.
- Campos de texto com estados de foco, bordas e superfícies consistentes.

### Telas e componentes revisados

- Primeiro acesso:
  - logo preservada e centralizada;
  - título alinhado à referência recebida;
  - campos com labels acima;
  - ação principal verde;
  - link de restauração separado da ação principal.
- Home:
  - cabeçalho com marca e saudação;
  - ação de voz horizontal, com ícone, explicação e affordance de navegação;
  - métricas com hierarquia e acentos semânticos;
  - alerta com faixa lateral de atenção;
  - navegação inferior persistente.
- Produtos, clientes, fornecedores e pedidos:
  - linhas mais compactas;
  - cards de lista com altura e padding próprios para repetição;
  - status visíveis sem texto ambíguo.
- Restauração do comércio:
  - estado sem backup real, sem métricas fictícias;
  - botão de voltar adicionado para evitar fluxo sem saída.
- Componentes reutilizáveis:
  - `TinoLogo`;
  - `TinoTopBar`;
  - `TinoBottomNavigation`;
  - `TinoPrimaryButton` e `TinoSecondaryButton`;
  - `TinoTextField`;
  - `TinoCard`;
  - `TinoVoiceCard`;
  - `TinoMetricCard`;
  - `TinoInsightCard`;
  - linhas de produto, cliente, fornecedor e pedido.
- Venda rápida:
  - não depende mais de produtos demonstrativos no fluxo real;
  - carrinho preservado até o pagamento;
  - produto sem estoque não pode ser adicionado;
  - sucesso contextual com retorno explícito.
- Fiado e pagamentos:
  - cliente selecionado preservado entre as etapas;
  - valores inválidos bloqueados;
  - confirmação e sucesso contextualizados;
  - quantidade da linha preservada ao registrar fiado;
  - conta e recebimento sem cliente exibem estado seguro, sem fallback de demonstração.
- Produto:
  - estado vazio real com ação de cadastro;
  - lista, detalhe e ajuste preservam o produto selecionado;
  - criação de produto real validada em aparelho;
  - validação inline de preço e estoque;
  - entrada de mercadoria com formulário específico.
- Estados globais:
  - configurações separadas por intenção;
  - resumo, insights e notificações baseados nos dados locais;
  - offline e sincronização exibindo pendências reais;
  - erro e ambiguidade de voz com saídas explícitas;
  - Home sem vendas, fiado ou alertas fictícios;
  - voz sem transcrição inventada e sem dependência de modelo generativo.
- Voz contextual:
  - onboarding apresenta `PREENCHER FALANDO` acima do formulário;
  - cadastro de produto e entrada de mercadoria apresentam o mesmo método de
    entrada contextual;
  - voz inline não usa seta/chevron e não navega para a tela global de voz;
  - partial/revised aparecem na legenda do componente inline enquanto o usuário
    fala;
  - contexto ONBOARDING espera comércio, nome e celular;
  - PRODUCT_CREATE e STOCK_RECEIPT preenchem somente seus campos permitidos;
  - campos só são preenchidos para revisão, sem salvar automaticamente;
  - extração passa por allowlist e validação determinística antes de preencher;
  - correções de campos ausentes ou inválidos ficam visíveis e não bloqueiam a
    edição manual dos demais campos;
  - contrato transversal preparado para produto, estoque, cliente, fornecedor,
    fiado, venda e voz global.

## Validação realizada

- `gradle assembleDebug` passou com sucesso.
- `gradle testDebugUnitTest` passou com sucesso.
- `gradle lintDebug` passou com sucesso.
- APK final instalado por ADB em dispositivo Android conectado.
- Estados sem dados revisados para remover valores de demonstração do fluxo real.
- Primeiro acesso revisado em dispositivo real.
- Primeiro acesso bloqueia avanço incompleto e informa os campos pendentes.
- Extração de voz contextual normaliza telefone/preço/quantidade, filtra campos
  fora do contexto e retorna correção explícita quando necessário.
- Voz contextual mantém captura/transcrição independente; preenchimento
  estruturado por modelo foi removido e retorna à edição manual segura.
- Home revisada em dispositivo real.
- Produtos revisado em dispositivo real.
- Fluxo de restauração revisado e retorno adicionado.
- Não foram alterados domínio, banco ou contratos de sincronização nesta rodada.

Comando de build:

```bash
ANDROID_HOME=/home/carlos-henrique/Android/Sdk \
ANDROID_SDK_ROOT=/home/carlos-henrique/Android/Sdk \
gradle :app:assembleDebug
```

## O que ainda falta

### Prioridade P0 — necessário antes de chamar de pronto

- Substituir os fluxos genéricos de `InfoFlowScreen` por layouts com hierarquia própria para cada tarefa.
- Concluir os estados de erro recuperável e offline dos fluxos já iniciados.
- Fazer uma revisão visual dedicada de todas as telas secundárias, não apenas do shell, na seguinte ordem:
  1. venda rápida;
  2. recebimento e fiado;
  3. produto;
  4. fornecedores;
  5. pedidos e entrega;
  6. configurações;
  7. offline, erro, sucesso e confirmação.
- Aplicar os três gates obrigatórios em cada tela: `visual fidelity`, `navigation safety` e `state completeness`.
- Garantir que nenhuma tela pareça genérica quando comparada à Home.
- Revisar o comportamento em estados vazios reais, sem dados e sem internet.
- Garantir que toda tela tenha uma ação principal clara e apenas uma saída primária.
- Fazer revisão de contraste e legibilidade no tamanho real de uso, incluindo aparelhos menores.
- Validar todos os botões de voltar, cancelar e sair em cada fluxo.

Telas incluídas na revisão P0:

- Venda rápida: `QuickSaleScreen`, `ReceiveSaleScreen`.
- Recebimento e fiado: `CreditListScreen`, `CustomerAccountScreen`, `ReceivePaymentScreen`, `SelectCustomerScreen`, `ConfirmCreditScreen`.
- Produto: `ProductsScreen`, `ProductDetailScreen`, `NewProductScreen`, `AdjustStockScreen`, `StockEntryScreen`.
- Fornecedores: `SuppliersScreen`, `PurchaseSuggestionsScreen`, `SupplierOrderScreen`.
- Pedidos e entrega: `OrdersScreen`, `OrderDetailScreen`, `PickingScreen`, `DeliveryScreen`.
- Configurações: `SettingsScreen`.
- Estados: `OfflineScreen`, `VoiceErrorScreen`, `AmbiguityScreen`, `NotificationScreen`, `CompletedScreen`, estados vazios e confirmações.

Não fazem parte desta rodada:

- mudanças de domínio;
- alterações de banco ou migrações;
- mudanças no contrato de sincronização;
- novas funcionalidades fora das telas já existentes.

### Prioridade P1 — qualidade percebida

- Extrair as telas de `MainActivity.kt` para arquivos por feature, reduzindo o risco de inconsistência.
- Criar tokens explícitos de elevação, raio, cor semântica e estados interativos.
- Padronizar copy dos botões para português de ação, evitando excesso de texto em caixa alta.
- Adicionar estados de foco, erro e validação inline nos campos.
- Adicionar feedback de salvamento, sincronização, carregamento e falha.
- Revisar ícones para garantir peso visual consistente em todas as telas.
- Melhorar a tela de splash para reduzir espera percebida e evitar que pareça travada.

### Prioridade P2 — acabamento e escala

- Criar previews Compose para os estados principais de cada componente.
- Adicionar testes de screenshot/regressão visual.
- Executar e corrigir `testDebugUnitTest` e `lintDebug`.
- Validar tamanhos de fonte, contraste e toque com acessibilidade Android.
- Testar em mais de um tamanho de aparelho e densidade.
- Documentar decisões de design e exemplos de uso dos componentes.

## Débitos conhecidos

- `TinoApp.kt` ainda concentra muitos fluxos e é grande para manutenção visual;
  `MainActivity.kt` já foi reduzida ao host de startup.
- Dados demonstrativos ficaram restritos a previews Compose; integrações de voz
  e restauração ainda exibem indisponibilidade honesta quando não conectadas.
- O backend real de sincronização não está conectado por padrão.
- Há avisos de API Android depreciada para `statusBarColor` e `navigationBarColor`.
- A validação visual atual é manual; ainda não existe baseline automatizado de screenshots.

## Critério de conclusão UX/UI

Considerar o app pronto somente quando:

1. Um comerciante conseguir identificar onde está e o que fazer sem explicação externa.
2. Home, Produtos, Fiado e Mais parecerem partes do mesmo produto.
3. Cada fluxo tiver ação principal, volta e cancelamento previsíveis.
4. Estados vazios, erro, sucesso e offline tiverem o mesmo nível de cuidado da Home.
5. O app passar por `assembleDebug`, testes, lint e validação em dispositivo real.
6. Nenhuma tela depender de cor para compensar hierarquia, copy ou layout fracos.
