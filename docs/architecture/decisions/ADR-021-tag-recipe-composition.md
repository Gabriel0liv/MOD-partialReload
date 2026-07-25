# ADR-021 — composição tags e recipes

Status: aceito.

`KEEP_RECIPE_BLOCKER`: `PreparedTags` não elimina o blocker de recipes. Um
artefato conjunto exigirá recipes preparadas contra tags candidatas e serializers
sem consultas incoerentes a bindings ativos.

