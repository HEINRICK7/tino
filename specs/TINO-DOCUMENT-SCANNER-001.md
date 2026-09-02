# TINO Document Scanner 001 — Captura guiada de DANFE

**Status:** QUALITY GATE CONNECTED / GEOMETRY AND RECTIFICATION EXTRACTED  
**Data:** 2026-08-18

## Objetivo

Criar uma superfície de scanner local-first para capturar a tabela da DANFE
com mais qualidade antes do OCR. Este slice não altera estoque, fornecedor,
produto ou qualquer estado fiscal.

## Fluxo atual

```text
CameraX Preview
    ↓
ImageAnalysis — amostragem do plano Y
    ↓
brightness / contrast / sharpness / stability
    ↓
DocumentCaptureQualityGate
    ↓
feedback inline
    ↓
captura de alta resolução
```

O analisador [TinoDocumentFrameAnalyzer] não executa OCR e não possui acesso ao
Room. Ele fornece métricas ao mesmo `DocumentCaptureQualityGate` já testado no
`tino-fiscal-core`.

## Comportamento da câmera

- CameraX em paisagem e tela imersiva;
- moldura ampla para a tabela;
- botão de fechar compacto, fora da área central;
- orientação dinâmica: `Posicione`, `Aproxime`, `Melhore a iluminação`,
  `Mantenha firme` e `Pronto` aparecem na barra inferior;
- captura automática após estado `READY` estável;
- captura manual continua disponível como fallback;
- foto segue para o fluxo de conferência existente;
- nenhuma entrada fiscal é confirmada automaticamente.

## Limites honestos deste slice

Ainda não estão implementados neste slice:

- detecção de contorno angulado robusta com quatro cantos reais;
- correção de perspectiva orientada por contorno em todos os cenários;
- OCR estrutural de tabela.

O `sheetDetected` do analisador de frames continua sendo uma heurística
conservadora baseada em contraste e densidade de bordas. A captura agora passa
por uma segunda etapa de geometria no arquivo de alta resolução: candidato,
validação de quadrilátero, cálculo de dimensões e retificação. O detector
atual usa uma região clara conectada e produz um quadrilátero normalizado
axis-aligned; ele ainda não acompanha cantos inclinados da folha como um
scanner maduro faria.

## Gates

| Gate | Estado |
|---|---|
| `CAMERAX_PREVIEW` | PASS |
| `IMAGE_ANALYSIS_CONNECTED` | PASS |
| `BRIGHTNESS_METRIC` | PASS |
| `SHARPNESS_METRIC` | PASS |
| `STABILITY_METRIC` | PASS |
| `QUALITY_GATE_REUSE` | PASS |
| `AUTO_CAPTURE_READY` | PASS — baseado na heurística atual |
| `MANUAL_CAPTURE_FALLBACK` | PASS |
| `NO_OCR_IN_SCANNER_SLICE` | PASS |
| `NO_DOMAIN_MUTATION` | PASS |
| `QUAD_ORDERING_AND_VALIDATION` | PASS — testes JVM |
| `RECTIFIED_OUTPUT_SIZE` | PASS — teste determinístico |
| `PERSPECTIVE_RECTIFICATION_RUNTIME` | PASS — executada após captura no Android |
| `CORRECTED_IMAGE_PREVIEW` | PASS — conectado à conferência |
| `REAL_CONTOUR_FOLLOWING` | PENDING — detector atual é axis-aligned |
| `PHYSICAL_DEVICE_SMOKE_TEST` | PENDING — APK instalado; device está bloqueado |

## Próximo slice

`TINO-DOCUMENT-SCANNER-003` deve substituir o candidato axis-aligned por
contorno real com quatro cantos, estabilidade geométrica entre frames e
perspectiva acompanhando a folha. Só depois conectar OCR estrutural e revisão
de produtos.
