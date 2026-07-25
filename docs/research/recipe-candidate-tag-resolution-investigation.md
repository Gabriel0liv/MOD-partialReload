# Resolução de tags em recipes

Em Minecraft 1.20.1, `RecipeManager.fromJson` delega ao `RecipeSerializer`.
Ingredients vanilla baseados em `tag` preservam a referência lógica durante o
parse (`Ingredient.TagValue`/`TagKey`); membros são materializados por
`Ingredient.getItems`/`Ingredient.test`, que consultam holders ativos. Portanto
o parse estrutural é seguro, mas uma validação que invoque esses métodos não é
uma validação contra o candidato.

O provider conjunto valida a referência e os membros por IDs contra
`CandidateTagResolutionView` e não executa crafting, `getItems`, sync ou
`RecipeManager.apply`. Serializers que materializam holders no `fromJson` não
podem ser considerados seguros sem evidência e falham fechado.
