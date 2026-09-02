# TINO Document Intake 002 — DANFE Table Vision Spike

**Status:** SCANNER AUTO-CAPTURE + RECTIFIED PREVIEW PASS / OCR ROW EXTRACTION PENDING  
**Data:** 2026-08-18

CameraX foi fixado em `1.4.2` neste slice para permanecer compatível com
AGP `8.6.1` e `compileSdk 35`. A atualização para versões que exigem SDK/AGP
mais novos fica fora deste experimento.

## Scanner overlay implementado

`DocumentScannerOverlay.kt` adiciona:

- `CaptureUiState` derivado do `DocumentCaptureQualityGate`;
- `PreviewView` via `LifecycleCameraController`;
- `Canvas` sobre o preview;
- quatro cantos seguindo `detectedQuad` com interpolação animada;
- progresso honesto baseado nos frames estáveis;
- scan line somente no estado `READY`;
- haptic somente na transição `CAPTURING`;
- flash visual curto no estado `CAPTURED`;
- mensagens `Posicione`, `Enquadre`, `Melhore a iluminação`, `Mantenha firme`,
  `Pronto` e `Capturando`.

O overlay está conectado à rota de produção:

`Mais → Notas → Ler nota com a câmera → Capturar foto → Conferir produtos`.

A captura automática agora usa `ImageAnalysis` e o `DocumentCaptureQualityGate`.
O detector real de quatro cantos e a retificação permanecem no
`TINO-DOCUMENT-SCANNER-002`; a heurística atual não é considerada detecção
geométrica de documento.

Para DANFE comprida, a rota usa moldura em modo `PRODUCT_TABLE`: o comerciante
aproxima somente a tabela de produtos, evitando tentar ler a nota inteira em
uma única foto vertical.

## OCR local conectado

`MlKitDanfeVisionAdapter` recebe a foto capturada, executa reconhecimento de
texto no aparelho e envia as linhas para `DanfeProductMapper`. O resultado é
renderizado na tela de conferência como produtos encontrados, `NeedsReview` ou
erro de leitura. Nenhuma dessas etapas altera Room, estoque ou fornecedor.

## Objetivo

Recuperar somente as linhas da tabela `DADOS DO PRODUTO/SERVIÇOS` de uma DANFE
real. O spike não reconhece fornecedor, impostos fora da tabela, transportadora
ou vencimentos e não altera estoque.

```text
foto DANFE
  ↓
perspectiva / crop da tabela
  ↓
TableRecognitionAdapter
  ↓
DocumentVisionPort
  ↓
DanfeProductMapper
  ↓
ImportedProduct[] ou NeedsReview
```

## Porta criada

`DocumentVisionPort` recebe `DocumentImage` e retorna `ProductImportResult`.
Implementações disponíveis/previstas:

- `MlKitDanfeVisionAdapter` — runtime local conectado à câmera;
- `PaddleTableAdapter` — experimento futuro de estrutura de tabela;
- `ServerVisionAdapter` — fallback sem alterar o contrato;
- `FakeVisionAdapter` — testes e preview;
- `NfeXmlAdapter` — fonte A1/XML futura.

## Mapper restrito

`DanfeProductMapper` aceita apenas:

```text
COD PROD | DESCRIÇÃO | NCM | CST | CFOP | UN | QTD | V.UNIT | V.TOTAL
```

Ele não preenche `packageQuantity` por inferência de texto. A embalagem segue
para o `FiscalPackagingResolver` já existente. Se unidade, quantidade ou
confiança mínima não forem confiáveis, o resultado é `NeedsReview` preservando
as linhas extraídas para conferência.

## Fixtures

As duas DANFEs reais estão catalogadas em
`tools/document-intake-002-fixtures.json`:

- `danfe-cherta-001-full` — foto inteira;
- `danfe-cherta-001-close-table` — foto aproximada da tabela.

As linhas do manifesto são referência de avaliação humana, não saída de OCR.

## Comparação A/B

| Variante | Pipeline | Estado |
|---|---|---|
| A | `SLANeXt_wired + OCR mobile` | PENDENTE — modelo não instalado |
| B | `General Table Recognition V2` | PENDENTE — modelo não instalado |

Métricas obrigatórias:

- product row recall;
- description accuracy;
- quantity accuracy;
- unit accuracy;
- NCM accuracy;
- column attachment accuracy;
- latency;
- RAM;
- APK/model size.

Não registrar métrica antes de executar as duas variantes sobre as mesmas
fixtures e comparar com o manifesto.

## Gates

| Gate | Estado |
|---|---|
| `DOCUMENT_VISION_PORT` | PASS |
| `DANFE_PRODUCT_MAPPER` | PASS |
| `STRICT_COLUMN_SCOPE` | PASS |
| `LOW_CONFIDENCE_NEEDS_REVIEW` | PASS |
| `FIXTURES_REGISTERED` | PASS |
| `CAMERAX_SURFACE` | PASS — `PreviewView` + controller |
| `SCANNER_OVERLAY_STATES` | PASS |
| `QUAD_INTERPOLATION` | PASS — Canvas |
| `STABILITY_PROGRESS` | PASS — derivado do gate |
| `READY_SCAN_FEEDBACK` | PASS |
| `CAPTURE_HAPTIC_FEEDBACK` | PASS |
| `LOCAL_OCR_RUNTIME` | PASS — `MlKitDanfeVisionAdapter` |
| `PHOTO_TO_PRODUCT_RESULT` | PASS — fluxo validado no aparelho |
| `SLANEXT_WIRED_RUNTIME` | PENDING |
| `GENERAL_TABLE_V2_RUNTIME` | PENDING |
| `PRODUCT_ROW_RECALL` | PENDING |
| `QUANTITY_ACCURACY` | PENDING |
| `UNIT_ACCURACY` | PENDING |
| `NCM_ACCURACY` | PENDING |
| `COLUMN_ATTACHMENT` | PENDING |
| `LATENCY_RAM_MODEL_SIZE` | PENDING |
| `NO_MUTATION` | PASS |
| `PHYSICAL_CAMERA_SMOKE_TEST` | PASS — câmera, auto-capture e conferência retificada no aparelho |
| `REAL_DANFE_ROW_EXTRACTION` | PENDING — amostra física ainda não produziu linhas confiáveis |

Conclusão atual: o TINO já está preparado para comparar os modelos sem alterar
domínio. O próximo trabalho é instalar/validar os runtimes de visão em um
ambiente de experimento e produzir métricas reais; não escolher A ou B por
hipótese.
