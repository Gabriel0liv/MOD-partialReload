# Investigação KubeJS recipes

## Runtime real

A auditoria deixou de depender apenas da branch GitHub. Foram inspecionados os
JARs, sources, POMs e Gradle metadata publicados de KubeJS Forge
`2001.6.5-build.16`, `.24` e `.26`. O alvo `.26` usa Rhino Forge
`2001.2.2-build.17` e Architectury Forge `9.1.12`.

## Registro de eventos

`ServerEvents.RECIPES` é um `EventHandler` estático criado no `EventGroup`
global `ServerEvents`. Ao executar `ServerEvents.recipes(callback)`, Rhino chama
`EventHandler.call`; este usa `ScriptType` do contexto, resolve
`type.manager.get()` e anexa o callback ao container global daquele handler.
Não há argumento para um event registry local.

O registro só é aceito quando `type.manager.get().canListenEvents` está ativo.
Para `ScriptType.SERVER`, o supplier é
`ServerScriptManager::getScriptManager`, que retorna o singleton. Logo um
`ServerScriptManager` secundário não controla o destino do callback.

## RecipesEventJS

A instância mantém `originalRecipes`, `addedRecipes`, IDs tomados e funções de
schemas. `post(recipeManager, jsonMap)`:

1. avalia conditions e desserializa o baseline em wrappers KubeJS;
2. dispara os listeners globais de `ServerEvents.RECIPES`;
3. materializa recipes vanilla/modded com schemas e serializers registrados;
4. chama hooks `injectRuntimeRecipes` de todos os plugins;
5. reconstrói `byName`/`byType` e os grava no `RecipeManager` recebido.

Isso descreve corretamente `shaped`, `shapeless`, cooking, stonecutting,
smithing, `custom`, `remove`, `replaceInput` e `replaceOutput`, mas não constitui
uma função pura. O baseline correto de uma integração futura continua sendo a
visão vanilla/Forge do `ResourceManager`, nunca o manager já transformado.

## Estado compartilhado relevante

- `ServerScriptManager.instance` e `ScriptType.SERVER.manager`;
- registry de `EventGroup` e containers de `EventHandler`;
- `KubeJSPlugins` e seus caches/hooks;
- `RecipeNamespace`/schemas e mappings estáticos;
- `KubeJSReloadListener.resources` e `recipeContext`;
- `UtilsJS.staticServer` e `staticRegistryAccess`;
- `RecipesEventJS.instance`, custom ingredients e result callbacks;
- serializers especiais, actions e packs virtuais.

`ScriptManager.reload` altera vários desses itens antes mesmo de um callback de
recipe ser invocado. Rollback posterior não desfaz IO, threads, comandos ou
mutações no servidor acessíveis pelos bindings.

## Estratégias avaliadas

**A — API oficial isolável:** inexistente no build `.26`.

**B — clone tipado:** bloqueado porque `ScriptType.SERVER` e `ServerEvents` não
são injetáveis. Um clone exigiria trocar globals ou copiar estado privado e
continuaria oferecendo bindings do servidor ativo.

Conclusão: não executar handlers, não criar adapter, não produzir candidate e
não reutilizar o commit existente. O erro é
`KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`.
