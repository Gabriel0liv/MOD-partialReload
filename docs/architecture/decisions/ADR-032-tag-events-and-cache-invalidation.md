# ADR-032 — evento e caches

Após bind, chamar `Ingredient.invalidateAll()` e publicar
`TagsUpdatedEvent` no Forge event bus. A mesma sequência é usada no rollback.
Falha de cache ou evento inicia rollback; listeners externos tornam o commit
incompatível quando seu risco não pode ser verificado.
