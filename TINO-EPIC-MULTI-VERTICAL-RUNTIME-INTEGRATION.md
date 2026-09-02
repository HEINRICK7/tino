# TINO — Epic: Multi-Vertical Runtime Integration

**Estado:** `FOUNDATION_IN_PROGRESS / M8_PASS`  
**Tipo:** epic pós-G4.1  
**Escopo:** transformar a fundação `BusinessProfile` em runtime multi-vertical
funcional, mantendo um único APK e uma arquitetura orientada a capabilities.

Os contratos congelados desta epic estão em [TINO-PRODUCT-CONSTITUTION.md](TINO-PRODUCT-CONSTITUTION.md)
e [TINO-MULTI-VERTICAL-RUNTIME-SPEC.md](TINO-MULTI-VERTICAL-RUNTIME-SPEC.md).

## Contexto

O domínio já possui `BusinessProfile`, `BusinessVertical`, `BusinessModule` e
`TinoModuleRegistry`. Nesta primeira implementação, o perfil persistido e o
`DefaultBusinessContextResolver` passaram a ser a fonte de composição do
aplicativo executável; novos packs continuam deliberadamente fora do escopo.

O produto deve evoluir para um TINO baseado em capacidades, não para um
conjunto de telas condicionadas diretamente por segmento.

## Dependência interna

```text
BusinessProfile persistido
  → ModuleRegistry por perfil
  → filtragem de capabilities
  → composição de Home e navegação
  → vertical packs reais
```

O `BusinessVertical` seleciona módulos. Os módulos fornecem capabilities e
metadata. A UI, a navegação e o Agentic Shell consomem essa metadata sem
espalhar condicionais como `when (segment)` pelo aplicativo.

## Sequência de implementação

1. Persistir `BusinessProfile` e módulos ativos no Room, com migration segura.
2. Integrar o perfil ao onboarding e às configurações, incluindo estados de
   ausência, edição e recuperação.
3. Fazer o `TinoModuleRegistry` derivar módulos e capabilities do perfil.
4. Filtrar capabilities disponíveis no Agentic Shell e no contexto de sessão.
5. Compor Home, navegação, ações e estados a partir de metadata de capability.
6. Implementar packs reais de Bakery, Restaurant, Store e serviços somente
   quando suas capabilities e contratos estiverem definidos.

## Fora do escopo desta rodada

- não iniciar implementação antes de G4.1 virar `PASS_FULL`;
- não iniciar G6 enquanto G4.1 estiver pendente;
- não criar novos `when (BusinessVertical)` espalhados na UI;
- não tratar os enums atuais como suporte funcional já entregue;
- não criar APKs separados por segmento.

## Critérios de conclusão da epic

- um perfil persistido sobrevive a restart e é a fonte de verdade do runtime;
- módulos ativos podem ser compostos sem alterar o APK;
- capabilities, vocabulário e analytics são derivados dos módulos ativos;
- Agentic Shell, Home e navegação não expõem capacidades desabilitadas;
- o perfil pode ser alterado sem apagar dados comerciais;
- pelo menos um pack não varejista passa por um fluxo integrado completo;
- testes de domínio, Room, Agentic Shell, UI e validação física passam.

## Gate de entrada

`G4.1 = PASS_FULL`, incluindo fala longa, revisão/edição, continuação,
correction learning e fallback integrado à voz no dispositivo.
