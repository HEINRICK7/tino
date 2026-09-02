# TINO — Business Data Source Onboarding — Android Evidence

Status: **IMPLEMENTED / VALIDATED**

O Android implementa o handoff do contrato autoritativo do backend sem
redesenhá-lo. A origem dos dados continua pertencendo ao `Business`.

## Implementação

- `domain/onboarding/BusinessDataSourceApi.kt` define somente o `GET` e o
  `PUT` previstos no contrato;
- `core/network/BusinessDataSourceApi.kt` usa
  `/api/v1/businesses/{businessId}/data-source`, payloads snake_case exatos e
  valida a resposta autoritativa;
- `domain/onboarding/BootstrapOnboarding.kt` configura a origem somente depois
  que um Business novo existe; para Business existente, lê e herda a fonte;
- `TinoApp.kt` expõe apenas “Não, começar no TINO”, “Sim, conectar meu sistema”
  e “Doces & Sonhos”; não exibe URL, API key, token, endpoint ou credencial;
- `TinoViewModel.kt` mantém `business_id` remoto separado do perfil local;
- `core/di/AppModule.kt` usa a base HTTPS oficial e a renovação de sessão já
  existente;
- `core/network/BootstrapApi.kt` lê `data_source_type` quando retornado pelo
  backend, sem transformá-lo em regra local.

## Evidência de contrato

Payload nativo:

```json
{"source_type":"TINO_NATIVE","provider":null}
```

Payload Doces & Sonhos:

```json
{"source_type":"EXTERNAL_API","provider":"DOCES_SONHOS"}
```

O app não chama a API Doces & Sonhos diretamente e nunca envia credenciais do
provider. Em outro aparelho, a configuração é obtida com `GET` para o mesmo
`business_id`; a seleção local não sobrescreve uma configuração existente.

## Testes

- `BusinessDataSourceApiTest`: payload nativo, provider externo, parsing da
  resposta autoritativa e leitura em outro aparelho;
- `BootstrapApiTest`: leitura do contrato de bootstrap em snake_case;
- `gradle :app:testDebugUnitTest --no-daemon`: **PASS**.

Não foi criada migration Room: a origem não é persistida no Android como
autoridade concorrente. O perfil local continua sendo salvo somente após o
bootstrap final retornar `READY`.

Referências do handoff no backend:

- `docs/TINO-BUSINESS-DATA-SOURCE-ONBOARDING-CONTRACT.md`;
- `docs/TINO-BUSINESS-DATA-SOURCE-ONBOARDING-ANDROID-EVIDENCE.md`;
- `docs/TINO-ANDROID-API-INTEGRATION.md`.
