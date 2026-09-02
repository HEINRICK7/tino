# TINO — Agentic Golden Path 013: Preenchimento resiliente do cadastro

**Status:** IMPLEMENTADO / reteste no aparelho pendente
**Tipo:** correção de produção
**Problema:** a transcrição chegava corretamente, mas uma resposta Gemma fora do JSON esperado impedia o preenchimento dos campos.

## Correção

- O parser aceita aliases seguros como `store`, `owner` e `cellphone`.
- O prompt passou a incluir formato JSON explícito.
- O exemplo literal de comércio/nome/telefone foi removido: ele podia ser
  copiado pelo modelo como se fosse dado do comerciante.
- Quando o Gemma falha ou retorna JSON inválido no onboarding, o TINO usa a
  frase confirmada para extrair comércio, nome e telefone.
- Mesmo quando o Gemma retorna JSON aparentemente válido, o onboarding prioriza
  a extração da frase confirmada e não aceita valores inventados pelo modelo.
- O fallback não salva dados e continua passando pela validação normal dos
  campos antes de chegar à tela.
- A frase reproduzida no aparelho foi coberta por teste Robolectric.

## Gates

| Gate | Evidência | Status |
|---|---|---|
| `TRANSCRIPT` | SpeechRecognizer committed chega ao adapter | PASS |
| `FALLBACK` | Frase real gera os três campos | PASS |
| `ALIASES` | Chaves equivalentes são canonicalizadas | PASS |
| `VALIDATION` | Campos passam pelo validator existente | PASS |
| `PIPELINE_TEST` | Transcriber → adapter → extração → validator com frase real | PASS |
| `BUILD` | Testes, assemble e lint | PASS |
| `DEVICE_RETEST` | Repetir fala no aparelho com APK corrigido | IN_PROGRESS |

## Próxima ação

Repetir o cadastro no aparelho. Se algum campo continuar vazio, capturar o
transcript exibido e o estado `ORGANIZANDO SUA FALA...` para ajustar somente a
regra de extração necessária. APK corrigido instalado com `pid=17641`, sem fatal
exception e com Gemma presente.
