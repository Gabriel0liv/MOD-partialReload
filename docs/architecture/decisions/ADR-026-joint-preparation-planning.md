# ADR-026 — planejamento conjunto

Quando `ChangeSet` contém recipes e tags relevantes, `prepare changed` escolhe
automaticamente o pipeline conjunto. `prepare tags` permanece isolado;
`prepare recipes` faz upgrade explícito quando tags relevantes estão alteradas.
O resultado continua nas categorias públicas TAGS e RECIPES.
