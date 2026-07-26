# ADR-035 — preflight repetido e escopo de registry

O comando apenas enfileira a transação. No `ServerTickEvent.END`, o serviço
repete a validação de identidade do artefato, players, `RecipeManager`,
`RegistryAccess`, fingerprint de compatibilidade e hashes de tags/recipes.
Somente registries cujo recurso de tag mudou no snapshot são mutados. Qualquer
mudança fora da allowlist (`item`, `block`, `fluid`, `entity_type`,
`game_event`, `mob_effect`, `enchantment`) falha antes de `bindTags`.
