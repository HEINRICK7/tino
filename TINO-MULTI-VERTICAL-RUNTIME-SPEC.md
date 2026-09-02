# TINO — Multi-Vertical Runtime Specification

**Status:** SPEC_BASELINED  
**Derivada de:** TINO-PRODUCT-CONSTITUTION.md  
**Data:** 2026-08-23

Este documento transforma a constituição aprovada em contratos executáveis.
Ele define fronteiras e invariantes; nomes físicos só podem ser adaptados quando
preservarem a semântica e os critérios desta especificação.

## 1. Modelo de domínio

### BusinessType

Linguagem de descoberta exibida ao usuário. Não é fonte direta de decisões
espalhadas na UI.

BusinessType pode conter RETAIL, BAKERY, RESTAURANT, STORE e OTHER. Um
BusinessType seleciona um Preset; depois do setup o runtime consome a composição
resultante, não o enum diretamente.

### Preset

Configuração inicial versionada e segura:

- id;
- businessType;
- operationalPatterns;
- modules;
- defaultQuickQueries;
- version.

Presets não podem criar capabilities que não existam no registry.

### BusinessProfile

Fonte persistida da configuração do estabelecimento:

- businessType;
- operationalPatterns;
- activeModules;
- permanentCapabilities;
- version.

O perfil não contém estado transacional. Alterá-lo nunca apaga produtos, clientes,
vendas, recebíveis ou histórico financeiro.

### OperationalPattern

Descreve um modo recorrente de operação. Patterns são combináveis e não são
sinônimos de módulos. Cada pattern declara:

- id;
- módulos requeridos;
- capabilities fornecidas;
- vocabulário;
- regras de contexto.

Exemplos conceituais: TURNOVER_COMMERCE, PRODUCTION_AND_SALES,
SERVICES_WITH_APPOINTMENTS, SERVICES_WITH_WORK_ORDER e FOOD_SERVICE. Eles não
autorizam packs novos por si só.

### Module

Agrupa um domínio funcional. Cada módulo declara:

- id;
- dependências;
- capabilities;
- patterns compatíveis.

O registry valida dependências antes de persistir uma composição.

### Capability

É a fronteira explícita de execução do TINO. Cada capability declara:

- id;
- tipo: QUERY, MUTATION ou NAVIGATION;
- risco;
- módulos requeridos;
- contexto requerido;
- tipo de StructuredResult;
- componentes A2UI compatíveis;
- orçamento de timeout.

Leituras podem responder diretamente. Mutações produzem PROPOR, review A2UI
e confirmação antes de executar.

## 2. Ativação

ActivationMode contém PERMANENT e EPHEMERAL.

Uma ativação registra capability, modo, instante de concessão, expiração opcional
e origem.

- PERMANENT entra no BusinessProfile somente após consentimento explícito.
- EPHEMERAL vale para a intenção ou sessão delimitada e não altera o perfil.
- CANCELLED não executa nem persiste mutação.

Se a capability necessária estiver inativa, o caminho é bloqueado antes da tool e
retorna RECUPERAR com Ativar e executar, Só desta vez ou Cancelar.

## 3. ContextResolver

O resolver é a fonte de composição do runtime. Recebe input, profile, session e
eventual ativação; devolve:

- patterns resolvidos;
- módulos ativos;
- capabilities disponíveis;
- vocabulário;
- tool set;
- componentes A2UI permitidos.

Agent, Fast Router, Gemma, Quick Queries e navegação consomem esse resultado.
Nenhum consumidor deve derivar capabilities perguntando diretamente se o
BusinessType é varejo, restaurante ou outro.

## 4. Fluxo operacional

Entrada
→ Intent Resolver
→ Context Resolver
→ Capability Resolver

- leitura → StructuredResult → TinoUiPlanner → A2UI → RESPONDER;
- mutação → Draft/Review A2UI → confirmação → Core → PROPOR;
- ambiguidade → escolha contextual → PERGUNTAR;
- indisponível → ativação ou recovery → RECUPERAR.

Capabilities retornam resultados estruturados e não montam layout. A LLM não
inventa componentes. O planner só seleciona componentes registrados e
compatíveis com resultado, risco, contexto e restrições do device.

## 5. Home, Quick Queries e A2UI

HomeConfiguration é derivada de profile, patterns, capabilities, estado atual do
Core, contexto temporal e preferências seguras. Deve conter semanticamente:

- entrada universal;
- ações rápidas;
- resumo operacional;
- itens de atenção;
- sugestões;
- destinos de navegação.

Quick Queries só aparecem se suas capabilities estiverem disponíveis. Uma
consulta global de estoque produz coleção; não abre picker de produto. Uma
consulta de reposição preserva estoque, mínimo e motivo no StructuredResult.

O catálogo deve priorizar componentes semânticos reutilizáveis: MetricCard,
EntityCard, StockCard, ReplenishmentCard, ReceivableCard, SummaryCard, List,
Choice, ConfirmationCard, StatusCard e Action.

Novos componentes só podem ser propostos quando uma necessidade semântica
reutilizável não puder ser composta pelo catálogo existente.

## 6. Autonomia, segurança e memória

- Memória pode melhorar vocabulário, ranking, contexto e preferências.
- Memória nunca substitui Room/Core nem decide verdade financeira.
- Aprendizado persistente precisa de origem, timestamp, confiança, frequência,
  escopo e reversibilidade.
- Preferências de apresentação podem ser aprendidas automaticamente.
- Ativação de módulos, criação ou merge de entidades e aliases sensíveis exigem
  decisão explícita.
- Pagamento, quitação, preço, saldo, cancelamento e demais mutações protegidas
  nunca são executados silenciosamente.

## 7. Deadlines e recovery

Todo caminho registra, no mínimo:

VOICE_COMMITTED
→ ROUTING_STARTED
→ ROUTING_COMPLETED
→ CAPABILITY_STARTED
→ CAPABILITY_COMPLETED
→ A2UI_READY
→ RENDERED

Ao atingir o deadline, o runtime cancela o trabalho, registra o estágio e retorna
recovery acionável. Não existe spinner infinito, retry ilimitado ou fallback que
continue aguardando indefinidamente.

Recoveries mínimas:

- capability inativa: ativar, usar uma vez ou cancelar;
- capability indisponível: tentar novamente ou continuar manualmente;
- interpretação inválida: reformular ou escolher sugestão;
- timeout técnico: retry limitado e mensagem curta;
- empty state: ausência válida de dados, sem tratá-la como erro.

## 8. Migração do modelo atual

O código atual possui BusinessVertical → BusinessModule → Capability. A migração
deve ser aditiva, compatível e não destrutiva:

1. Mapear BusinessVertical para BusinessType compatível.
2. Transformar o preset atual em Preset versionado.
3. Derivar OperationalPatterns iniciais dos módulos já ativos.
4. Preservar activeModules e criar capabilities por registry, sem inferir novas
   permissões transacionais.
5. Persistir profileVersion, patterns e ativações permanentes em migration
   aditiva; dados antigos recebem preset Retail compatível.
6. Introduzir ContextResolver como camada de compatibilidade antes de remover
   consultas diretas ao vertical.
7. Migrar Home, Quick Queries, Agent Context e navegação para ResolvedContext.
8. Remover gradualmente condicionais de UI baseadas em vertical.

Nenhuma migration pode apagar dados comerciais ou transformar capability em
mutação silenciosa. O rollout deve ser observável, versionado e reversível.

## 9. Critérios executáveis

O runtime só pode ser considerado integrado quando:

- perfil antigo abre com fallback seguro e sobrevive a restart;
- patterns, módulos e capabilities são derivados por registry;
- módulos desativados desaparecem de Home, navegação, Quick Queries e tools;
- capabilities inativas convergem para recovery sem execução;
- ativação EPHEMERAL não altera o perfil persistido;
- mutações continuam exigindo review e confirmação;
- structured results chegam ao planner antes do renderer;
- toda intenção termina dentro do timeout definido;
- logs identificam profile, pattern, capability, estágio e recovery;
- uma composição não varejista funciona sem Home ou Core paralelo.

Até esses critérios serem atendidos, nenhum pack de Confeitaria, Oficina,
Restaurante ou Serviços deve ser iniciado.
