# Inventário MineDev

## Fonte

Checkout somente leitura de [`Gabriel0liv/MineDev`](https://github.com/Gabriel0liv/MineDev), commit `5b3405c82b17946e2943b8604d2c39b50fa6ae52` (2026-07-24). Foram inspecionados `DrathosCore/`, `DrathosOrigins_2.0.0/` e `.docs/`.

## Inventário observado

Foram encontrados 1.238 arquivos nos três escopos. Nos dois datapacks, a distribuição por diretório de dados foi:

| Loader/diretório | Arquivos |
|---|---:|
| `powers` | 538 |
| `silentgear_materials` | 266 |
| `functions` | 98 |
| `tags` | 74 |
| `item_modifiers` | 34 |
| `silentgear_traits` | 34 |
| `predicates` | 19 |
| `origins` | 16 |
| `damage_type` | 11 |
| `advancements` | 9 |
| `loot_tables` | 8 |
| `global_powers` | 1 |
| `origin_layers` | 1 |
| `recipes` | 1 |
| `worldgen` | 1 |

O conteúdo inclui tags de itens, biomas, funções e damage types. O biome `drathoscore:submundo` está em `worldgen/biome`, portanto é registry dinâmico e não deve ser tratado como JSON comum seguro.

## Estado persistente e execução

As functions contêm 66 comandos de scoreboard e 203 operações de entity tags. Esses valores representam estado vivo que não pertence aos arquivos carregados. Recompilar uma function não autoriza resetar scoreboards ou tags.

As tags públicas vanilla agregam:

- `minecraft:load`: `drathos:setup_player_ids`, setups de Afrodite/Artemis e `drathoscore:load`;
- `minecraft:tick`: ticks de armadilhas, santuário, IDs, flores e `drathoscore:totem_limit/tick`.

Logo, a categoria pública correta é `functions`; execução de load functions deve ser uma política interna futura, nunca uma categoria.

## Grafo de dependências real

- advancements são usados como detectores e disparam functions;
- powers referenciam functions, predicates, tags, damage types e outros powers;
- origins referenciam listas de powers;
- a origin layer referencia origins;
- global power sets concedem powers fora da seleção de origin;
- DrathosOrigins referencia funções e recursos de DrathosCore, e DrathosCore contém recursos consumidos pelo conjunto Origins;
- item modifiers e loot tables referenciam itens/tags e outros elementos de loot;
- materiais de namespaces `silentgear`, `silentcompat` e outros referenciam traits de `silentgear`, `silentcompat` e `silentgems`;
- tags de DrathosOrigins incluem itens opcionais do Silent Gear.

## Consequências arquiteturais

1. Namespace, datapack e categoria não delimitam sozinhos uma transação.
2. O scanner deve preservar recurso individual e pack de origem.
3. O planner futuro precisará de dependências cruzadas por ID e por loader.
4. `powers`, `origins`, `origin_layers` e `global_powers` pertencem à categoria pública `origins`.
5. Loot injetado pelo Silent Gear ainda pertence à categoria pública `loot`, com contribuição de provider Silent Gear.
6. Worldgen e damage types são classificados, mas `RESTART_REQUIRED` na fase 1.
