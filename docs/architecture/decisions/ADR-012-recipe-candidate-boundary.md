# ADR-012 — Fronteira do candidato de recipes

Status: Aceito.

O candidato é reconstruído integralmente via ResourceManager e
`RecipeManager.fromJson`, mas nunca publicado. Isso preserva RecipeManager,
recipe book, menus e sincronização até uma spec de commit.
