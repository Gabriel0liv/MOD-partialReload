# ADR-050 — staging de recipes no KubeJS 2001

Status: aceito — integração bloqueada.

## Contexto

A Fase 4J auditou KubeJS Forge `2001.6.5-build.26` e comparou `.16`/`.24`.
Partial Reload precisaria executar scripts editados contra um candidato sem
alterar script manager, listeners, globals, plugins, managers ou mundo ativos.

## Decisão

Não implementar staging nem commit no runtime 2001.6.5. O lifecycle público é
process-wide: `ScriptType.SERVER` aponta ao singleton, handlers pertencem a
`EventGroup` estático e `ScriptManager.reload` limpa listeners/caches ativos.
`RecipesEventJS.post` não é uma transformação pura e publica no
`RecipeManager`. Os bindings expõem servidor, Java e execução assíncrona.

## Alternativas rejeitadas

- chamar `reload`, `fullReload` ou comandos KubeJS;
- trocar `ServerScriptManager.instance` durante a preparação;
- copiar/restaurar containers de handlers por reflexão;
- executar handlers ativos sobre scripts antigos;
- confiar em rollback posterior de globals;
- interpretar JavaScript parcialmente fora do runtime KubeJS.

Todas perdem equivalência ou permitem side effects não reversíveis.

## Consequências

O runtime foi auditado, mas `KUBEJS_RECIPE_TRANSACTIONAL_COMMIT_NOT_IMPLEMENTED`.
Recipes vanilla/Forge e o modo deferred permanecem inalterados. Reabertura exige
API upstream ou adapter oficial com registry/contexto/bindings injetáveis e
output independente.
