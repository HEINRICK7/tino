# TINO — Product Requirements Document

**Versão:** 0.1
**Status:** Product Discovery / Pilot
**Plataforma inicial:** Android
**Princípio:** Local-first, voice-first, intelligence-first
**Piloto:** pequeno mercadinho real

---

## 1. Visão do produto

TINO é um assistente inteligente para pequenos comerciantes.

O produto deve permitir que uma pessoa que não possui familiaridade com sistemas de gestão opere seu comércio principalmente através da **fala**, mantendo vendas, estoque, fiado, compras, fornecedores e pedidos organizados.

O TINO não deve exigir que o comerciante aprenda conceitos de ERP ou PDV.

> **O comerciante não aprende o TINO. O TINO aprende como o comerciante trabalha.**

### Proposta

**TINO — inteligência para o pequeno comércio.**

O comerciante fala.

O TINO entende, confirma, registra, aprende e ajuda a decidir.

---

# 2. Problema

Pequenos comércios ainda operam frequentemente através de:

```text
vende
 ↓
dinheiro / PIX / fiado
 ↓
caderneta
 ↓
olha a prateleira
 ↓
percebe que está acabando
 ↓
liga / WhatsApp para fornecedor
 ↓
mercadoria chega
 ↓
repõe
```

Esse processo funciona, porém produz pouca informação estruturada.

Consequências:

* estoque desconhecido;
* ruptura de produtos;
* capital parado;
* fiado difícil de acompanhar;
* compras baseadas principalmente em memória;
* pouca compreensão da margem;
* dificuldade para prever demanda;
* pedidos de clientes espalhados pelo WhatsApp;
* dependência do conhecimento do proprietário.

O TINO não pretende destruir esse comportamento.

Pretende **colocar inteligência por trás dele**.

---

# 3. Público inicial

Micro e pequenos comerciantes.

Primeiro vertical:

**mercadinhos e mercearias.**

Características importantes:

* Android como dispositivo principal;
* pode não existir computador;
* WhatsApp faz parte da operação;
* PIX, dinheiro e fiado coexistem;
* baixa tolerância a interfaces complexas;
* conectividade pode oscilar;
* conhecimento do negócio está principalmente na cabeça do proprietário.

---

# 4. Princípios de produto

## 4.1 Voice-first

A principal interface operacional é voz.

Exemplos:

> “Tino, chegaram duas caixas de café Maratá com 24 unidades cada.”

> “João levou dois cafés fiado.”

> “Vendi três leites e um açúcar no PIX.”

> “Quanto vendi hoje?”

> “O que está acabando?”

> “O que preciso comprar amanhã?”

---

## 4.2 Local-first

O funcionamento básico do comércio NÃO depende da nuvem.

Devem funcionar localmente:

* vendas;
* estoque;
* fiado;
* clientes;
* produtos;
* compras;
* fornecedores;
* consultas básicas;
* histórico operacional.

---

## 4.3 Cloud-backed

Local-first não significa local-only.

O TINO deve manter uma cópia sincronizada dos dados importantes.

Objetivos:

* recuperação após perda do celular;
* recuperação após quebra;
* troca de aparelho;
* múltiplos dispositivos futuramente;
* backup;
* WhatsApp;
* inteligência agregada.

---

# 5. Arquitetura conceitual

```text
                     TINO ANDROID
                          │
              ┌───────────┴───────────┐
              │                       │
             🎙️                     UI
              │                       │
              ▼                       │
       LIVE TRANSCRIBER               │
              │                       │
              ▼                       │
      transcript committed            │
              │                       │
              ▼                       │
       GEMMA ORCHESTRATOR ◄───────────┘
              │
           Tool Call
              │
              ▼
       COMMERCE RUNTIME
              │
 ┌────────────┼────────────┐
 ↓            ↓            ↓
Venda       Estoque       Fiado
 ↓            ↓            ↓
Cliente     Compras     Financeiro
              │
              ▼
          LOCAL DATA
              │
              ▼
          SYNC ENGINE
              │
        internet disponível
              │
              ▼
          TINO CLOUD
```

---

# 6. Stack Android

## Linguagem

**Kotlin**

Não haverá camada Java de aplicação salvo dependência externa inevitável.

## Interface

**Jetpack Compose**

UI declarativa e adaptada a diferentes tamanhos de Android.

## Design System

**Material 3**

Customizado para TINO.

Características:

* botões grandes;
* tipografia grande;
* contraste elevado;
* poucas ações por tela;
* feedback visual e sonoro;
* áreas de toque grandes;
* navegação rasa.

## Concorrência

**Kotlin Coroutines**

## Estado reativo

**Kotlin Flow / StateFlow**

## Persistência

**Room + SQLite**

Room será a abstração principal sobre o banco local.

## Dependency Injection

**Hilt**

## Background jobs

**WorkManager**

Responsável por:

* sincronização;
* retries;
* upload de eventos;
* download de alterações;
* manutenção;
* backups auxiliares.

---

# 7. Arquitetura Android

```text
app
│
├── core
│   ├── database
│   ├── network
│   ├── sync
│   ├── speech
│   ├── model
│   ├── ui
│   └── common
│
├── feature
│   ├── home
│   ├── voice
│   ├── sales
│   ├── stock
│   ├── credit
│   ├── purchases
│   ├── suppliers
│   ├── customers
│   ├── fiscal
│   └── settings
│
└── domain
    ├── sales
    ├── inventory
    ├── credit
    ├── purchasing
    └── catalog
```

Dependências devem apontar para abstrações.

UI não contém regras de negócio.

Gemma não contém regras de negócio.

Transcriber não contém regras comerciais.

---

# 8. Banco local

Entidades iniciais:

```text
Product
Customer
Supplier

Sale
SaleItem

CreditAccount
CreditEntry

Purchase
PurchaseItem

InventoryItem
StockMovement

FiscalDocument

DomainEvent

SyncState
```

Cada operação relevante deve produzir um `DomainEvent`.

Exemplo:

```json
{
  "event_id": "uuid",
  "type": "stock.received",
  "entity_id": "uuid",
  "device_id": "uuid",
  "occurred_at": "...",
  "sync_status": "pending"
}
```

---

# 9. Sincronização

A sincronização é requisito de primeira classe.

## Regra principal

> **Local write first. Cloud sync second.**

Nunca:

```text
Android
 ↓
esperar API
 ↓
salvar
```

Sempre:

```text
ação
 ↓
transação SQLite
 ↓
DomainEvent
 ↓
UI responde
 ↓
Sync Queue
 ↓
Cloud
```

---

# 10. Estados de sincronização

Cada evento pode estar:

```text
LOCAL
PENDING
SYNCING
SYNCED
FAILED
CONFLICT
```

Falha de internet não impede operação.

---

# 11. Idempotência

Cada operação recebe UUID.

Exemplo:

```text
sale_id
event_id
device_id
```

Se o celular enviar duas vezes:

```text
evt_873
evt_873
```

o servidor processa apenas uma vez.

Isso é obrigatório para operações financeiras e estoque.

---

# 12. Recuperação de dispositivo

Cenário:

> celular caiu e quebrou.

Novo celular:

```text
Instala TINO
 ↓
Autentica
 ↓
Seleciona estabelecimento
 ↓
Cloud Snapshot
 ↓
Eventos posteriores
 ↓
Reconstrói banco local
 ↓
TINO pronto
```

Meta futura:

**recuperação completa em poucos minutos.**

---

# 13. Voz

O TINO não implementará seu próprio Speech-to-Text.

Será consumidor do nosso **Live Transcriber**.

```text
Microfone
 ↓
Live Transcriber
 ↓
partial
 ↓
revised
 ↓
committed
 ↓
TINO
```

Somente transcrição `committed` pode iniciar uma operação comercial.

---

# 14. Gemma

Gemma será responsável por:

* compreensão da intenção;
* extração de entidades;
* seleção de ferramentas;
* diálogo;
* resolução de ambiguidades;
* transformação de linguagem natural em comandos estruturados.

Gemma NÃO será responsável por:

* calcular saldo;
* alterar estoque diretamente;
* determinar valores financeiros;
* emitir nota diretamente;
* ser fonte de verdade;
* executar SQL.

---

# 15. Tool Calling

Ferramentas iniciais:

```text
search_product
search_customer

register_sale
register_credit_sale

register_stock_receipt
check_stock

get_customer_balance
register_credit_payment

get_today_sales

prepare_purchase
find_supplier
```

Exemplo:

```text
"João levou dois cafés fiado."
```

Gemma:

```json
{
  "tool": "register_credit_sale",
  "customer": "João",
  "items": [
    {
      "product": "Café",
      "quantity": 2
    }
  ]
}
```

Commerce Runtime valida antes da execução.

---

# 16. Confirmação humana

Operações relevantes seguem:

```text
fala
 ↓
interpretação
 ↓
preview
 ↓
CONFIRMAÇÃO
 ↓
execução
```

Exemplo:

> Entendi:
>
> João
> 2 × Café Maratá
> Total: R$16,00
> Fiado
>
> **Confirmar?**

---

# 17. Entrada de mercadoria

Existem duas fontes principais.

## Com documento fiscal

Prioridade:

**XML NF-e → dados estruturados.**

Extrair:

* fornecedor;
* produtos;
* GTIN;
* NCM;
* unidades;
* quantidades;
* custos;
* total;
* chave da nota.

O documento fiscal mantém sua proveniência.

## Sem documento estruturado

Voice-first.

Exemplo:

> “Chegaram duas caixas de café Maratá com 24 em cada.”

Transcriber → Gemma → Stock Intake → confirmação.

---

# 18. Catálogo progressivo

Não exigir cadastro inicial completo.

O catálogo cresce através de:

```text
NF-e
+
entrada por voz
+
vendas
```

Produto desconhecido:

> Novo produto encontrado:
>
> Café Maratá Tradicional 250g
>
> **Criar produto?**

O relacionamento com códigos fiscais/GTIN é persistido para reconhecimento futuro.

---

# 19. Fiado

Fiado é entidade de primeira classe.

Fluxos:

```text
criar fiado
consultar saldo
registrar pagamento
consultar histórico
enviar cobrança
```

Nunca classificar moralmente clientes.

Inteligência pode indicar:

* tempo médio para pagamento;
* saldo;
* atraso;
* comportamento histórico.

Decisão continua humana.

---

# 20. Aplicativo do comerciante

Mobile Android.

Home inicial:

```text
┌──────────────────────────┐
│ TINO                     │
│                          │
│ Bom dia 👋               │
│                          │
│ O que você quer fazer?   │
│                          │
│        ┌────────┐        │
│        │   🎙️   │        │
│        │  FALAR │        │
│        └────────┘        │
│                          │
│ ⚠ 3 coisas precisam     │
│   da sua atenção         │
│                          │
│ Hoje: R$ 847             │
└──────────────────────────┘
```

Não deve parecer ERP.

---

# 21. Cliente

Não haverá aplicativo obrigatório para consumidor no MVP.

Canal:

**WhatsApp.**

Cliente pode:

* escrever;
* mandar áudio;
* consultar produtos;
* perguntar preços;
* montar pedido;
* informar localização;
* confirmar;
* acompanhar pedido.

---

# 22. Pedidos pelo WhatsApp

Exemplo:

> 🎙️ “Manda dois café, três leite e um açúcar.”

TINO:

> Seu pedido ficou:
>
> 2 × Café Maratá
> 3 × Leite
> 1 × Açúcar
>
> **Total R$42,70**
>
> Posso confirmar?

Cliente:

> Pode.

Pedido entra no aplicativo do comerciante.

---

# 23. Entrega

MVP:

```text
RETIRADA
ou
ENTREGA
```

Para zona rural, endereço pode ser:

* localização GPS;
* ponto salvo;
* referência textual.

Exemplo:

```text
Casa da Maria
📍 localização salva

Referência:
depois da igreja, muro azul
```

---

# 24. Inteligência / Machine Learning

TINO deve nascer preparado para ML, mas não fingirá inteligência sem dados.

Todos os eventos relevantes serão armazenados.

Objetivos futuros:

### Demand Forecasting

Prever demanda por produto.

### Stockout Prediction

Prever ruptura.

### Replenishment Recommendation

Recomendar reposição.

### Market Basket Analysis

Identificar produtos comprados juntos.

### Slow Moving Inventory

Identificar estoque parado.

### Customer Recurrence

Identificar padrões recorrentes.

### Purchase Optimization

Aprender quantidade e frequência ideal de compra.

---

# 25. Experiência inteligente

O objetivo não é dashboard.

O objetivo é ação.

Em vez de:

> gráfico de estoque.

TINO diz:

> **Café Maratá deve acabar amanhã.**

Em vez de:

> relatório de vendas.

TINO diz:

> **Refrigerante vende 43% mais aos sábados.**

Em vez de:

> análise de inventário.

TINO diz:

> **Não compre mais desse biscoito agora. Ainda existem 18 e você vendeu apenas 3 este mês.**

---

# 26. Fiscal

Fiscal deve ser um bounded context próprio.

Nunca acoplar lógica tributária ao domínio central de vendas.

```text
Commerce Runtime
       │
       ▼
   Fiscal Port
       │
       ▼
Fiscal Provider
```

NF-e/NFC-e e demais obrigações serão implementadas conforme enquadramento e validação contábil/fiscal.

---

# 27. Segurança

Obrigatório:

* banco local protegido;
* tokens em Android Keystore;
* TLS;
* autenticação;
* device identity;
* logs auditáveis;
* backups;
* revogação de dispositivo;
* dados financeiros nunca em logs comuns.

---

# 28. Observabilidade

Registrar:

```text
command received
intent detected
tool selected
confirmation
domain operation
sync status
ML recommendation
user acceptance/rejection
```

Sem armazenar dados sensíveis desnecessariamente.

---

# 29. MVP — Pilot

O primeiro TINO NÃO terá tudo.

## P0

* Android;
* Room;
* offline-first;
* sincronização;
* produtos;
* estoque;
* clientes;
* fiado;
* fornecedores;
* entrada de mercadoria;
* vendas básicas;
* Live Transcriber;
* Gemma orchestration;
* confirmação por voz/UI;
* recuperação de dispositivo.

## P1

* NF-e XML;
* catálogo progressivo;
* pedidos WhatsApp;
* retirada/entrega;
* localização;
* lista de compras;
* fornecedor via WhatsApp.

## P2

* previsão de demanda;
* ruptura;
* recomendação de compra;
* produtos associados;
* recorrência;
* estoque parado.

---

# 30. Métricas do piloto

Não medir downloads.

Medir comportamento real.

### Adoção

* comandos de voz/dia;
* operações confirmadas;
* operações corrigidas;
* operações abandonadas.

### Voz

* intent accuracy;
* entity accuracy;
* product matching accuracy;
* correction rate.

### Operação

* divergência de estoque;
* produtos em ruptura;
* tempo para registrar mercadoria;
* fiado registrado;
* pedidos recebidos.

### Inteligência

* recomendações produzidas;
* recomendações aceitas;
* ruptura evitada;
* desperdício reduzido.

### Negócio

* vendas;
* ticket médio;
* frequência;
* margem estimada;
* recompra.

---

# 31. North Star

A principal métrica não será:

**quantas funcionalidades o TINO possui.**

Será:

> **Quanto trabalho e quantas decisões o TINO consegue retirar das mãos do pequeno comerciante sem retirar dele o controle do negócio.**

---

# 32. Visão

Hoje:

```text
comerciante
 ↓
caderno
 ↓
memória
 ↓
WhatsApp
 ↓
prateleira
```

TINO:

```text
             COMERCIANTE
                  │
                 🎙️
                  │
                  ▼
                TINO
                  │
       ┌──────────┼───────────┐
       ↓          ↓           ↓
     organiza   aprende     lembra
       │          │           │
       └──────────┼───────────┘
                  ↓
               recomenda
                  │
                  ▼
            COMERCIANTE DECIDE
```

**Falou. O TINO cuidou.**
