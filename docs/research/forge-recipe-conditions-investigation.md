# Forge recipe conditions

Forge 47.4.10 usa `CraftingHelper.processConditions` e
`ICondition.IContext`; `RecipeManager.fromJson` recebe esse contexto. Condition
false omite a recipe sem erro. Serializer de condition desconhecido ou
exception de avaliação é erro estruturado e torna o candidato inaplicável.
Nesta fase o contexto usado é `ICondition.IContext.EMPTY`, documentando que
condições dependentes de tags reais exigem a futura preparação de tags.
