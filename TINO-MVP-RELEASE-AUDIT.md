# TINO — MVP Release Audit

Status: `AUDIT_COMPLETE / NO_NEW_FEATURES`

Data: 2026-08-30  
Plataforma: Android + Backend  
Canal analisado: `0.1.0-pilot.1` / `versionCode=2`

## Decisão executiva

O menor TINO que deve chegar aos primeiros clientes é um assistente local para
o trabalho diário do pequeno comércio:

```text
abrir o comércio
→ cadastrar ou encontrar produto
→ registrar venda
→ registrar entrada ou ajuste operacional
→ acompanhar estoque
→ anotar fiado
→ receber pagamento
→ continuar trabalhando sem internet
```

Esse núcleo deve ser a única experiência pública inicial. A Home deve levar a
esse trabalho, e não apresentar o inventário completo da arquitetura.

NF-e, voz/TINO, inteligência, fornecedores, compras, pedidos, A2UI e sync
continuam valiosos, mas não precisam ser promovidos ao MVP público só porque
existem no código. NF-e fica explicitamente `PILOT_ONLY`, como solicitado.

## Critério de classificação

| Classe | Regra de release |
|---|---|
| `CORE_MVP` | Necessário para o primeiro cliente conseguir operar o comércio com segurança. |
| `PILOT_ONLY` | Pode ser usado em piloto controlado, com observação e fallback manual. Não é promessa do MVP público. |
| `HIDDEN_READY` | Implementado o suficiente para ficar atrás de uma política de exposição; não entra em navegação/CTA público. |
| `FUTURE` | Não desenvolver agora; existe como contrato, fundação, spike ou direção. |
| `REMOVE_FROM_EXPERIENCE` | Existe tecnicamente, mas deve sair da experiência atual por duplicar função, sugerir ERP ou criar promessa que o produto ainda não sustenta. Código pode permanecer. |

`CORE_MVP` descreve necessidade de produto, não necessariamente uma tela
visível. `HIDDEN_READY` descreve prontidão técnica parcial, não autorização para
prometer a capacidade. Uma mesma área pode ter uma capability `HIDDEN_READY` e
uma rota atual `REMOVE_FROM_EXPERIENCE`.

## Job do primeiro cliente

> Quando estou atendendo e a mercadoria entra ou sai, quero registrar o que
> aconteceu em poucos segundos, mesmo com internet ruim, para saber o que tenho,
> o que vendi e quem ainda me deve — sem perder o controle do comércio.

Dimensões observadas:

- funcional: reduzir caderno, memória e conferência manual;
- emocional: confiança de que o lançamento foi salvo e não duplicado;
- social: trabalhar no ritmo do balcão, sem parecer que o comerciante precisa
  aprender um ERP.

O maior risco de release hoje não é ausência de uma feature; é excesso de
superfície e promessa. A evidência existente é principalmente técnica. Ainda
não há evidência de uso contínuo de um comerciante real.

## Classificação Android

| Área / superfície | Classificação | Decisão de experiência | Evidência atual |
|---|---|---|---|
| Primeiro acesso, comércio, nome e perfil mínimo | `CORE_MVP` | Manter simples; não transformar perfil fiscal/comercial em bloqueio funcional. | `TinoApp.kt`, `BusinessProfile.kt`, `pilot/TINO-PILOT-RUNBOOK.md` |
| Home operacional | `CORE_MVP` | Manter como ponto de entrada para hoje, ação rápida e atenção essencial. | `TinoHome.kt`, `TinoNavigation.kt` |
| Venda manual | `CORE_MVP` | Fluxo público principal; dinheiro, PIX e fiado com confirmação clara. | `QuickSaleScreen`, `ReceiveSaleScreen`, `CommerceRepository` |
| Produtos, preço e estoque local | `CORE_MVP` | Manter lista, busca, cadastro mínimo e detalhe útil. | `ProductsScreen`, `CommerceRepository`, Room |
| Entrada manual de mercadoria | `CORE_MVP` | Manter como fallback universal, inclusive offline. | `StockEntryScreen`, fluxo local testado |
| Clientes e Caderneta | `CORE_MVP` | Manter criação, saldo, conta e histórico essencial. | `CustomersScreen`, `CreditListScreen`, `CustomerAccountScreen` |
| Recebimento de fiado | `CORE_MVP` | Manter dinheiro/PIX e confirmação; Undo universal não é requisito para expor tudo. | `ReceivePaymentScreen`, ledger local |
| Offline como comportamento | `CORE_MVP` | Manter a promessa simples “você pode continuar trabalhando”; esconder detalhes técnicos. | `OfflineScreen`, Room, outbox |
| Sync local/outbox/retry | `HIDDEN_READY` | Infraestrutura obrigatória do núcleo; não vender “backup” até cloud real estar conectado. | `core/sync`, `ADR-001.md` |
| Voz contextual e comandos TINO | `PILOT_ONLY` | Ativar por canal/allowlist de piloto; manual é sempre o fallback. | Pipeline existe, mas voz real depende de aparelho, ASR, modelo e validação física contínua. |
| Agentic Shell | `PILOT_ONLY` | Usar para aprender; não fazer o cliente depender da interpretação agentic para operar. | `TinoAgentSession`, `AgenticVoiceViewModel`, status parcial |
| A2UI protocol, catalog e renderer | `HIDDEN_READY` | Manter como infraestrutura para resultados agentic; não apresentar “A2UI” ao cliente. | `interfaceadapter/a2ui`, renderer allowlisted |
| NF-e por chave → preview → confirmação | `PILOT_ONLY` | Expor somente em piloto; nunca prometer SERPRO Produção. | Contrato backend `4737fd4`, E2E HTTP real Trial PASS |
| Scanner/OCR/foto de DANFE | `REMOVE_FROM_EXPERIENCE` | Retirar CTA do fluxo conectado; câmera pode voltar apenas como atalho para chave. | `feature/fiscal`, `FiscalReviewScreen`, runbook e contrato vigente |
| `fiscal-core` parser/raw/canonical local | `HIDDEN_READY` | Preservar código; não permitir que seja a rota pública de NF-e. | Classificação anterior de fiscal-core e novo fluxo remoto |
| Fornecedores | `FUTURE` | Tirar da navegação pública inicial; manter somente se necessário para piloto operacional. | `SuppliersScreen`, dados locais reais, sem necessidade para o job mínimo |
| Compras / sugestões de reposição | `FUTURE` | Remover “Comprar” da experiência inicial; pode ser observado em piloto. | `PurchaseSuggestionsScreen`, inteligência parcial |
| Pedidos, separação e entrega | `REMOVE_FROM_EXPERIENCE` | Ocultar rotas e CTAs; não há canal WhatsApp/lifecycle real para sustentar a promessa. | `OrdersScreen`, `PickingScreen`, `DeliveryScreen`, backend messaging não integrado |
| Resumo diário | `CORE_MVP` | Reduzir a leitura a poucos fatos do dia, sem dashboard. | `DailySummaryScreen`, fatos Room |
| Insights, recomendações e Attention | `PILOT_ONLY` | Mostrar somente em piloto, com linguagem de estimativa e evidência. | `TinoEvidenceEngine`, `Recommendations`, status parcial/local |
| Notificações de atenção | `PILOT_ONLY` | Não criar expectativa de inteligência proativa no lançamento público. | Attention Engine local e canal Android testados; uso real ainda não validado |
| Modo debug: A2UI/G3/G4/G5 | `REMOVE_FROM_EXPERIENCE` | Continuar protegido por `BuildConfig.DEBUG`; nunca compõe a experiência de cliente. | Condicionais `BuildConfig.DEBUG` em `TinoApp.kt`/`TinoHome.kt` |
| Configuração de perfil/verticais | `HIDDEN_READY` | Usar internamente; não deixar “Padaria/Restaurante/Outro” sugerir packs completos. | `BusinessProfile`, presets e módulos compartilhados |
| “Backup e sincronização” | `REMOVE_FROM_EXPERIENCE` | Renomear/ocultar até existir backend cloud e restore real. | `SettingsScreen`, `TINO_SYNC_BASE_URL` vazio por padrão |

### Núcleo Android recomendado para exposição pública

```text
Home
├── Nova venda
├── Produtos / estoque
├── Caderneta
└── Mais
    ├── Entrada manual
    ├── Configurações mínimas
    └── Estado offline quando necessário
```

NF-e não entra nesse desenho público por default. No canal de piloto, entra como
uma ação de entrada de mercadoria, sempre acompanhada da entrada manual.

## Classificação Backend

| Área / capacidade | Classificação | Decisão |
|---|---|---|
| Identidade, autenticação, business e membership | `CORE_MVP` | Fundação para qualquer piloto autenticado e para separar negócios com segurança. |
| Bootstrap e instalação de device | `CORE_MVP` | Necessários quando o produto sair do aparelho local isolado. |
| Sync HTTP e projeções cloud | `FUTURE` | Não prometer no MVP público enquanto Android usa gateway vazio e não há operação cloud produtiva validada. |
| Catálogo/Product Search | `PILOT_ONLY` | Suporte ao fluxo NF-e; não precisa ser superfície própria no MVP. |
| NF-e retrieve/preview/confirm e GoodsReceipt | `PILOT_ONLY` | Implementado e testado no Trial; SERPRO Produção permanece bloqueado. |
| SERPRO Produção / certificado | `FUTURE` | Não desenvolver/ativar nesta fase. |
| Customer/Credit/Payment HTTP | `HIDDEN_READY` | Backend possui módulos e contratos, mas a operação pública Android continua local-first; falta E2E integrado para promovê-los. |
| Inventory cloud autoritativo para vendas locais | `FUTURE` | Não inventar sincronização parcial nem criar segunda autoridade para o núcleo offline. |
| Messaging/WhatsApp | `FUTURE` | Há modelos e contrato, mas não há gateway, webhook, identidade externa e lifecycle produtivos. |
| Reconciliation | `HIDDEN_READY` | Manter como infraestrutura operacional; não é valor de primeira tela. |
| Intelligence cloud/RAG/orquestrador | `FUTURE` | Contratos e fundações não equivalem a produto integrado. |
| Observabilidade, segurança, RLS e idempotência | `CORE_MVP` | Guardrails de lançamento, não feature comercial. |

## O que não está faltando para começar o piloto

Não há justificativa, nesta auditoria, para iniciar outra grande funcionalidade.
O que falta é evidência e redução:

1. provar uso real do núcleo manual com comerciantes;
2. retirar da navegação pública pedidos, compras, diagnósticos e importação por
   foto;
3. manter voz e NF-e atrás de uma política de canal;
4. validar crash, offline, restart, correção e confiança no dispositivo alvo;
5. não chamar sync local de backup cloud;
6. corrigir estados UX P0 já registrados antes de ampliar o alcance.

## Política única de exposição

A política deve ser centralizada por canal de release, sem depender de tipo de
empresa, MEI, existência de NF-e ou perfil fiscal:

```kotlin
enum class ReleaseChannel { PUBLIC_MVP, PILOT }

enum class ExposureClass {
    CORE_MVP,
    PILOT_ONLY,
    HIDDEN_READY,
    FUTURE,
    REMOVE_FROM_EXPERIENCE,
}

interface FeatureExposurePolicy {
    fun isExposed(feature: String, channel: ReleaseChannel): Boolean
}
```

Política conceitual:

| Classe | `PUBLIC_MVP` | `PILOT` |
|---|---:|---:|
| `CORE_MVP` | sim | sim |
| `PILOT_ONLY` | não | sim, por allowlist |
| `HIDDEN_READY` | não | não, salvo ferramenta interna explícita |
| `FUTURE` | não | não |
| `REMOVE_FROM_EXPERIENCE` | não | não; somente diagnóstico interno |

`BusinessProfile` continua responsável por contexto e configuração do negócio.
`FeatureExposurePolicy` decide somente o que o canal de release expõe. Nenhuma
condicional nova espalhada por `isPilot`, `isMei`, `hasNfe` ou `BusinessType`
deve substituir essa fronteira.

## Gates antes de chamar de MVP público

- [ ] Um comerciante real conclui venda, estoque, cliente, fiado e pagamento sem
      acompanhamento constante.
- [ ] Os fluxos manuais continuam funcionando offline, após restart e sem
      duplicar estoque/saldo.
- [ ] Estados de erro, sucesso, cancelamento e offline das telas core estão
      completos; o status UX atual ainda marca lacunas P0.
- [ ] A exposição pública não mostra pedidos, compras, debug, RAG, “backup” ou
      scanner fiscal como se fossem capacidades maduras.
- [ ] Voz e NF-e têm canal de piloto separado, fallback manual e métricas de
      correção/abandono.
- [ ] Não existe promessa de SERPRO Produção nem de cloud sync produtivo.
- [ ] O APK de release passa os gates técnicos e o runbook de piloto é
      executado no aparelho alvo.

## Próxima ordem — sem novas funcionalidades

1. Aprovar esta matriz de exposição.
2. Transformar a política em uma única fonte de decisão no app, sem alterar
   domínio ou adicionar capability.
3. Fazer o primeiro passe de remoção de experiência nas rotas já existentes.
4. Fechar UX P0 do núcleo manual.
5. Executar sessões reais de piloto e registrar evidência.
6. Só então promover, manter como piloto ou retirar cada camada.

## Fontes auditadas

- `specs/PRD.md`, especialmente “MVP — Pilot”, métricas e princípios local-first.
- `TINO-PRODUCT-CONSTITUTION.md` e `TINO-ARCHITECTURE.md`.
- `TINO-PROJECT-STATUS.md`, `TINO-CAPABILITY-MATRIX.md` e
  `TINO-INCOMPLETE-VALIDATION-BACKLOG.md`.
- `TINO-UX-UI-P0-HARDENING.md`, `STATUS-UX-UI.md` e
  `pilot/TINO-PILOT-RUNBOOK.md`.
- Inventário real de rotas em `TinoNavigation.kt` e superfícies em
  `TinoApp.kt`/`TinoHome.kt`.
- Backend `backend-tino`, módulos, controllers, README e o contrato
  `docs/contracts/TINO-ANDROID-GOODS-RECEIPT-API.md` no checkpoint `4737fd4`.

## Estado do trabalho

Esta auditoria não implementou novas funcionalidades, não alterou contratos,
Room, backend ou navegação. O próximo trabalho autorizado é redução/polimento
da experiência e, depois da aprovação da matriz, a implementação da política
centralizada de exposição.
