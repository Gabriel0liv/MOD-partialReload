# Investigação KubeJS

## Versão e fonte

Fonte [`KubeJS-Mods/KubeJS`](https://github.com/KubeJS-Mods/KubeJS), branch `2001`, commit `ba142541dcc1d230383f4a55e38dd92ff10d1029`. `gradle.properties` declara Minecraft `1.20.1` e KubeJS `2001.6.5`. Esta é a versão candidata pesquisada, não uma dependência adicionada.

## Carregamento de scripts

`ScriptType.SERVER` aponta para `server_scripts` e `ServerScriptManager`. Durante o pipeline global, Mixins envolvem o `CloseableResourceManager`, criam packs virtuais, executam `ScriptManager.reload`, eventos de geração de data, hooks de plugins, pre-tags e inicializam o evento de recipes.

`ScriptManager.reload` chama `unload`, limpa caches de plugins, carrega arquivos do diretório e recursos `kubejs/`, cria um novo contexto Rhino e executa scripts. `ScriptType.unload` percorre todos os `EventGroup`/`EventHandler` e remove os containers do tipo SERVER. Portanto listeners antigos do próprio KubeJS são removíveis, mas somente pelo lifecycle interno completo; chamar APIs de evento isoladas não é equivalente.

## Recipes e baseline

O Mixin de `RecipeManager` entrega a `RecipesEventJS` o mapa JSON de recipes do datapack antes da aplicação vanilla. Cada instância nova mantém:

- `originalRecipes`, reconstruído do mapa JSON baseline;
- `addedRecipes`, novo por ciclo;
- IDs tomados e callbacks específicos do evento.

Após `ServerEvents.RECIPES`, KubeJS materializa um mapa novo e substitui `recipeManager.byName` e `recipeManager.recipes` via Mixin. Um reload parcial futuro precisa reconstituir o baseline do `ResourceManager`, não partir do `RecipeManager` já modificado, ou alterações acumularão.

## Tags, loot e addons

- pre-tag events são reunidos pelo `ServerScriptManager` antes dos loaders;
- eventos de loot operam sobre o mapa JSON antes da desserialização do `LootDataManager`, por Mixins;
- plugins recebem `clearCaches`, `onServerReload` e podem injetar recipes runtime;
- addons podem registrar handlers, schemas, wrappers e hooks, tornando a compatibilidade dependente do conjunto e versão instalados.

## Startup scripts

`startup_scripts` têm lifecycle separado e registram conteúdo, tipos e modificações de startup. Apesar de existir mensagem de reload de startup para desenvolvimento, esse domínio não é seguro para hot reload transacional do servidor e permanece `RESTART_REQUIRED`.

## Superfície de integração

As classes centrais são públicas em Java, mas os pontos que interceptam managers/mapas dependem de Mixins e campos expostos pelo KubeJS. Não há um contrato público único que prepare, valide e faça commit de apenas `server_scripts`/recipes com rollback.

Opções futuras:

1. `compileOnly` + runtime opcional contra `2001.6.5`, com adapter tipado;
2. módulo separado por versão;
3. Mixin versionado apenas se uma lacuna exata for comprovada.

Reflection genérica foi rejeitada. Nenhuma dependência KubeJS entra na fase 1.
