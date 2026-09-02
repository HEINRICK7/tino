# G4.3 — Intelligence Runtime no dispositivo

**Data:** 28/08/2026  
**Device:** Samsung SM-A042M / Android 14 (API 34)  
**Serial:** `R9XW2006AWX`

## Resultado

O APK debug atual foi instalado incrementalmente, sem apagar os dados locais.
O harness `tools/g4-3-intelligence-runtime-smoke.sh` abriu a
`IntelligenceRuntimePort` real e consultou a pergunta somente-leitura
`qual produto tem o menor estoque?`.

Resultado observado:

```text
G4.3 PASS: Intelligence Runtime consultou fatos locais e respondeu sem mutação.
status=ANSWERED
planner=deterministic
confidence=0.99
answer=O menor estoque é o de Agua sanitária Minuano: 24 unidade(s).
facts=products
analytics=lowest_stock
```

Evidência bruta: `/tmp/tino-g4-3-runtime-final-evidence`.

APK usado no smoke G4.3: 586.851.711 bytes, SHA-256
`57f7ac11a9fd1c6286170c61c2d9a689d0a4bfd421fc1c853ee766acaf84b441`.

## Correções verificadas

- O timeout global de 8 s foi incompatível com o cold start do modelo no
  device; o orçamento do runtime agora é finito e comporta a fronteira local
  de inferência.
- O prompt do planejador foi reduzido para um contrato de seleção de objetivo;
  a execução das ferramentas continua no executor local validado.
- Perguntas de negócio já conhecidas pelo planner determinístico não acionam o
  Gemma pesado. Isso evita uma espera de até 90 s na consulta normal e mantém o
  resultado auditável.
- O executor respondeu com dados do Room e analytics determinísticos; nenhuma
  capability de mutação foi chamada.

## Validação automatizada

- `gradle :app:testDebugUnitTest --no-daemon --console=plain`: **PASS** — 553
  testes do app, 0 falhas.
- `gradle :app:assembleDebug --no-daemon --console=plain`: **PASS**.
- `git diff --check`: **PASS**.

Esta evidência fecha a prova física do caminho de consulta do Intelligence
Runtime. O gate físico seguro da mutação também passou em banco Room isolado;
ele está registrado em [TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md](TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md).
Nenhuma mutação foi feita no banco piloto comercial. O fallback determinístico
continua não sendo um modelo de ML, e ainda permanecem avaliação do modelo em
dados reais, RAG externo produtivo e cobertura física universal.
