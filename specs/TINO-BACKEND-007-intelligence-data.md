# TINO-BACKEND-007 — Intelligence Data

**Status:** Draft implementável
**Pré-requisitos:** B003 e B006
**Objetivo:** definir sinais, features, recomendações, decisões humanas e
limites de aprendizado do TINO.

## 1. Princípios

- inteligência sugere; o comerciante decide;
- recomendação não altera estoque, preço, compra ou fiado sozinha;
- cada recomendação explica o sinal que a originou;
- baseline heurístico local funciona sem cloud;
- dados pessoais e conversas não entram em dataset sem base e consentimento;
- nenhum modelo substitui validação do domínio.

## 2. Sinais operacionais

O baseline pode usar dados já necessários ao comércio:

```text
product_id
stock_quantity
units_sold_last_7_days
units_sold_last_30_days
units_received_last_30_days
days_since_last_sale
days_since_last_receipt
credit_open_balance
```

Cada sinal precisa de:

```text
source_event_position
calculated_at
window_start
window_end
quality
```

Não gerar uma recomendação quando o sinal estiver incompleto ou stale além da
janela definida.

## 3. Features e qualidade

Features são derivadas de eventos/projeções B003:

```text
daily_sales_rate = units_sold / days_with_data
days_of_cover = stock_quantity / daily_sales_rate
reorder_pressure = expected_demand - stock_quantity
```

Regras:

- evitar divisão por zero;
- informar quando o histórico é insuficiente;
- não comparar produtos sem normalizar unidade;
- não usar preço como proxy de demanda sem documentar a hipótese;
- versionar o cálculo da feature.

## 4. Recomendações v1

```text
STOCKOUT
REPLENISHMENT
SLOW_MOVING
RECURRENCE
```

Cada recomendação contém:

```text
recommendation_id
store_id
type
product_id ou customer_id
message
evidence
confidence
feature_version
created_at
decision
```

Exemplos aceitáveis:

```text
"Café está sem estoque; vendeu 12 unidades nos últimos 30 dias."
"Café pode acabar em menos de um mês com o ritmo atual."
"Biscoito está parado: 3 unidades vendidas em 30 dias e 18 em estoque."
```

Mensagens não podem afirmar certeza onde existe apenas estimativa.

## 5. Decisão humana

Estados:

```text
PENDING
ACCEPTED
REJECTED
EXPIRED
```

Aceitar uma recomendação registra uma decisão; não executa automaticamente uma
compra. Uma ação posterior, como preparar pedido, passa pelo B005/B006 e por
confirmação própria.

Rejeição pode receber motivo opcional para melhorar a utilidade futura, sem
punir o comerciante ou esconder recomendações.

## 6. Dataset e privacidade

O dataset analítico deve separar:

```text
raw operational events
derived aggregate features
recommendation outcomes
```

Remover ou tokenizar:

- nome e telefone do cliente quando não forem necessários;
- conteúdo de áudio e transcript;
- endereço de entrega;
- documentos fiscais pessoais.

Retenção, consentimento e exclusão devem ser definidos antes de treinamento
com dados de lojas reais.

## 7. Avaliação

Métricas mínimas:

```text
recommendation_shown
recommendation_accepted
recommendation_rejected
recommendation_expired
stockout_after_recommendation
false_positive_feedback
```

Avaliar por store e período, evitando que uma loja domine o resultado.
Comparar modelo com baseline heurístico e manter fallback local.

## 8. Limites de ML

- Gemma não escreve features nem projeções;
- ML não decide confirmação fiscal, pagamento ou venda;
- qualquer mudança de modelo exige `model_version`;
- recomendações devem ser reproduzíveis por versão;
- falha do modelo retorna ao baseline ou a estado sem recomendação;
- não enviar dados comerciais para serviço externo não aprovado.

## 9. Gate de aceite do B007

- baseline local funciona sem internet;
- features têm janela, versão e qualidade;
- recomendações exibem evidência e incerteza;
- decisão humana é persistida;
- recomendação não muta domínio automaticamente;
- dataset redige dados desnecessários;
- avaliação mede aceitação e falso positivo;
- modelo tem fallback e versionamento;
- testes cobrem estoque zero, histórico vazio, divisão por zero e rejeição.

