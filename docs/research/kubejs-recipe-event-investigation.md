# Investigação do evento de recipes

No runtime Forge 2001.6.5 a classe é `RecipesEventJS`. Ela possui mapas/filas
mutáveis para recipes originais, adicionadas e IDs tomados. `post` desserializa
o baseline, chama o `EventHandler` global de `ServerEvents.RECIPES`, executa
hooks de plugins e publica mapas novos no `RecipeManager` recebido.

O objeto de evento pode ser novo, mas seus callbacks não são locais: pertencem
a containers estáticos registrados pelo singleton `ServerScriptManager`. Assim,
passar um manager candidato não permite executar os scripts editados sem antes
alterar listeners/globals ativos. Nenhuma chamada é feita pelo Partial Reload.
