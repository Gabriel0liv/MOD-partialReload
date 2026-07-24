# ADR-006 — Predicates acoplados ao grafo de loot

Status: Aceito — 2026-07-24

## Contexto

`LootDataManager` 1.20.1 carrega predicates, item modifiers e loot tables no
mesmo map candidato e valida o conjunto completo com um `LootDataResolver`.
Tabelas e modifiers podem consumir conditions sob context parameter sets mais
restritos que `ALL_PARAMS`.

## Decisão

Classificar a capacidade como `PREDICATES_COUPLED_TO_LOOT`. A Spec 009 não cria
`PreparedPredicates` independente. Predicates permanecem visíveis em
scan/diff/plan e, na fase 3B (Spec 010), são preparados somente no candidato
conjunto de loot tables e item modifiers (`PreparedLootData`).

## Consequências

A preparação de functions continua isolada. A fase 3B cria um resolver
candidato completo, usa serializers Forge/modded, valida os três tipos e
preserva o manager ativo. O ADR não autoriza commit ou mutação de maps privados.

## Alternativas rejeitadas

- Apenas validar JSON com Gson: prova sintaxe, não semântica contextual.
- Resolver predicates contra o `LootDataManager` ativo: mistura gerações.
- Mutar temporariamente `elements`: viola não mutação e exige acesso privado.
