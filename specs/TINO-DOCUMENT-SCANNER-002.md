# TINO Document Scanner 002 — Geometria e retificação da DANFE

**Status:** IMPLEMENTADO / VALIDAÇÃO FÍSICA PENDENTE  
**Data:** 2026-08-18

## Objetivo

Levar a captura de uma foto bruta para uma imagem retificada antes da leitura
dos produtos. Este slice não confirma importação fiscal, não cria produto,
não altera estoque e não grava fornecedor.

## Fluxo

```text
captura CameraX em resolução alta
        ↓
BitmapDocumentQuadDetector
        ↓
quadrilátero normalizado + validação
        ↓
DocumentPerspectiveRectifier
        ↓
imagem retificada em cache
        ↓
preview na tela Conferir produtos
        ↓
adapter de visão existente
```

O processamento continua fora da main thread. A imagem original permanece a
fonte para a transformação final; a análise usa uma cópia limitada para
evitar custo e memória desnecessários.

## Contratos implementados

`NormalizedDocumentQuad` mantém os pontos na ordem:

```text
topLeft → topRight → bottomRight → bottomLeft
```

`DocumentQuadGeometry` rejeita candidatos com área pequena/grande demais,
centro fora da região útil, lados muito desproporcionais ou pontos fora da
imagem.

`DocumentPerspectiveRectifier`:

- calcula dimensões de saída preservando a orientação da folha;
- limita dimensões para evitar bitmaps abusivamente grandes;
- usa `Matrix.setPolyToPoly` no Android para aplicar a transformação;
- salva a imagem retificada como JPEG temporário em cache;
- não altera o estado fiscal.

## Captura e fallback

- auto-capture continua protegido pelo estado `READY` e cooldown existente;
- auto-capture exige também candidato geométrico detectado e estável por
  frames consecutivos;
- captura manual usa exatamente a mesma detecção/retificação;
- sem quadrilátero válido, a conferência informa que não encontrou as bordas
  e oferece nova tentativa;
- falha de geometria não vira sucesso parcial e não dispara mutação.

## Limite conhecido

Este slice não é ainda um detector de scanner completo. O detector atual
encontra uma região clara conectada e devolve um quadrilátero axis-aligned.
Isso permite validar a pipeline de retificação e preview, mas não garante
acompanhar uma DANFE inclinada pelos quatro cantos reais. Esse é o trabalho do
SCANNER-003.

Também não adiciona um novo OCR. O adapter de visão já existente recebe os
bytes da imagem retificada; a precisão de tabela continua sendo um gate
separado do experimento Document Intake 002.

## Gates

| Gate | Estado |
|---|---|
| `DOCUMENT_QUADRILATERAL` | PASS — contrato e validação |
| `CORNER_ORDERING` | PASS — testes |
| `PERSPECTIVE_TRANSFORM` | PASS — runtime Android conectado |
| `RECTIFIED_OUTPUT_SIZE` | PASS — teste determinístico |
| `FULL_RESOLUTION_SOURCE` | PASS |
| `POST_RECTIFICATION_QUALITY` | PASS — imagem passa ao fluxo de conferência |
| `AUTO_CAPTURE_STABLE` | PASS — gate anterior preservado |
| `GEOMETRY_STABILITY_GUARD` | PASS — auto-capture bloqueado sem candidato estável |
| `MANUAL_CAPTURE_FALLBACK` | PASS |
| `PREVIEW_CORRECTED` | PASS — preview na conferência |
| `NO_OCR_ADDED` | PASS |
| `NO_FISCAL_MUTATION` | PASS |
| `NO_STOCK_MUTATION` | PASS |
| `UNIT_TESTS` | PASS |
| `ASSEMBLE_DEBUG` | PASS |
| `LINT_DEBUG` | PASS |
| `PHYSICAL_DEVICE_SMOKE_TEST` | PASS — auto-capture e preview retificado comprovados no device |
| `REAL_FOUR_CORNER_CONTOUR` | PENDING — próximo refinamento |

## Próximo passo

`TINO-DOCUMENT-SCANNER-003` deve implementar contorno real da folha, ordenação
e interpolação dos quatro cantos entre frames, rejeição de retângulos que não
sejam documento e validação com as duas DANFEs reais antes de avançar no OCR.
