# Conditions Forge e tags candidatas

`RecipeManager.fromJson` recebe `ICondition.IContext`; o contexto usado pela
preparação atual é `ICondition.IContext.EMPTY`. Forge não oferece em 1.20.1
um contexto público que substitua globalmente bindings de tags por um mapa
candidato. Conditions customizadas podem capturar registries ativos. Assim,
conditions com dependência de tags candidatas são classificadas como
`RECIPE_CONDITION_CANDIDATE_TAGS_UNSUPPORTED` e falham fechado; conditions
independentes continuam avaliáveis no contexto existente.
