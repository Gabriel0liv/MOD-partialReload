# ADR-006 — Predicates acoplados ao grafo de loot

Status: Aceito — 2026-07-24

## Contexto

`LootDataManager` 1.20.1 carrega predicates, item modifiers e loot tables no
mesmo map candidato e valida o conjunto completo com um `LootDataResolver`.
Tabelas e modifiers podem consumir conditions sob context parameter sets mais
restritos que `ALL_PARAMS`.

## Decisão

Classificar a capacidade como `PREDICATES_COUPLED_TO_LOOT`. A Spec 009 não cria
`PreparedPredicates`. Predicates permanecem visíveis em scan/diff/plan, com
blocker explícito, e serão preparados somente junto de loot tables e item
modifiers.

## Consequências

A fase 2 concentra implementação em functions. A futura fase de loot precisa
criar um resolver candidato completo, usar serializers Forge/modded, validar
todos os três tipos e preservar o manager ativo.

## Alternativas rejeitadas

- Apenas validar JSON com Gson: prova sintaxe, não semântica contextual.
- Resolver predicates contra o `LootDataManager` ativo: mistura gerações.
- Mutar temporariamente `elements`: viola não mutação e exige acesso privado.
