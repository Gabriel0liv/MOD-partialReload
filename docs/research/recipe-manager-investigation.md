# RecipeManager 1.20.1

O source oficial Forge 47.4.10 expõe `RecipeManager.fromJson(id, json,
ICondition.IContext)`, `getRecipes()`, `getRecipeIds()`, `byKey()` e maps
privados por `RecipeType`/ID. O reload vanilla usa `listResources("recipes", ...)`
e `SimpleJsonResourceReloadListener`; o ResourceManager já resolve a prioridade
dos packs e remove recursos ausentes.

`fromJson` consulta `RecipeSerializer`/`RecipeType` registrados, portanto cobre
serializers vanilla, Forge e mods carregados sem parser paralelo. A preparação
usa a visão vencedora do ResourceManager e nunca chama `apply` nem
`replaceRecipes`. RecipeManager ativo, recipe book, menus e sync permanecem
fora do escopo.

Recipes especiais podem ser JSON com serializer especial; conteúdo registrado
somente por código não aparece na visão de resources e é fora da preparação.
