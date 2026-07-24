# ADR-001 — Categorias públicas amplas

Status: Aceito — 2026-07-24

## Contexto

MineDev contém centenas de resources e múltiplos loaders que contribuem aos mesmos domínios.

## Decisão

Expor somente FUNCTIONS, ADVANCEMENTS, PREDICATES, RECIPES, LOOT, ITEM_MODIFIERS, TAGS, ORIGINS, KUBEJS, SILENTGEAR, DYNAMIC_REGISTRIES e UNKNOWN. Detalhes como tick/load, tipo de loot, power ou material ficam em providers/recursos.

## Consequências

Comandos permanecem compreensíveis; providers podem agregar dependências internas. Diagnóstico precisa exibir provider/recurso para não esconder complexidade.

## Alternativas rejeitadas

Categoria por arquivo, mecânica, tag especial ou tipo de material: interface explosiva e incapaz de representar transações.
