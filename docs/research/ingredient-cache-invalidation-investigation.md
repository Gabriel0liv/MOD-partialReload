# Cache de Ingredient

`Ingredient` mantém `itemStacks`, `stackingIds` e contador estático de
invalidação. Forge/Minecraft 1.20.1 expõe `Ingredient.invalidateAll()`, que é o
hook oficial para invalidar resultados derivados após tags. A operação é
server-thread e deve ocorrer tanto no commit quanto no rollback.
