# G4.5 — Readiness do dataset real para avaliação

**Data:** 28/08/2026  
**Device:** Samsung SM-A042M / Android 14 (API 34)  
**Serial:** `R9XW2006AWX`

## Resultado observado

O caminho de leitura do `TinoEvidenceSnapshotBuilder` executou sem mutação,
mas o banco atualmente instalado está vazio:

```text
read_status=PASS products=0 customers=0 products_with_sales=0
products_with_model_evaluation=0 products_with_passing_model=0
model_readiness=INSUFFICIENT_DATA_NO_PRODUCTS
```

Consulta direta somente-leitura ao `tino.db` confirmou:

```text
products=0
customers=0
stock_movements=0
```

Assim, não existe base atual para avaliar previsão, anomalia, sazonalidade ou
recomendação em dados reais. O comportamento correto neste estado é declarar
`INSUFFICIENT_DATA`, não criar vendas, estoque ou clientes artificiais.

## Incidente de ambiente

Antes desta execução, o smoke G4.3 havia lido quatro produtos do Samsung e
respondido com `Agua sanitária Minuano: 24 unidade(s)`. Depois da execução do
`connectedDebugAndroidTest` e da reinstalação do APK debug, o Android registrou
`firstInstallTime=2026-08-28 19:32:49` para `com.tino.app`; o banco foi recriado
vazio. Não houve chamada de capability de mutação nem fixture gravado no banco
instalado. O fixture do teste físico usa `Room.inMemoryDatabaseBuilder`.

Foi procurado backup/export no workspace, armazenamento compartilhado e
backup Android. O backup encontrado refere-se apenas a `com.tino.app.test`,
não ao pacote comercial `com.tino.app`; não há fonte local identificada para
restaurar os quatro produtos.

## Estado do gate

`PASS_READ_ONLY_BOUNDARY`: o snapshot pode ser lido com segurança.  
`BLOCKED_DATASET_RESTORE`: a avaliação em dados reais aguarda restauração ou
importação legítima do dataset; nenhum dado será recriado por hipótese.
