# TINO Spatial Guide

## Proposta de evolução do mascote e da orientação contextual

## Status

Proposta arquitetural para evolução do M7 — Tino Presence.

Este documento descreve uma direção futura. Ele não autoriza, por si só, a implementação imediata nem altera os gates atuais do projeto.

## Visão

O mascote deixa de ser apenas um elemento visual que procura um espaço livre na tela e passa a representar fisicamente o agente TINO dentro da interface.

O TINO deve conseguir:

- mover-se entre regiões relevantes da interface;
- apontar ou chamar atenção para uma informação importante;
- observar o contexto da tela;
- orientar o usuário com mensagens curtas;
- reagir às ações do usuário;
- permanecer discreto quando não houver motivo para interferir.

A proposta não consiste em renderizar uma cópia do mascote dentro de cada componente A2UI. Deve existir um único TINO vivo sobre a interface, enquanto os componentes tornam-se conscientes do contexto do TINO.

## Princípio central

Todos os componentes podem conhecer o TINO, mas nem todos devem exibi-lo.

O mascote deve funcionar como uma entidade única que se desloca até o elemento relevante. Isso preserva o minimalismo visual e evita poluir a tela com vários mascotes decorativos.

## Modelo de funcionamento

O TINO recebe informações de três fontes:

- estado atual da tela;
- árvore A2UI renderizada;
- ação ou contexto do usuário.

Com essas informações, um mecanismo de orientação produz um plano contendo:

- objetivo da orientação;
- mensagem opcional;
- elemento-alvo;
- ação do mascote;
- movimento esperado;
- prioridade;
- ação de interface sugerida, quando aplicável.

O plano é executado por um MascotHost, que coordena o posicionamento e as animações do mascote.

## Consciência semântica da tela

O posicionamento atual baseado apenas em regiões livres e áreas não clicáveis deve evoluir para um posicionamento orientado por intenção.

Em vez de saber somente onde não pode ficar, o TINO deve compreender:

- em qual tela está;
- quais elementos existem;
- quais elementos são clicáveis;
- qual é a finalidade de cada elemento;
- qual ação está disponível;
- quais dados exigem atenção;
- qual elemento é mais relevante para o próximo passo do usuário.

Por exemplo, na tela de Estoque, o TINO pode identificar:

- busca de produto;
- filtro Todos;
- filtro Estoque baixo;
- filtro Sem estoque;
- lista de produtos;
- quantidade de produtos com estoque baixo;
- produto com estoque zerado.

Com isso, ele pode orientar o usuário para o filtro ou produto relevante, em vez de simplesmente ocupar uma área vazia.

## Exemplo: tela de Estoque

Ao entrar na tela de Estoque, o TINO deve analisar o estado atual.

Se houver um produto com estoque baixo, ele pode:

- olhar para o indicador de estoque baixo;
- aproximar-se de uma área segura próxima ao filtro correspondente;
- exibir uma mensagem curta, como “1 produto está acabando. Quer ver qual?”;
- acompanhar o usuário quando o filtro for ativado;
- deslocar-se para o produto relevante;
- indicar o produto com uma orientação breve, como “É este aqui.”.

O TINO não deve transformar a interface em um tutorial permanente. Ele deve conduzir o olhar do usuário somente quando isso for útil.

## Exemplo: tela Mais

A tela Mais pode conter várias opções, como:

- Fornecedores;
- Comprar;
- Resumo;
- Notas;
- Clientes;
- Configurações;
- modo offline;
- ações A2UI;
- outras funcionalidades disponíveis.

O TINO pode analisar o estado do negócio para escolher uma orientação relevante.

Se não houver fornecedores cadastrados e existir estoque baixo, pode orientar:

> Cadastre de quem você compra. Depois eu consigo ajudar com reposição.

Se já houver fornecedores e existir estoque crítico, pode sugerir:

> Tem produto acabando. Podemos preparar uma compra.

Se não houver nenhuma situação relevante, o mascote deve permanecer em estado ocioso, em uma área segura e discreta.

## Metadados de orientação nos componentes A2UI

Os componentes A2UI devem poder carregar metadados semânticos destinados ao agente. Esses metadados não substituem a aparência nem a responsabilidade do renderer Compose. Eles informam o significado do componente e as ações disponíveis.

Um contrato conceitual pode conter:

~~~kotlin
data class TinoGuidance(
    val id: String,
    val purpose: String,
    val priority: Int = 0,
    val message: String? = null,
    val nextAction: String? = null,
    val mascotAction: MascotAction = MascotAction.OBSERVE
)
~~~

Exemplo conceitual para um alerta de estoque baixo:

~~~kotlin
TinoGuidance(
    id = "inventory.low_stock",
    purpose = "Mostrar produtos que precisam de reposição",
    priority = 90,
    message = "Tem um produto acabando.",
    nextAction = "OPEN_LOW_STOCK",
    mascotAction = MascotAction.POINT
)
~~~

Exemplo conceitual para busca de produto:

~~~kotlin
TinoGuidance(
    id = "inventory.search",
    purpose = "Encontrar um produto",
    priority = 50,
    mascotAction = MascotAction.LOOK
)
~~~

## Modifier semântico para Compose

Para integrar a semântica ao Compose de forma consistente, pode ser criado um modifier equivalente a tinoAware().

Exemplo conceitual:

~~~kotlin
Modifier.tinoAware(
    id = "inventory.low_stock",
    role = TinoRole.ACTION,
    description = "Produtos com estoque baixo",
    priority = 90
)
~~~

Esse registro deve informar, no mínimo:

- identificador estável;
- posição e tamanho na tela;
- visibilidade;
- se o elemento é clicável;
- estado atual;
- importância relativa;
- descrição semântica;
- ação disponível;
- relação com a tela ou capability atual.

O objetivo é fornecer ao TINO uma visão estrutural da interface, sem depender de computer vision para interpretar a própria tela.

## Host único do mascote

O aplicativo deve possuir um único TinoMascotHost na raiz visual da experiência relevante.

Esse host deve ser independente das telas específicas e funcionar em:

- Home;
- Estoque;
- Caderneta;
- Clientes;
- Fornecedores;
- Compras;
- Notas;
- Resumo;
- superfícies A2UI;
- outras telas que ofereçam elementos conscientes do TINO.

O renderer da interface continua responsável pelos componentes. O TinoMascotHost funciona como uma camada sobre a interface, recebendo o plano de orientação e executando a presença do mascote.

## Modos de posicionamento

Devem existir pelo menos dois modos de posicionamento.

### IDLE

No estado ocioso, o TINO procura a melhor região vazia e segura da tela.

Ele deve evitar:

- componentes clicáveis;
- textos importantes;
- navegação;
- barras do sistema;
- teclado;
- áreas visualmente obstruídas;
- regiões que prejudiquem a leitura.

### GUIDING

Quando houver uma orientação ativa, o TINO procura uma região segura próxima ao elemento-alvo.

O objetivo não é sobrepor o componente, mas criar proximidade suficiente para indicar a relação entre o mascote e o elemento relevante.

## Cálculo de posição

O mecanismo de posição pode considerar:

- espaço livre;
- distância de elementos clicáveis;
- distância de textos importantes;
- relevância semântica;
- proximidade do alvo;
- sobreposição com navegação;
- proximidade dos limites do sistema;
- sobreposição com o teclado;
- obstrução visual.

Uma formulação conceitual seria:

~~~text
CandidateScore =
    freeSpace
  + distanceFromClickable
  + distanceFromText
  + semanticRelevance
  + proximityToTarget
  - navigationOverlap
  - systemInsets
  - keyboardOverlap
  - visualObstruction
~~~

Os valores e pesos devem ser calibrados com evidência visual e testes em dispositivos reais.

## Ações visuais do mascote

O mascote pode usar um conjunto pequeno de ações sem exigir expressões complexas:

~~~kotlin
enum class MascotAction {
    IDLE,
    LOOK,
    FOLLOW,
    POINT,
    WAIT,
    LISTEN,
    THINK,
    CONFIRM,
    ALERT
}
~~~

Comportamentos esperados:

- IDLE: permanece em uma região segura;
- LOOK: direciona os olhos para o alvo;
- FOLLOW: acompanha o deslocamento ou ação do usuário;
- POINT: chama atenção para o elemento relevante;
- WAIT: aguarda uma ação do usuário;
- LISTEN: indica que está recebendo uma entrada;
- THINK: executa uma pequena animação de processamento;
- CONFIRM: sinaliza que uma ação foi concluída;
- ALERT: chama atenção para uma situação que exige cuidado.

Os olhos podem ser o principal mecanismo de comunicação, mantendo a identidade minimalista do TINO.

## Orientação ao longo do Agent Loop

As etapas do Agent Runtime podem produzir sinais para o mascote.

Exemplo de uma operação falada:

> TINO, coloca dois cafés Maratá na conta da Maria.

O runtime pode passar por etapas como:

1. receber a fala;
2. localizar Maria;
3. localizar Café Maratá;
4. consultar preço;
5. consultar estoque;
6. montar a operação;
7. mostrar uma prévia A2UI;
8. solicitar confirmação;
9. executar a operação;
10. mostrar o resultado.

Cada etapa pode controlar a presença do mascote:

- durante a fala, usar LISTEN;
- durante uma busca, usar LOOK ou THINK;
- quando a prévia estiver pronta, mover-se para perto dela;
- durante a confirmação, olhar para a ação de confirmar;
- após a execução, usar CONFIRM.

O mascote deve representar o estado real do runtime. Não devem ser usados timers artificiais que indiquem progresso inexistente.

## A2UI e orientação do agente

O contrato A2UI pode conter um bloco específico de orientação do assistente.

Exemplo conceitual:

~~~json
{
  "type": "confirmation",
  "id": "credit.confirmation",
  "assistant": {
    "target": "confirm_button",
    "behavior": "guide",
    "message": "Confira antes de lançar.",
    "action": "look_at"
  }
}
~~~

Outros comportamentos possíveis:

~~~json
{
  "assistant": {
    "behavior": "observe"
  }
}
~~~

~~~json
{
  "assistant": {
    "behavior": "warn",
    "target": "stock_warning"
  }
}
~~~

~~~json
{
  "assistant": {
    "behavior": "wait",
    "target": "voice_input"
  }
}
~~~

O A2UI passa a descrever não apenas componentes, mas também atenção, orientação e movimento do TINO. O renderer continua limitado ao catálogo e às regras visuais controladas pelo aplicativo.

## Memória de orientação

O TINO deve aprender o nível de ajuda adequado para cada usuário, sem transformar essa memória em uma fonte de verdade operacional.

Na primeira utilização de uma funcionalidade, pode apresentar uma orientação simples:

> Produtos que estão acabando aparecem aqui.

Depois de vários usos, pode reduzir a intervenção e apenas apontar para o filtro relevante. Após uso frequente, pode permanecer silencioso, salvo quando surgir uma situação anormal.

Situações como estoque zerado, falha recuperável ou confirmação de uma ação importante podem elevar novamente a prioridade da orientação.

Uma memória conceitual pode conter:

~~~kotlin
data class TinoGuideMemory(
    val seenFeatures: Set<String>,
    val completedFeatures: Set<String>,
    val dismissedTips: Set<String>,
    val actionFrequency: Map<String, Int>,
    val lastGuidance: Map<String, Long>,
    val guidanceLevel: Int
)
~~~

Essa memória deve controlar somente a frequência e o nível de orientação. Ela não deve alterar estoque, clientes, vendas, recebíveis ou qualquer outro dado operacional do Room.

## Regras de experiência

O TINO Spatial Guide deve obedecer às seguintes regras:

1. O mascote não deve chamar atenção sem motivo contextual.
2. O mascote não deve cobrir botões, textos ou dados importantes.
3. A orientação deve ser curta e compreensível em poucos segundos.
4. O movimento deve reforçar a ação, não substituir a informação textual.
5. Ações críticas devem continuar visíveis e acessíveis sem depender do mascote.
6. O usuário deve poder ignorar ou dispensar uma orientação.
7. A orientação deve representar o estado real do Agent Runtime.
8. O mascote nunca deve indicar que uma operação foi concluída antes da confirmação real.
9. A memória de orientação deve respeitar o comportamento do usuário.
10. Deve existir apenas um mascote ativo por superfície de experiência.

## Integração arquitetural futura

O desenho futuro deve manter as responsabilidades separadas:

- o Agent Runtime entende intenção e contexto;
- as capabilities consultam e alteram o domínio autorizado;
- o resultado estruturado informa o que aconteceu;
- o A2UI descreve a interface permitida;
- o TinoGuideEngine decide se há uma orientação relevante;
- o TinoMascotHost executa a presença visual;
- o Position Engine calcula uma região segura;
- o Animation Engine executa o movimento;
- o Room continua sendo a verdade operacional do negócio.

O mascote não deve consultar diretamente o banco para inventar estados nem executar operações de negócio. Ele recebe sinais estruturados do runtime e reage a eles.

## Evolução do M7 — Tino Presence

Esta proposta deve ser tratada como uma evolução do M7, depois que os módulos anteriores produzirem sinais reais:

- M1 — Shared Agent State;
- M2 — Agent Progress Runtime;
- M3 — Agentic Streaming;
- M4 — HITL Runtime;
- M5 — Interrupt & Correction;
- M6 — Incremental A2UI;
- M7 — Tino Presence;
- M8 — Full Runtime Integration.

Não é recomendado criar orientação baseada em timers artificiais antes de o runtime possuir eventos confiáveis de estado, progresso, confirmação, interrupção e resultado.

## Critérios de aceitação futuros

Uma implementação desta proposta somente deve ser considerada completa quando:

- existir um único TinoMascotHost funcional nas superfícies previstas;
- componentes relevantes registrarem metadados semânticos;
- o mascote evitar regiões clicáveis e obstruções;
- o mascote puder se aproximar de um alvo sem cobri-lo;
- a ação visual refletir eventos reais do runtime;
- o usuário puder dispensar orientações;
- a memória reduzir dicas repetitivas;
- situações anormais puderem reativar a orientação;
- a interface continuar plenamente utilizável com o mascote ocioso ou oculto;
- não houver mutação operacional causada pelo sistema de orientação;
- os testes cobrirem telas, tamanhos e dispositivos relevantes.

## Decisões que permanecem fora do escopo

Esta proposta não autoriza:

- criar um mascote separado para cada card;
- permitir que a LLM desenhe componentes arbitrários;
- permitir que o mascote execute operações de negócio diretamente;
- usar computer vision como requisito para interpretar a própria UI;
- adicionar AG-UI, CopilotKit ou outra arquitetura externa;
- implementar novos verticais ou novos domínios;
- substituir o A2UI por telas específicas para cada pergunta;
- usar animações para mascarar loading ou falhas do runtime.

## Conclusão

O sistema atual de busca por áreas não clicáveis é a base do futuro TINO Spatial Guide. A evolução proposta adiciona semântica: o mascote deixa de procurar apenas um espaço vazio e passa a entender por que deve estar próximo de determinado elemento.

O resultado esperado é um TINO que conduza o olhar, ajude quando necessário, permaneça discreto quando não for necessário e represente visualmente o estado real do agente. A interface continua minimalista, mas passa a ter uma presença contextual e útil.

