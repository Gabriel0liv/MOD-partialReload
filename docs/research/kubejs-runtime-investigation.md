# Investigação do runtime KubeJS

O cache local contém somente `kubejs-neoforge:2101.7.2-build.277`, com `rhino:2101.2.7-build.77`, exigindo NeoForge `[21.1.199,)`. Isso corresponde a Minecraft 1.21.1, não ao alvo Forge 47.4.10/Minecraft 1.20.1. Não há JAR Forge 1.20.1, metadata MineDev ou addon KubeJS local.

No source disponível, `dev.latvian.mods.kubejs.server.ServerScriptManager` controla scripts, packs virtuais, `reload()` e `fullReload()`. `dev.latvian.mods.kubejs.recipe.RecipesKubeEvent` recebe manager/resource manager e mantém `originalRecipes`, `addedRecipes` e `removedRecipes`. `post(...)` descobre recipes, dispara handlers e aplica mutações ao mapa de JSON recebido.

`ServerScriptManager.fullReload()` agenda o comando global `reload`; portanto é proibido. O construtor/event dependem de globals, plugins, schemas e registries. Não existe método público de clone ou staging isolado.

Decisão: `RESEARCH_ONLY` para o alvo atual. A integração futura será módulo tipado/versionado após prova de staging.

