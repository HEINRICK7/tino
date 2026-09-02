# TINO Document Intake — Camera DANFE / XML Contract

**Status:** CORE CONTRACT COMPLETE / CAMERA ADAPTER PENDING  
**Data:** 2026-08-18

## Decisão

A experiência será câmera guiada, mas o dado enviado para reconhecimento será
uma captura HD única e estável:

```text
CameraX Preview + ImageAnalysis
        ↓
quality gate / enquadramento / estabilidade
        ↓
ImageCapture em alta qualidade
        ↓
perspectiva + recorte da tabela
        ↓
OCR/layout adapter
        ↓
ImportedProduct[]
        ↓
matching + packaging + preview fiscal existente
```

O mesmo contrato também será usado por:

```text
NFeXmlAdapter ──────┐
DanfeVisionAdapter ─┴→ ProductImportPort → matching → preview → confirmação
```

Nenhum adapter cria produto, atualiza estoque ou executa commit sozinho.

## Core implementado

Em `tino-fiscal-core`:

- `ImportedProduct` sem dependência de Room, CameraX, OpenCV ou OCR;
- `ProductImportPort` como porta de extração;
- resultados `Success`, `NeedsReview` e `Unavailable`;
- `DocumentFrameMetrics`;
- `DocumentCaptureQualityGate` com orientação para detectar folha, distância,
  luz, nitidez e estabilidade;
- três testes cobrindo gate e contrato comum.

O gate exige folha detectada, enquadramento entre 55% e 97%, luminosidade,
nitidez e três frames estáveis antes de retornar `ReadyToCapture`.

## Decisões de runtime

- CameraX será responsável por `Preview`, `ImageAnalysis` e `ImageCapture`; a
  documentação Android confirma que esses use cases podem ser combinados.
- A análise contínua não executará OCR pesado em todos os frames.
- A transformação de perspectiva e localização da tabela devem ser um adapter
  de visão, não regra do domínio.
- PP-StructureV3/Paddle não entra como dependência do core. A validação do
  caminho Android deve escolher explicitamente entre modelo mobile local e
  serviço externo; a documentação do Paddle registra que o deployment Android
  suporta apenas um subconjunto de modelos.
- Foto, XML e resultado extraído continuam evidências separadas até a
  confirmação fiscal humana.

## Gates

| Gate | Estado |
|---|---|
| `IMPORTED_PRODUCT_CONTRACT` | PASS |
| `CAMERA_GUIDANCE_RULES` | PASS |
| `AUTO_CAPTURE_QUALITY_GATE` | PASS — core puro |
| `CAMERAX_PREVIEW` | PENDING |
| `CAMERAX_IMAGE_ANALYSIS` | PENDING |
| `CAMERAX_HD_CAPTURE` | PENDING |
| `PERSPECTIVE_CORRECTION` | PENDING |
| `TABLE_LOCALIZATION` | PENDING |
| `PADDLE_MOBILE_RUNTIME` | PENDING |
| `PRODUCT_MATCHING_REUSE` | READY — adapter deve consumir core existente |
| `NO_MUTATION_BEFORE_CONFIRMATION` | PASS — contrato não expõe commit |
| `OFFLINE` | PENDING para o adapter de visão |
| `TESTS` | PASS — core |

## Próximo corte

Implementar a tela CameraX com preview, orientação visual e captura HD. O
primeiro teste físico deve provar apenas:

```text
folha detectada → alinhamento → estabilidade → captura → imagem salva localmente
```

Sem OCR e sem estoque nesse primeiro teste. Depois conectamos o adapter de
visão e medimos extração real antes de liberar o commit fiscal.
