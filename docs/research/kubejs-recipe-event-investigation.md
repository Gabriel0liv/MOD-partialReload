# Investigação do evento de recipes

`RecipesKubeEvent` possui mapas/filas mutáveis e expõe `post`, `discoverRecipes` e `applyChanges`. Conhece schemas, registries, ResourceManager, plugins e `ServerScriptManager`; não é um DTO puro. Filtros por ID/output/input/mod e operações add/remove/replace aparecem nas classes recipe/filter/match.

Como o evento aplica mudanças no mapa fornecido e pode invocar plugins/addons, chamá-lo contra o runtime ativo violaria a não mutação. A API observada não permite instância isolada com registries e schemas candidatos sem reproduzir o lifecycle. Nenhuma chamada é feita pelo mod.

