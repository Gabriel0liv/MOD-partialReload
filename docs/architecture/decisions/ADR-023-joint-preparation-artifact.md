# ADR-023 — artefato conjunto atômico

Tags e recipes preparadas são encapsuladas em `PreparedTagsAndRecipes`, com
um único snapshot, view candidata, grafo e delta. Nenhum subartefato é exposto
como preparado globalmente antes da conclusão de ambos.
