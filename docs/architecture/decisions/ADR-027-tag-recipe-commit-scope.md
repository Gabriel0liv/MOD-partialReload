# ADR-027 — escopo de commit de tags e recipes

O primeiro commit conjunto suporta apenas registries estáticos com holders já
presentes (`item`, `block`, `fluid`, `entity_type`, `game_event`, `mob_effect`,
`enchantment`). Biome, damage type, worldgen, dimension type e registries Forge
custom permanecem `PREPARE_ONLY` ou `RESTART_REQUIRED`. Qualquer registry fora
da allowlist bloqueia antes da mutação.
