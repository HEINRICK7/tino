# TINO Intelligence System

Especificação da Inteligência de Negócio, Contextual, Relacional e Preditiva

**Status:** EM IMPLEMENTAÇÃO — núcleo executável integrado  
**Produto:** TINO  
**Objetivo:** definir as inteligências que formam o cérebro do TINO sem transformar o produto em um ERP complexo ou em uma IA que faz inferências sem evidência.

**Testes desta base:** 556 testes unitários do app e 32 do módulo fiscal passaram em 2026-08-28.

**Checkpoint 2026-08-28 — núcleo Evidence → Insight → Attention:**

Evidência detalhada: [TINO-EVIDENCE-INTELLIGENCE-CORE-2026-08-28.md](TINO-EVIDENCE-INTELLIGENCE-CORE-2026-08-28.md).

- `TinoEvidenceSnapshotBuilder` monta o contexto a partir do `IntelligenceFactsPort`, incluindo histórico de estoque, comparação de 30 dias, dia da semana, recebimentos semanais, comportamento de pagamento e memória governada;
- o mesmo contexto agrega recorrência de compras de clientes e histórico de compras por fornecedor, incluindo custo anterior/atual e associação ao produto;
- pedidos de compra agora registram data prevista e recebimento real; o TINO sinaliza entregas atrasadas, próximas e padrões de pontualidade somente quando esses fatos estão persistidos;
- `TinoEvidenceEngine.analyze` materializa todas as evidências candidatas antes do ranking e liga cada insight às evidências que o justificam;
- cada evidência preserva, além do texto explicativo, os valores observados que sustentaram a conclusão (estoque, vendas, saldo, datas, recebimentos, fornecedor, recomendação ou memória), distinguindo `ROOM`, `DERIVED` e `BUSINESS_MEMORY`;
- o ranking também registra relevância contextual, impacto no negócio, horizonte temporal e momento de geração do insight;
- `TinoDailySalesStatistics` compara o último dia observado com uma linha de base de dias anteriores, exigindo amostra mínima e expondo média, desvio, z-score, confiança e a ausência de causalidade;
- `TinoWeekdaySalesStatistics` só promove sazonalidade quando há histórico mínimo e ocorrências repetidas do dia comparado, expondo médias, uplift e confiança;
- `TinoDemandForecastStatistics` estima a demanda dos próximos sete dias com média, dispersão e faixa de incerteza, recusando-se a preencher datas sem observação;
- `TinoDemandRegressionModel` ajusta uma regressão linear local quando há histórico suficiente, expõe método, confiança e intervalo residual e recua para `TinoDemandForecastStatistics` quando a amostra é insuficiente;
- `TinoDemandModelValidator` faz backtesting temporal sem olhar o futuro e só promove a regressão quando o erro percentual e a cobertura do intervalo passam o gate definido;
- `LocalApprovedKnowledgeAdapter` responde dúvidas estáveis de ajuda/glossário offline com fonte versionada e mantém termos fora do corpus explicitamente indisponíveis; o catálogo aprovado valida entradas antes da ativação, mantém versão anterior para rollback e expõe versão, modo e latência na resposta;
- o catálogo aprovado agora possui adapter Room persistente, migration 25→26 e restauração após reinicialização; ativação e rollback são transacionais e continuam sujeitos à validação do catálogo;
- ranking considera relevância, urgência, novidade e confiança, com limite de três itens visíveis;
- candidatos fora do TOP N permanecem no catálogo para reconciliação; sinais que deixam de existir são resolvidos pelo Attention Engine;
- previsões e padrões são explicitamente marcados como `FORECAST`/`INFERENCE` e `SUSPECT`; ambiguidade Pix continua exigindo ação humana;
- somente memórias `LEARNED`/`TRUSTED` entram no contexto; fatos transacionais continuam no Room;
- o painel `···` consome o contexto reativo e abre ações por capability, sem mutar dados a partir de um insight;
- ações originadas por um pensamento preservam seu contexto de produto/fornecedor: consultas de estoque/reposição, lista de produto e fornecedor específico resolvem a entidade local correta, enquanto os atalhos sem sujeito continuam globais;
- `REGISTER_STOCK_ENTRY` atravessa o contrato agentic com `product_ref`, `quantity`, `unit_cost_cents` e `supplier_ref`, produz preview A2UI e permanece atrás da confirmação humana antes de chamar o use case de estoque;
- `TinoAttentionNotificationWorker` agenda um digest local periódico via WorkManager e `TinoAttentionNotificationPublisher` publica apenas atenções persistidas, cancela sinais resolvidos, com canal Android e permissão explícita;
- lint e assemble passam; no Samsung SM-A042M/API 34 autorizado, instalação, abertura, painel contextual, fallback físico e inferência real do Gemma passaram sem crash; o caminho somente-leitura do Intelligence Runtime também respondeu fatos do Room no device.

**Já no runtime (fundação, sem inteligência de objetivo):**

- Percepção contextual nos `···` (`TinoEvidenceEngine`)
- Relações Pix ↔ dívida (`TinoPaymentMatcher`; nunca dá baixa)
- Tempo / ruptura estimada (`TinoStockoutForecast`)
- Incerteza transversal (`ThoughtUncertainty`: Sei / Suspeito / Não sei)
- Explicação em linguagem simples (`why`)
- Proatividade silenciosa (sem `···` se nada passa o limiar)
- Ação só por capability A2UI
- Memória governada em `GovernedBusinessMemory`, alimentando o contexto de pensamentos apenas após promoção

**Fora de escopo agora:** inteligência de objetivos.

**Ainda não é considerado fechado:** ML de negócio aprovado além destas análises estatísticas locais, avaliação com dataset real restaurado e validação física exaustiva de todas as ações e estados do painel/notificações. A cobertura de entrega prevista/real de fornecedores já possui persistência, fluxo de pedido/recebimento, evidências e sinais na UX, mas ainda requer validação física.

---

## 1. Visão

O TINO não deve ser apenas um aplicativo que armazena produtos, clientes, dívidas, pagamentos e movimentações.

Ele deve conseguir transformar os dados do comércio em entendimento útil:

Perceber → relacionar → compreender o tempo → medir incerteza → aprender → prever → recomendar → explicar.

A inteligência deve permanecer silenciosa quando não houver algo realmente útil.

O objetivo não é mostrar que o sistema possui IA. O objetivo é ajudar o comerciante a perceber e agir sobre coisas que poderiam passar despercebidas.

## 2. Princípio central

Cada inteligência responde a uma pergunta diferente:

| Inteligência | Pergunta |
|---|---|
| Perceptiva / Contextual | O que está acontecendo agora? |
| Memória / Aprendizado | O que aprendi sobre este comércio e este usuário? |
| Temporal | Como isso se comporta ao longo do tempo? |
| Relacional | Como esses fatos estão conectados? |
| Anomalias | O que está diferente ou estranho? |
| Preditiva | O que provavelmente acontecerá? |
| Recomendação | O que seria útil fazer? |
| Proativa | Existe algo importante que merece ser sinalizado agora? |
| Explicativa | Por que o TINO está dizendo isso? |
| Incerteza | Quanto o TINO realmente sabe sobre essa conclusão? |

Essas capacidades não devem virar vários assistentes independentes. Elas formam um único TINO Intelligence Engine.

## 3. Arquitetura conceitual

```
DADOS DO NEGÓCIO
│
├── produtos
├── estoque
├── movimentações
├── clientes
├── caderneta
├── pagamentos
├── fornecedores
├── fiscal
├── recebimentos
├── histórico
├── contexto da tela
└── correções do usuário
        │
        ▼
┌──────────────────────────────┐
│       BUSINESS MEMORY        │
└──────────────┬───────────────┘
               ▼
┌───────────────────────────────────────────────┐
│            TINO INTELLIGENCE ENGINE           │
│                                               │
│ Percepção       Relações       Tempo          │
│ Anomalias       Predição       Aprendizado    │
│ Recomendação    Incerteza      Explicação     │
└──────────────────────┬────────────────────────┘
                       ▼
                EVIDENCE / INSIGHT
                       │
          ┌────────────┴────────────┐
          │ relevância              │
          │ urgência                │
          │ novidade                │
          │ confiança               │
          │ contexto                │
          └────────────┬────────────┘
                       ▼
             CONTEXTUAL RANKING
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
         ···          A2UI          VOZ
      percepção      ação       interação direta
```

## 4. Inteligência Perceptiva / Contextual

Responsável por observar o estado atual do negócio e identificar fatos relevantes dentro do contexto em que o usuário está.

Pergunta: "Existe algo aqui que merece atenção?"

Exemplos:

- Estoque: "Café Maratá está com apenas 2 unidades."
- Caderneta: "João combinou pagar hoje e ainda possui R$ 180 em aberto."
- Financeiro: "Os recebimentos desta semana estão abaixo da semana anterior."

A inteligência contextual não deve simplesmente repetir informações que já estão evidentes na tela.

Ruim: "Você possui 1 produto com estoque baixo." quando a própria tela já mostra 1 estoque baixo.

Melhor: "Café Maratá está com 2 unidades e teve 5 saídas desde ontem."

## 5. Business Memory / Inteligência de Aprendizado

Mantém conhecimento adquirido sobre o comércio, entidades, linguagem do usuário, padrões operacionais e correções confirmadas.

Pergunta: "O que o TINO já aprendeu que pode melhorar esta decisão?"

Exemplo de correção de voz:

```
"maraca"
    ↓
correção do usuário
    ↓
"Café Maratá"
    ↓
alias candidato
```

Regras:

- aprendizado deve possuir proveniência
- correções explícitas têm maior peso
- hipótese não deve automaticamente virar fato
- aprendizado reversível
- confidence deve acompanhar inferências
- tipos de persistência devem distinguir CANDIDATE, CONFIRMED, REJECTED ou equivalentes

## 6. Inteligência Temporal

Permite ao TINO interpretar o negócio como uma sequência temporal e não apenas como o estado atual das tabelas.

Pergunta: "O que o tempo muda na interpretação desse dado?"

O pequeno comércio trabalha naturalmente com hoje, ontem, amanhã, sexta, fim de semana, semana passada, começo do mês, vencido há X dias.

Exemplo: hoje é quinta + refrigerante com 6 unidades + histórico de aumento às sextas → "Amanhã costuma sair bastante refrigerante e restam apenas 6 unidades."

Isso é superior a "Refrigerante está com estoque baixo."

Capacidades: sazonalidade semanal, tendência, recência, tempo desde última movimentação, vencimentos, promessas de pagamento, comparação entre períodos, recorrência, velocidade de saída, tempo estimado até ruptura.

## 7. Inteligência Relacional

Conecta fatos que isoladamente possuem pouco significado.

Pergunta: "Esses registros estão relacionados?"

```
CLIENTE ─── DÍVIDA
   │          │
   │          └── PAGAMENTO
   │
   └── COMPORTAMENTO

PRODUTO ─── MOVIMENTAÇÃO
   │
   ├── ESTOQUE
   ├── VENDA
   └── FORNECEDOR

DATA ─── EVENTO ─── PADRÃO
```

Exemplo de conciliação: João deve R$ 180, prometeu pagar hoje, normalmente paga via Pix, entrou um Pix de R$ 180 hoje → "Este recebimento pode corresponder ao pagamento de João."

O resultado continua sendo uma hipótese até que a política de confiança permita confirmação automática ou o usuário confirme.

## 8. Inteligência de Anomalias

Detecta comportamentos significativamente diferentes do esperado.

Pergunta: "O que está fora do padrão?"

Anomalia não significa erro ou fraude. O TINO deve comunicar "Isso está diferente do padrão." e não "Isso está errado." sem evidência suficiente.

## 9. Inteligência Preditiva

Utiliza histórico e estado atual para estimar acontecimentos futuros.

Pergunta: "O que provavelmente acontecerá se o comportamento continuar?"

Toda previsão precisa ter, conforme aplicável: `prediction`, `confidence`, `evidence`, `time_horizon`, `generated_at`.

A interface deve evitar apresentar previsão como certeza.

## 10. Inteligência de Recomendação

Transforma evidências em possíveis ações úteis.

Pergunta: "Diante disso, o que poderia ajudar?"

O TINO recomenda; não executa mutações sensíveis silenciosamente. A recomendação deve passar pelo mesmo sistema de capabilities e políticas A2UI.

## 11. Inteligência Proativa

Decide quando uma evidência é importante o suficiente para ser sinalizada sem que o usuário tenha feito uma pergunta.

Os `···` significam: "O TINO percebeu algo." Não significam necessariamente alerta, erro ou problema.

Se nada ultrapassar o limiar mínimo de relevância: apenas TINO. Sem badge, sem contador, sem `···`, sem inventar conteúdo. O silêncio também é um comportamento inteligente.

## 12. Inteligência Explicativa

Permite explicar a origem de uma conclusão.

Insight: "Café Maratá pode acabar amanhã."

Explicação: "Porque restam 2 unidades e saíram 5 desde ontem."

Insights importantes devem possuir evidências rastreáveis. Não precisamos expor cálculos técnicos ao comerciante, mas precisamos conseguir explicar a conclusão em linguagem simples.

## 13. Inteligência de Incerteza

Permite que o sistema saiba diferenciar: SEI, SUSPEITO, NÃO SEI.

Essa inteligência deve ser transversal a todo o TINO.

Exemplo ambíguo: Pix R$ 180, João deve R$ 180, Carlos deve R$ 180 → AMBIGUOUS → "Recebi um Pix de R$ 180. Ele pode estar relacionado a João ou Carlos. Quer identificar?" Nunca: "João pagou."

Onde aplicar: transcrição, entity resolution, aliases aprendidos, conciliação Pix, previsões, anomalias, recomendações, relacionamento entre eventos.

## 14. Modelo de Evidence

```kotlin
data class BusinessEvidence(
    val id: EvidenceId,
    val type: EvidenceType,
    val subject: BusinessEntityRef?,
    val facts: List<Fact>,
    val source: EvidenceSource,
    val confidence: Confidence,
    val occurredAt: Instant?,
    val detectedAt: Instant
)
```

Uma evidência não é necessariamente um pensamento visível. Ela primeiro passa pelo ranking contextual.

## 15. Modelo de Insight

```kotlin
data class TinoInsight(
    val id: InsightId,
    val type: InsightType,
    val title: String,
    val explanation: String,
    val evidenceIds: List<EvidenceId>,
    val confidence: Confidence,
    val relevance: Double,
    val urgency: Double,
    val novelty: Double,
    val actions: List<CapabilityRef>
)
```

Tipos: OBSERVATION, ATTENTION, OPPORTUNITY, ANOMALY, REMINDER, PATTERN, PREDICTION, SUGGESTION, QUESTION, POSITIVE_SIGNAL.

## 16. Ranking Contextual

O TINO pode detectar dezenas de evidências. O usuário não deve receber dezenas de pensamentos.

```
27 evidências
     ↓
Contextual Ranking
     ↓
relevance / urgency / novelty / confidence / screen context / business impact
     ↓
TOP N
     ↓
···
```

A experiência deve privilegiar poucos insights de alta qualidade.

## 17. Relação com o mascote

```
                         TINO
                          │
            ┌─────────────┼─────────────┐
            ▼             ▼             ▼
          TOQUE          ···           VOZ
            │             │             │
            ▼             ▼             ▼
      O que posso     O que o TINO   Faça/pergunte
      fazer aqui?       percebeu       diretamente
            │             │             │
            ▼             ▼             ▼
      Bottom Rise      Thoughts      Agent Runtime
          A2UI          Surface
```

- Toque no mascote: "O que posso fazer aqui?" → catálogo A2UI Bottom Rise
- `···`: "O TINO percebeu algo." → evidências ranqueadas
- Voz: "Quero pedir ou perguntar algo diretamente." Transcrição em segundo plano

## 18. Pensamento não é capability

Essa separação é obrigatória.

Capability = o que o TINO consegue fazer ("Estoque baixo").

Thought / Insight = o que o TINO percebeu ("Café Maratá está com 2 unidades e, pelo ritmo de saída, pode acabar amanhã.").

## 19. Pensamento pode gerar ação

```
DADO → EVIDENCE → INSIGHT → ··· → usuário abre → evidência explicada → CAPABILITY → A2UI
```

A mutação continua sujeita a preview, confirmação e demais gates.

## 20. Inteligência por domínio

Estoque: ruptura provável, zerado, baixa com alta velocidade, produto parado, saída acelerada, divergência, reposição, sazonalidade.

Caderneta: vencendo hoje, atrasos, promessa, recorrência, concentração de dívida, recebimentos esperados, mudança de padrão.

Clientes: sem movimentação, recorrente, mudança de comportamento, saldo crescente, quitação, padrão de compra.

Financeiro: variação de recebimentos, comparação de períodos, concentração por meio, recebimento incomum, previsão de entrada.

Fornecedores: vencimento, variação de preço, associação a produto em risco, recorrência de compra.

## 21. Inteligência de Objetivos — evolução posterior

Não implementar agora. Pergunta futura: "Qual resultado estamos tentando alcançar?"

Não implementar autonomia irrestrita. Objetivos devem operar sobre capabilities explícitas, políticas de segurança e confirmação quando necessário.

## 22. Ordem recomendada de evolução

1. PERCEPÇÃO
2. RELAÇÕES
3. TEMPO
4. INCERTEZA
5. APRENDIZADO
6. ANOMALIAS
7. PREDIÇÃO
8. RECOMENDAÇÃO
9. PROATIVIDADE
10. OBJETIVOS

A intenção é impedir que recomendações e automações sejam construídas antes de termos boa evidência, confiança e explicabilidade.

## 23. Regras de segurança

1. Evidência antes de conclusão.
2. Confiança explícita no runtime, mesmo quando não exibida numericamente.
3. Hipótese não vira fato. MAYBE não vira CONFIRMED em silêncio.
4. HITL para ambiguidade em ação sensível.
5. Nenhuma mutação por insight. Detectar "João provavelmente pagou" não executa `settleDebt(Joao)`.
6. Proveniência: de onde veio, quando, qual dado, observado ou inferido, qual confiança.
7. Silêncio é válido. Não criar pensamento artificial para manter o mascote "ativo".

## 24. Princípio de UX

O comerciante não deve precisar entender machine learning, confidence score, grafo, anomaly detection, embeddings, forecasting, reasoning ou agentes.

Ele deve receber: "Café pode acabar amanhã."

Se quiser entender: "Restam 2 unidades e saíram 5 desde ontem."

Se quiser agir: "Registrar entrada."

```
PERCEBER → ENTENDER → AGIR
```

## 25. Critérios para um pensamento aparecer

Deve acrescentar mudança, risco, anomalia, oportunidade, padrão, previsão, lembrete contextual, relação não óbvia ou sinal positivo relevante — e superar confiança, relevância, novidade e contexto.

## 26. Anti-padrões

- Repetir a UI
- Criar ansiedade / alerta vermelho permanente
- Inventar causalidade
- Previsão sem confiança
- Automatizar hipótese
- Despejar dezenas de insights
- Fazer o mascote falar o tempo todo

## 27. Exemplo completo — Estoque

Estado: 7 produtos, 1 estoque baixo. Café Maratá com 2 unidades e 5 saídas desde ontem.

Insight: "Café Maratá pode acabar amanhã."

Explicação: "Restam 2 unidades e saíram 5 desde ontem."

Ações: Ver movimentações, Registrar entrada, Ver fornecedor.

Interface: TINO `···`. O usuário escolhe se deseja abrir.

## 28. Exemplo completo — Caderneta + Pix

João saldo R$ 180, Pix recebido R$ 180. Relação João ↔ Pix com confiança média.

Insight: "Entrou um Pix de R$ 180 que pode estar relacionado à conta do João."

Ação: Identificar pagamento.

Se João e Carlos tiverem o mesmo valor: ambiguidade. O TINO não dá baixa silenciosamente.

## 29. Definição final

```
PERCEBER
   ↓
RELACIONAR
   ↓
ENTENDER O TEMPO
   ↓
MEDIR INCERTEZA
   ↓
APRENDER
   ↓
DETECTAR PADRÕES / ANOMALIAS
   ↓
PREVER
   ↓
RECOMENDAR
   ↓
EXPLICAR
   ↓
AGIR SOMENTE ATRAVÉS DE CAPABILITIES SEGURAS
```

Interface:

- TINO → tudo normal
- TINO `···` → "percebi algo que talvez seja útil"
- TOQUE NO TINO → "o que posso fazer aqui?"
- VOZ → "faça ou consulte isso para mim"

## 30. Regra de produto

O TINO não precisa parecer inteligente o tempo inteiro. Ele precisa perceber o que importa, saber o quanto pode confiar na própria conclusão, explicar por que chegou nela e transformar entendimento em uma ação simples quando o usuário quiser.
