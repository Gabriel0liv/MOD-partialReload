# ADR-004 — SPI de providers

Status: Aceito — 2026-07-24

## Contexto

Vanilla, Forge e mods possuem loaders diferentes dentro das mesmas categorias.

## Decisão

Usar `ReloadProvider` experimental com `id`, `categories`, `compatibility`, `scan`, `validate` e `createPlan`. Registry interno rejeita IDs duplicados e consulta por categoria. Contratos de commit ficam fora até implementados.

## Consequências

Integrações opcionais não contaminam o core e podem ter versionamento próprio. A SPI 0.x pode mudar.

## Alternativas rejeitadas

Switch global por mod/path e interface que já prometa rollback.
