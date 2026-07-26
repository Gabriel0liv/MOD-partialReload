# Publicação do RecipeManager

`RecipeManager` 1.20.1 possui maps privados `recipes` por `RecipeType` e
`byName`, mas também expõe `replaceRecipes(Iterable<Recipe<?>>)`. O método
reconstrói os índices completos e preserva a identidade do manager. A
estratégia escolhida evita AT para esses maps e retém a coleção real anterior
para rollback.
