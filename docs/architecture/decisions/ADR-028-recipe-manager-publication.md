# ADR-028 — publicação do RecipeManager

Escolha: `PRESERVE_MANAGER_IDENTITY_REPLACE_INTERNAL_STATE`, usando a API
pública `RecipeManager.replaceRecipes(Iterable<Recipe<?>>)`. A coleção completa
é construída antes do commit; a identidade do manager permanece igual e a
geração anterior retém a coleção real para rollback. Nenhum map privado é
alterado entrada a entrada.
