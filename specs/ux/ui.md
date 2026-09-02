> Constituição obrigatória: [TINO-ZERO-DOUBT-UX.md](../TINO-ZERO-DOUBT-UX.md).
> Esta referência visual não substitui os gates de clareza, estado, voz,
> offline e aparelho real definidos naquele documento.
> Voz inline usa `TinoInlineVoiceInput` e não navega para `Voice`.

A navegação principal teria apenas 4 áreas:

┌───────────────────────────────┐
│                               │
│            CONTEÚDO           │
│                               │
├───────────────────────────────┤
│ 🏠 Hoje   📦 Produtos         │
│ 📒 Fiado  ☰ Mais              │
└───────────────────────────────┘

E o botão de voz 🎙️ Falar com o TINO permanece disponível nas telas em que fizer sentido.

01 — Splash

Objetivo: abrir extremamente rápido.

┌───────────────────────────────┐
│                               │
│                               │
│            TINO               │
│                               │
│   Inteligência para o         │
│   pequeno comércio            │
│                               │
│                               │
└───────────────────────────────┘

Sem animação longa.

Tempo ideal: praticamente imperceptível.

02 — Primeiro acesso

Não quero onboarding de 8 telas.

┌───────────────────────────────┐
│ TINO                          │
│                               │
│ Vamos preparar seu comércio.  │
│                               │
│ Nome do comércio              │
│ [ Mercadinho __________ ]     │
│                               │
│ Seu nome                      │
│ [ _____________________ ]     │
│                               │
│ Celular                       │
│ [(86) ________________ ]      │
│                               │
│       [ CONTINUAR ]           │
└───────────────────────────────┘

Depois autenticação simples e criação/restauração da loja.

03 — Restaurar loja

Essa tela é fundamental por causa da sincronização.

┌───────────────────────────────┐
│ ← Recuperar meu comércio      │
│                               │
│ Encontramos:                  │
│                               │
│ 🏪 Mercadinho São José        │
│ Último backup: hoje 14:32     │
│                               │
│ Produtos       428            │
│ Clientes        67            │
│ Vendas       salvas           │
│                               │
│   [ RESTAURAR NESTE CELULAR ] │
└───────────────────────────────┘

Nada de mostrar termos como snapshot, database ou sync cursor.

04 — HOME / HOJE

Essa é a tela mais importante do produto.

┌───────────────────────────────┐
│ TINO                ✓ salvo   │
│                               │
│ Bom dia, João 👋              │
│                               │
│      O que quer fazer?        │
│                               │
│       ┌───────────────┐       │
│       │      🎙️      │       │
│       │ FALAR COM     │       │
│       │   O TINO      │       │
│       └───────────────┘       │
│                               │
│ Hoje                          │
│                               │
│ 💰 R$ 847,50 vendidos         │
│ 📒 R$ 120,00 no fiado         │
│ 🛒 27 vendas                  │
│                               │
│ ⚠️ TINO percebeu 3 coisas     │
│                               │
│ Café pode acabar amanhã       │
│ [ VER ]                       │
│                               │
├───────────────────────────────┤
│ 🏠 Hoje 📦 Produtos 📒 Fiado ☰│
└───────────────────────────────┘

O comerciante não vê dashboard.

Ele vê:

quanto vendeu + o que precisa fazer + botão de voz.

05 — Falar com o TINO

Ao tocar no microfone:

┌───────────────────────────────┐
│ ×                             │
│                               │
│                               │
│              🎙️              │
│                               │
│          Estou ouvindo        │
│                               │
│ "Chegou uma caixa de          │
│  café Maratá..."              │
│                               │
│                               │
│         [ TERMINAR ]          │
└───────────────────────────────┘

Partial/revised aparecem como legenda.

Nada é registrado ainda.

06 — TINO entendeu

Depois do committed + Gemma:

┌───────────────────────────────┐
│ TINO entendeu                 │
│                               │
│ 📦 Entrada de mercadoria      │
│                               │
│ Café Maratá 250g              │
│                               │
│ 1 caixa                       │
│ 24 unidades                   │
│                               │
│ Estoque atual        8        │
│ Depois ficará       32        │
│                               │
│ Isso está certo?              │
│                               │
│ [ NÃO ]      [ ✓ SIM ]        │
└───────────────────────────────┘

Essa tela aparece para qualquer operação que altere negócio.

07 — Correção da interpretação

Se ele tocar NÃO:

┌───────────────────────────────┐
│ O que está errado?            │
│                               │
│ Produto                       │
│ Café Maratá 250g       [✎]    │
│                               │
│ Caixas                  1 [−+]│
│                               │
│ Unidades por caixa      24    │
│                               │
│ [ 🎙️ CORRIGIR FALANDO ]      │
│                               │
│        [ SALVAR ]             │
└───────────────────────────────┘

Importante: permitir corrigir por voz novamente.

08 — Operação concluída
┌───────────────────────────────┐
│                               │
│             ✓                 │
│                               │
│        Entrada registrada     │
│                               │
│ Café Maratá                   │
│ +24 unidades                  │
│                               │
│ Estoque: 32                   │
│                               │
│         [ PRONTO ]            │
└───────────────────────────────┘

2 segundos e volta à Home automaticamente.

09 — Venda rápida

Apesar de voice-first, precisamos da opção manual.

┌───────────────────────────────┐
│ ← Nova venda                  │
│                               │
│ 🔎 Procurar     📷 Código     │
│                               │
│ Mais vendidos                 │
│                               │
│ [☕ Café] [🥛 Leite]          │
│ [🍚 Arroz] [🥤 Coca]          │
│ [🫘 Feijão] [🧼 Sabão]        │
│                               │
│ ───────────────────────────   │
│ Coca-Cola 2L             2    │
│ Café Maratá              1    │
│                               │
│ TOTAL              R$ 29,00   │
│                               │
│       [ RECEBER ]             │
└───────────────────────────────┘
10 — Receber venda
┌───────────────────────────────┐
│ Como recebeu?                 │
│                               │
│ ┌───────────────────────────┐ │
│ │ 💵 DINHEIRO               │ │
│ └───────────────────────────┘ │
│                               │
│ ┌───────────────────────────┐ │
│ │ ⚡ PIX                    │ │
│ └───────────────────────────┘ │
│                               │
│ ┌───────────────────────────┐ │
│ │ 📒 FIADO                  │ │
│ └───────────────────────────┘ │
│                               │
│ Total R$ 29,00                │
└───────────────────────────────┘

Não enterraria esses métodos em select/dropdown.

11 — Escolher cliente para fiado
┌───────────────────────────────┐
│ ← Fiado                       │
│                               │
│ 🔎 Quem está levando?         │
│                               │
│ João Ferreira                 │
│ Deve R$ 72,00                 │
│                               │
│ Maria                         │
│ Deve R$ 18,50                 │
│                               │
│ Antônio                       │
│ Sem dívida                    │
│                               │
│ + Novo cliente                │
└───────────────────────────────┘
12 — Confirmar fiado
┌───────────────────────────────┐
│ 📒 João                       │
│                               │
│ Esta compra       R$ 29,00    │
│ Já devia          R$ 72,00    │
│ ───────────────────────────   │
│ Ficará devendo   R$ 101,00    │
│                               │
│ [ CANCELAR ]   [ ANOTAR ]     │
└───────────────────────────────┘

Palavra ANOTAR, não "lançar débito".

13 — Fiado / Caderneta
┌───────────────────────────────┐
│ 📒 Fiado                      │
│                               │
│ Total em aberto               │
│ R$ 1.420,70                   │
│                               │
│ 🔎 Procurar pessoa            │
│                               │
│ João Ferreira          R$101  │
│ Maria                  R$ 82  │
│ Antônio                R$ 47  │
│ Francisco              R$230  │
│                               │
├───────────────────────────────┤
│ 🏠 Hoje 📦 Produtos 📒 Fiado ☰│
└───────────────────────────────┘
14 — Conta do cliente
┌───────────────────────────────┐
│ ← João Ferreira               │
│                               │
│ Está devendo                  │
│ R$ 101,00                     │
│                               │
│ [ 💰 RECEBEU PAGAMENTO ]      │
│                               │
│ 16 ago                        │
│ Compra               +R$29    │
│                               │
│ 10 ago                        │
│ Compra               +R$22    │
│                               │
│ 05 ago                        │
│ Pagamento            -R$50    │
│                               │
│ [ ENVIAR PELO WHATSAPP ]      │
└───────────────────────────────┘
15 — Receber pagamento do fiado
┌───────────────────────────────┐
│ João pagou quanto?            │
│                               │
│         R$                    │
│       [ 50,00 ]               │
│                               │
│ Forma                         │
│                               │
│ [💵 Dinheiro] [⚡ PIX]        │
│                               │
│ Antes            R$101,00     │
│ Pagamento        R$ 50,00     │
│ Depois           R$ 51,00     │
│                               │
│       [ CONFIRMAR ]           │
└───────────────────────────────┘
16 — Produtos
┌───────────────────────────────┐
│ 📦 Produtos                   │
│                               │
│ 🔎 Procurar produto           │
│                               │
│ ⚠️ 4 acabando                 │
│ ❌ 2 acabaram                 │
│                               │
│ Café Maratá                   │
│ 6 unidades           ⚠️       │
│                               │
│ Leite                         │
│ 18 unidades                   │
│                               │
│ Açúcar                        │
│ 0 unidades            ❌      │
│                               │
│ + ADICIONAR PRODUTO           │
└───────────────────────────────┘
17 — Produto
┌───────────────────────────────┐
│ ← Café Maratá 250g            │
│                               │
│ [ FOTO ]                      │
│                               │
│ Venda              R$ 8,50    │
│ Custo médio        R$ 6,20    │
│                               │
│ Estoque                       │
│ 6 unidades                    │
│                               │
│ ⚠️ Deve acabar amanhã         │
│                               │
│ Este mês                      │
│ 74 vendidos                   │
│                               │
│ [ ALTERAR PREÇO ]             │
│ [ AJUSTAR ESTOQUE ]           │
└───────────────────────────────┘

ML aparece como uma frase útil.

18 — Ajustar estoque
┌───────────────────────────────┐
│ Ajustar Café Maratá           │
│                               │
│ Sistema diz: 6                │
│                               │
│ Quantos tem de verdade?       │
│                               │
│            [ 5 ]              │
│                               │
│ Motivo                        │
│                               │
│ ○ Contagem                    │
│ ○ Quebrou/perdeu              │
│ ○ Outro                       │
│                               │
│       [ CORRIGIR ]            │
└───────────────────────────────┘

Nunca sobrescrever silenciosamente: gera movimento de ajuste.

19 — Produto novo

Pode surgir por NF-e, voz ou manualmente.

┌───────────────────────────────┐
│ Novo produto                  │
│                               │
│ 📷 Tirar foto                 │
│                               │
│ Nome                          │
│ [ Café Maratá ]               │
│                               │
│ Tamanho                       │
│ [ 250g ]                      │
│                               │
│ Preço de venda                │
│ [ R$ 8,50 ]                   │
│                               │
│       [ CRIAR ]               │
└───────────────────────────────┘
20 — Escanear nota

Entrada de mercadoria deve ter destaque.

┌───────────────────────────────┐
│ Mercadoria chegou?            │
│                               │
│ Como quer registrar?          │
│                               │
│ ┌───────────────────────────┐ │
│ │ 📄 USAR NOTA FISCAL       │ │
│ │ Ler os produtos da nota   │ │
│ └───────────────────────────┘ │
│                               │
│ ┌───────────────────────────┐ │
│ │ 🎙️ FALAR                 │ │
│ │ Não tem nota? Conte       │ │
│ │ para o TINO               │ │
│ └───────────────────────────┘ │
└───────────────────────────────┘
21 — Nota encontrada
┌───────────────────────────────┐
│ Nota encontrada ✓             │
│                               │
│ Distribuidora São Paulo       │
│ 16/08/2026                    │
│                               │
│ 18 produtos                   │
│ Total R$ 1.284,30             │
│                               │
│ Café Maratá          24       │
│ Leite                48       │
│ Açúcar               20       │
│ + 15 produtos                 │
│                               │
│ [ VER PRODUTOS ]              │
│                               │
│ [ DAR ENTRADA EM TUDO ]       │
└───────────────────────────────┘
22 — Conferência da nota
┌───────────────────────────────┐
│ ← Conferir produtos           │
│                               │
│ ✓ Café Maratá       24        │
│ ✓ Leite             48        │
│ ✓ Açúcar            20        │
│ ⚠ Produto novo       6        │
│                               │
│ Produto novo:                 │
│ Biscoito XYZ                  │
│                               │
│ [ REVISAR ]                   │
│                               │
│        [ CONFIRMAR ]          │
└───────────────────────────────┘
23 — Comprar / Reposição

Essa tela será muito importante para ML.

┌───────────────────────────────┐
│ 🛒 O que comprar              │
│                               │
│ TINO sugere                   │
│                               │
│ ⚠ Café Maratá                 │
│ Restam 6                      │
│ Sugestão: 2 caixas            │
│ [✓]                           │
│                               │
│ ⚠ Leite                       │
│ Restam 8                      │
│ Sugestão: 3 caixas            │
│ [✓]                           │
│                               │
│ Biscoito XYZ                  │
│ Ainda tem bastante            │
│ Não comprar agora             │
│                               │
│ [ PREPARAR PEDIDO ]           │
└───────────────────────────────┘
24 — Pedido ao fornecedor
┌───────────────────────────────┐
│ Pedido                        │
│                               │
│ Distribuidora São Paulo       │
│                               │
│ 2 cx Café Maratá              │
│ 3 cx Leite                    │
│ 1 cx Óleo                     │
│                               │
│ [ + ADICIONAR PRODUTO ]       │
│                               │
│ [ ENVIAR NO WHATSAPP ]        │
└───────────────────────────────┘

No MVP, WhatsApp pode ser a integração.

25 — Fornecedores
┌───────────────────────────────┐
│ 🚚 Fornecedores               │
│                               │
│ Distribuidora São Paulo       │
│ Última compra: 12 ago         │
│                               │
│ Atacadão XYZ                  │
│ Última compra: 08 ago         │
│                               │
│ João dos Ovos                 │
│ Última compra: ontem          │
│                               │
│ + NOVO FORNECEDOR             │
└───────────────────────────────┘
26 — Pedidos dos clientes

Pedidos do WhatsApp chegam aqui.

┌───────────────────────────────┐
│ 🛍️ Pedidos                    │
│                               │
│ 🔴 NOVO                       │
│ Maria                         │
│ R$ 42,70                      │
│ Entrega                       │
│                               │
│ 🟡 SEPARANDO                  │
│ Francisco                     │
│ R$ 71,20                      │
│ Retirada                      │
│                               │
│ 🟢 PRONTO                     │
│ Antônio                       │
│ R$ 28,00                      │
└───────────────────────────────┘
27 — Detalhe do pedido
┌───────────────────────────────┐
│ Pedido #028                   │
│                               │
│ Maria                         │
│                               │
│ 2 Café Maratá                 │
│ 3 Leite                       │
│ 1 Açúcar                      │
│                               │
│ Total             R$42,70     │
│                               │
│ 🛵 Entrega                    │
│ 📍 Casa da Maria              │
│                               │
│ [ NÃO TENHO ITEM ]            │
│                               │
│ [ COMEÇAR A SEPARAR ]         │
└───────────────────────────────┘
28 — Separando pedido
┌───────────────────────────────┐
│ Separar pedido                │
│                               │
│ [✓] 2 Café Maratá             │
│ [✓] 3 Leite                   │
│ [ ] 1 Açúcar                  │
│                               │
│ 2 de 3 prontos                │
│                               │
│        [ PEDIDO PRONTO ]      │
└───────────────────────────────┘

Muito simples.

29 — Entrega
┌───────────────────────────────┐
│ Entregar para Maria           │
│                               │
│ 📍 Casa da Maria              │
│ 1,8 km                        │
│                               │
│ Referência                    │
│ Depois da igreja, muro azul   │
│                               │
│ [ ABRIR MAPA ]                │
│                               │
│ Pagamento                     │
│ PIX                           │
│                               │
│ [ SAIU PARA ENTREGA ]         │
└───────────────────────────────┘
30 — TINO percebeu

Essa é a nossa tela de inteligência.

Não chamaria de "Analytics".

┌───────────────────────────────┐
│ 🧠 TINO percebeu              │
│                               │
│ ⚠ Café pode acabar amanhã     │
│ [ Comprar ]                   │
│                               │
│ 📈 Refrigerante vende 38%     │
│ mais aos sábados              │
│ [ Ver ]                       │
│                               │
│ 💤 Esse biscoito vende pouco  │
│ Ainda existem 18 unidades     │
│ [ Ver produto ]               │
│                               │
│ 🔁 Maria costuma comprar      │
│ novamente nesta semana        │
└───────────────────────────────┘

Esse módulo precisa parecer conselhos, não BI.

31 — Resumo do dia
┌───────────────────────────────┐
│ Hoje                          │
│                               │
│ Vendeu                        │
│ R$ 847,50                     │
│                               │
│ 27 vendas                     │
│                               │
│ Dinheiro       R$ 320,00      │
│ PIX            R$ 427,50      │
│ Fiado          R$ 100,00      │
│                               │
│ Produto que mais vendeu       │
│ Coca-Cola 2L                  │
│                               │
│ [ OUVIR RESUMO 🎙️ ]          │
└───────────────────────────────┘

O botão Ouvir resumo é importante para nosso público.

TINO pode responder:

“Hoje você vendeu oitocentos e quarenta e sete reais...”

32 — Perguntar ao TINO

Além do botão principal, teremos uma interface de conversa/histórico.

┌───────────────────────────────┐
│ ← TINO                        │
│                               │
│ Você                          │
│ Quanto vendi hoje?            │
│                               │
│ TINO                          │
│ R$ 847,50 em 27 vendas.       │
│                               │
│ Você                          │
│ Quanto João deve?             │
│                               │
│ TINO                          │
│ João deve R$101,00.           │
│                               │
│                               │
│       🎙️ FALE                │
└───────────────────────────────┘

Não precisa parecer ChatGPT. É histórico operacional.

33 — Sincronização

Não deve ocupar espaço normalmente.

Ao tocar no ✓ salvo:

┌───────────────────────────────┐
│ Seus dados                    │
│                               │
│ ✓ Tudo protegido              │
│                               │
│ Última sincronização          │
│ agora                         │
│                               │
│ Este aparelho                 │
│ Samsung Galaxy ...            │
│                               │
│ Seus dados também estão       │
│ guardados com segurança.      │
└───────────────────────────────┘

Offline:

Sem internet.


Pode continuar trabalhando.


17 alterações serão protegidas
assim que a conexão voltar.
34 — Mais
┌───────────────────────────────┐
│ Mais                          │
│                               │
│ 🛍️ Pedidos                    │
│ 🚚 Fornecedores               │
│ 🛒 Comprar                    │
│ 📊 Resumo                     │
│ 📄 Notas                      │
│ 👥 Clientes                   │
│ ⚙️ Configurações              │
│                               │
├───────────────────────────────┤
│ 🏠 Hoje 📦 Produtos 📒 Fiado ☰│
└───────────────────────────────┘
35 — Configurações

Muito pequena.

Meu comércio
Minha conta
WhatsApp
Pagamentos
Impressora
Notas fiscais
Backup
Som e voz
Acessibilidade
Ajuda

Sem 40 preferências.

36 — Estado offline

O app inteiro precisa ter uma linguagem consistente.

Topo:

🟠 Sem internet

E nada mais.

Nunca modal bloqueando trabalho.

37 — Estado de erro de voz

Se o TINO não entendeu:

┌───────────────────────────────┐
│ 🤔 Não entendi direito        │
│                               │
│ Ouvi:                         │
│                               │
│ "chegou caixa maratá..."      │
│                               │
│ Pode falar novamente?         │
│                               │
│      [ 🎙️ FALAR DE NOVO ]    │
│                               │
│      [ FAZER MANUALMENTE ]    │
└───────────────────────────────┘

Nunca:

“Confidence 0.43.”

38 — Estado de ambiguidade

Exemplo:

“Vendi um café.”

Existem vários cafés.

Qual café?


[ FOTO ]
Maratá 250g
R$8,50


[ FOTO ]
Santa Clara 250g
R$9,20


[ FOTO ]
Maratá 500g
R$15,00


[ 🎙️ É O MARATÁ PEQUENO ]

Essa tela será extremamente importante para Gemma + Product Matching.

39 — Notificação importante

Exemplo:

TINO


⚠️ Café Maratá pode acabar amanhã.


Você costuma comprar 2 caixas.


[ VER ]

Poucas notificações.

Só quando existe ação realmente útil.

40 — Padrão visual

Eu faria o TINO claro, não dark-first.

Características:

Fundo: branco/quase branco.
Marca: verde profundo.
Ação principal: verde.
Atenção: âmbar.
Problema: vermelho somente quando necessário.
Cards: poucos e grandes.
Radius: moderado, não exageradamente "startup".
Ícones: extremamente reconhecíveis.

Tipografia:

Título          26–30sp
Número grande   28–36sp
Texto           17–19sp
Botão           18sp+
Legenda         >= 15sp

Área de toque: grande, especialmente nos fluxos utilizados durante o trabalho.

A UX inteira pode ser resumida em 6 verbos

O comerciante precisa entender apenas:

🎙️ FALAR
🛒 VENDER
📦 PRODUTOS
📒 FIADO
🚚 COMPRAR
🛍️ PEDIDOS

Todo o restante fica por trás disso.

E eu faria uma regra de design especialmente forte para o TINO:

Nenhuma tela operacional pode exigir que o comerciante entenda como o sistema funciona. Ela precisa perguntar apenas aquilo que ele já sabe sobre o próprio comércio.

Por exemplo, nunca perguntamos tipo de movimento de estoque. Perguntamos “quantos chegaram?”.

Nunca criar lançamento no contas a receber. Perguntamos “João levou fiado?”.

Nunca registrar baixa. Perguntamos “quanto ele pagou?”.

Isso é o que vai separar o TINO de um PDV comum.
