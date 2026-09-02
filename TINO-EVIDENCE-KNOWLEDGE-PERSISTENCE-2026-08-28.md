# Catálogo de conhecimento aprovado — persistência governada

**Data:** 28/08/2026

## Entregue

- `ApprovedKnowledgeCatalogPort` agora é assíncrono e continua independente de
  Room;
- `RoomApprovedKnowledgeCatalog` persiste a versão ativa e a anterior;
- a migration `25→26` cria `approved_knowledge_catalogs`;
- JSON é validado ao restaurar; catálogo inválido recua para o corpus built-in
  aprovado;
- ativação e rollback limpam/substituem o par ativo/anterior dentro de uma
  transação Room;
- a consulta continua limitada às coleções permitidas e desconhecidos seguem
  indisponíveis.

## Evidência automatizada

`gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain`
passou com **556 testes do app**, 0 falhas. O teste
`RoomApprovedKnowledgeCatalogTest` verifica bootstrap, restauração em nova
instância, ativação e rollback com provenance de fonte preservada.

`gradle :tino-fiscal-core:test --no-daemon --console=plain` também passou.

Build final desta correção: `app/build/outputs/apk/debug/app-debug.apk`,
587.477.741 bytes, SHA-256
`f355225c1ee8eb8a9dcf3284788357caf6999da32aa14e60d83989aa87a516c1`.

## Limite explícito

Isso fecha a persistência local de conteúdo revisado; não é RAG externo
produtivo. Ainda faltam fonte externa autenticada, ingestão aprovada fora do
processo, política de credenciais e observabilidade operacional dessa fonte.
