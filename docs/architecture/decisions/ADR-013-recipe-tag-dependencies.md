# ADR-013 — Dependências de tags de recipes

Status: Aceito.

Política: `BLOCK_WHEN_RELEVANT_TAGS_CHANGED`. Tags extraídas dos ingredientes
são registradas; somente mudança relevante no mesmo ChangeSet gera blocker,
pois preparação/commit geral de tags ainda não existe.
