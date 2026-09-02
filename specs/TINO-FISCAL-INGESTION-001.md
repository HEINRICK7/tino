# TINO — FISCAL INGESTION 001

**Status:** SLICE 005 COMPLETE / EXTERNAL FISCAL SERVICE PENDING  
**Data:** 2026-08-18

## Decisão de escopo

O TINO não integra NFeWizard, SEFAZ, certificado A1, Room, estoque, Gemma ou
A2UI neste slice.

O primeiro passo é somente:

```text
XML de NF-e sanitizado
        ↓
FiscalXmlParser
        ↓
CanonicalFiscalDocument
```

O módulo é JVM puro e não depende do Android. Isso mantém o parser substituível
e impede que uma biblioteca fiscal externa dite o domínio operacional.

## Fonte e evidência

O parser aceita uma `FiscalSource` explícita. Neste slice o fixture usa
`PROVIDED_XML`.

Cada documento e item preserva:

- origem;
- versão do parser;
- SHA-256 dos bytes originais;
- XML original sem normalização destrutiva.

Nenhum campo ausente é preenchido por inferência. `SEM GTIN` permanece sem GTIN
canônico.

## Modelo canônico coberto

`CanonicalFiscalDocument` preserva:

- id determinístico por chave/hash;
- chave de acesso;
- modelo, número e série;
- data de emissão;
- operação de entrada/saída;
- emitente e destinatário;
- itens com código do fornecedor, descrição, GTIN, NCM, CFOP, unidade,
  quantidade e valores decimais exatos;
- impostos disponíveis;
- totais;
- duplicatas;
- evidência original/proveniência.

Dinheiro e quantidades usam `BigDecimal` no parser fiscal. A conversão para
centavos do domínio operacional será um passo posterior, explícito e testado.

## Segurança do parser

O XML é lido com processamento seguro, sem DTD, entidades externas ou inclusão
de recursos externos. Falhas retornam `FiscalParseResult.Failure` tipado; o
parser não transforma XML inválido em documento parcial.

## Gates do Slice 001

- `FISCAL_CANONICAL_MODEL` — PASS;
- `XML_FIXTURE_SANITIZED` — PASS;
- `XML_PARSER` — PASS;
- `MONEY_EXACTNESS` — PASS;
- `ITEM_PRESERVATION` — PASS;
- `SUPPLIER_PRESERVATION` — PASS;
- `TOTAL_PRESERVATION` — PASS;
- `INSTALLMENT_PRESERVATION` — PASS;
- `PROVENANCE` — PASS;
- `ORIGINAL_EVIDENCE_PRESERVED` — PASS;
- `NO_INVENTED_FIELDS` — PASS;
- `NO_STOCK_MUTATION` — PASS por ausência de adapter de persistência;
- `NO_PRODUCT_MUTATION` — PASS por ausência de adapter de persistência;
- `NO_AGENT_DEPENDENCY` — PASS;
- `XML_EXTERNAL_ENTITY_BLOCKED` — PASS;
- `TESTS` — PASS, 4 testes do parser;
- `tino-fiscal-core:build` — PASS;
- `ASSEMBLE` — PASS (`:app:assembleDebug`);
- `LINT` — PASS (`:app:lintDebug`, sem erros/fatais).

## Validação executada

Comandos executados no workspace:

```text
gradle --no-daemon :tino-fiscal-core:test --console=plain
gradle --no-daemon :tino-fiscal-core:build :app:testDebugUnitTest :app:assembleDebug --console=plain
gradle --no-daemon :app:lintDebug --console=plain
```

Resultado observado:

- 4 testes do parser fiscal, sem falhas;
- build do módulo fiscal concluído;
- testes unitários do app concluídos;
- APK debug montado;
- lint debug concluído sem `Error` ou `Fatal`.

Esta validação é estrutural e local. Não comprova homologação SEFAZ, leitura de
XML emitido por terceiros além do contrato coberto pelo parser, nem integração
com NFeWizard, Room, estoque, Gemma ou A2UI.

## Slice 002 — matching e preview de importação

O segundo slice foi implementado no mesmo módulo JVM puro. Ele recebe somente
o documento canônico e snapshots de candidatos locais; não conhece Room nem
escreve no catálogo operacional.

### SupplierResolver

- CNPJ/CPF normalizado e exato é a primeira fonte de resolução;
- CNPJ divergente não é convertido silenciosamente em vínculo por nome;
- nome normalizado sem identidade fiscal é resolvido apenas quando único;
- duplicidade ou sugestão por nome retorna `Ambiguous` para revisão;
- ausência retorna `NotFound`, permitindo preview de novo fornecedor.

### ProductMatcher

A ordem determinística é:

```text
GTIN exato
    ↓
supplierId + supplierProductCode via SupplierProductMapping
    ↓
alias confirmado
    ↓
descrição normalizada
    ↓
ranking fuzzy
```

GTIN duplicado, descrição duplicada ou fuzzy sem confiança/separação suficiente
retornam candidatos ambíguos ou `NotFound`. Nenhum preço, estoque, GTIN ou id é
inventado pelo matching.

### FiscalImportPreview

O builder produz preview tipado para:

- fornecedor existente, novo ou ambíguo;
- produto existente, novo, ambíguo ou com embalagem a confirmar;
- quantidade, unidade, estoque atual e custo unitário fiscal preservados como
  `BigDecimal`;
- avisos de confirmação e de baixa confiança;
- `canCommit = false` quando houver ambiguidade ou embalagem pendente;
- `canCommit` não substitui a confirmação humana.

O preview não cria produto, fornecedor, estoque, compra, conta a pagar ou
qualquer evento. O commit determinístico permanece um slice posterior.

### Gates do Slice 002

- `SUPPLIER_EXACT_TAX_ID` — PASS;
- `PRODUCT_GTIN_EXACT` — PASS;
- `SUPPLIER_PRODUCT_MAPPING` — PASS;
- `NORMALIZED_DESCRIPTION` — PASS;
- `FUZZY_CANDIDATES` — PASS;
- `AMBIGUITY` — PASS;
- `NOT_FOUND` — PASS;
- `NO_AUTO_LOW_CONFIDENCE_MATCH` — PASS;
- `NO_MUTATION` — PASS;
- `TESTS` — PASS, 8 testes de matching;
- `ASSEMBLE` — PASS;
- `LINT` — PASS.

O total validado do módulo fiscal é de 12 testes, sem falhas.

## Slice 003 — embalagem e conversão de unidade

Foi adicionada uma fronteira pura para `ProductPackaging`. A conversão só
acontece quando existe um mapeamento confirmado para o produto, fornecedor e
unidade fiscal.

```text
2 CX
  ↓
mapping confirmado: 12 unidades por caixa
  ↓
24 unidades de estoque projetadas
```

Sem mapeamento, com mapeamento não confirmado ou com opções conflitantes, o
resultado é `RequiresConfirmation`. O sistema não presume que `1 CX = 1 UN` e
não materializa quantidade de estoque nessa situação.

### Gates do Slice 003

- `FISCAL_UNIT_PRESERVED` — PASS;
- `PACKAGE_MAPPING` — PASS;
- `UNKNOWN_PACKAGE_REQUIRES_HUMAN` — PASS;
- `NO_FAKE_CONVERSION` — PASS;
- `NO_MUTATION` — PASS;
- `TESTS` — PASS, 4 testes de embalagem;
- `ASSEMBLE` — PASS;
- `LINT` — PASS.

O total validado do módulo fiscal é de 16 testes, sem falhas.

## Slice 004 — contrato declarativo de preview fiscal

O preview agora pode ser convertido para uma mensagem declarativa tipada do
TINO, com schema explícito `tino.fiscal.a2ui.v1` e componente
`fiscal_import_summary`.

A mensagem preserva:

- fornecedor existente, novo ou ambíguo;
- contagem de itens existentes, novos, ambíguos e pendentes de embalagem;
- valor total da NF-e vindo do documento canônico;
- itens com o estado correspondente ao matching;
- warnings de revisão;
- ações limitadas a `REVIEW` e `CANCEL`.

Não existe ação `COMMIT` no contrato deste slice. A superfície é um contrato
de revisão e não um executor. Este protocolo é próprio do TINO e não deve ser
confundido com a especificação oficial do Google A2UI.

### Gates do Slice 004

- `FISCAL_IMPORT_SUMMARY_SURFACE` — PASS;
- `TYPED_ITEM_STATES` — PASS;
- `SOURCE_VALUES_PRESERVED` — PASS;
- `AMBIGUITY_VISIBLE` — PASS;
- `NO_COMMIT_ACTION` — PASS;
- `NO_MUTATION` — PASS;
- `TESTS` — PASS, 2 testes de superfície;
- `ASSEMBLE` — PASS;
- `LINT` — PASS.

O total validado do módulo fiscal é de 18 testes, sem falhas.

## Slice 005 — commit fiscal determinístico

O commit foi separado em duas camadas:

```text
FiscalImportCommitValidator       tino-fiscal-core
        ↓ plano validado
FiscalImportCommitService         Android/Room adapter
        ↓ uma transação
supplier + product + mapping + purchase + stock + history + outbox
```

Regras aplicadas:

- `humanConfirmed` e `operationId` são obrigatórios;
- `AmbiguousProduct`, `PackagingRequired` e fornecedor ambíguo bloqueiam o
  commit;
- produto novo exige nome, unidade e preço de venda confirmado — o custo
  fiscal nunca vira preço de venda automaticamente;
- valores fiscais são convertidos para centavos somente quando a conversão é
  exata, sem arredondamento;
- duplicata/conta a pagar não é criada neste slice;
- o documento e todos os efeitos operacionais entram na mesma transação Room;
- a chave de documento/hash e `operationId` impedem reimportação e replay local
  duplicados;
- XML bruto e hash ficam preservados em `fiscal_imports`;
- eventos de outbox são determinísticos e não carregam o XML fiscal completo.

### Persistência e migration

Foi criada a migration formal `6 → 7`, com:

- `fiscal_imports`;
- `supplier_product_mappings`;
- `product_purchase_history`;
- `SupplierEntity.taxId` opcional.

O snapshot inclui as três novas estruturas. O replay remoto aplica produto,
fornecedor, mapping, compra, estoque, histórico e marcador fiscal de forma
idempotente. O XML bruto não é enviado no evento remoto; para preservar a
evidência completa entre dispositivos, o caminho de snapshot é necessário.

### Gates do Slice 005

- `FISCAL_COMMIT_SERVICE` — PASS;
- `HUMAN_CONFIRMATION` — PASS;
- `ATOMIC_TRANSACTION` — PASS;
- `IDEMPOTENCY` — PASS;
- `NO_DOUBLE_STOCK` — PASS;
- `STOCK_MOVEMENT` — PASS;
- `PURCHASE_HISTORY` — PASS;
- `SUPPLIER_MAPPING` — PASS;
- `NEW_PRODUCT_CONFIRMATION` — PASS;
- `PACKAGING_GATE` — PASS;
- `AMBIGUITY_GATE` — PASS;
- `SALE_PRICE_UNCHANGED` — PASS;
- `EVIDENCE_PRESERVED` — PASS;
- `PAYABLE_ISOLATION` — PASS;
- `SYNC_SAFE` — PASS;
- `ROLLBACK` — PASS;
- `MIGRATION_6_7` — IMPLEMENTED, coberta pelo schema Room 7;
- `TESTS` — PASS, 5 testes do adapter Room e 5 testes do validador puro;
- `ASSEMBLE` — PASS;
- `LINT` — PASS.

O módulo fiscal passa a ter 23 testes, e o app executa os testes unitários
incluindo 5 cenários do commit fiscal.

## Slice 006

O serviço fiscal externo isolado foi criado em `tino-fiscal-service/`, ainda sem
tocar o domínio Android. O contrato e o harness de homologação estão
documentados em [`TINO-FISCAL-SERVICE-006.md`](TINO-FISCAL-SERVICE-006.md).
Conexão SEFAZ real continua pendente de ambiente autorizado:

```text
FiscalPort
    ↓
TINO Fiscal Service
    ↓
NFeWizard / adaptadores SEFAZ
    ↓
CanonicalFiscalHandoff
```

NFeWizard, certificados, SOAP, URLs de UF e detalhes da SEFAZ continuam fora
do app. O serviço externo prova localmente `discoverIncomingDocuments` e
`fetchFiscalDocument`; a próxima validação é homologação sem alterar o
contrato canônico nem o commit local já validado.
