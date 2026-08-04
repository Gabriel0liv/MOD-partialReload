# Spec 018 — commit transacional conjunto de loot data

## Estado

`IMPLEMENTED_ACCEPTED`

## Objetivo

Publicar, em um único safe point da server thread, a geração completa de
predicates, item modifiers e loot tables preparada por `PreparedLootData`.
As três categorias são inseparáveis porque compartilham o mesmo
`LootDataManager` e podem possuir referências cruzadas.

## Contrato do runtime alvo

Minecraft 1.20.1 com Forge 47.4.10 e mappings oficiais possui, em
`LootDataManager`, exatamente:

- `elements: Map<LootDataId<?>, ?>`;
- `typeKeys: Multimap<LootDataType<?>, ResourceLocation>`;
- `getElement(LootDataId<T>)` e `getKeys(LootDataType<?>)` públicos;
- `EMPTY_LOOT_TABLE_KEY`, que sempre resolve para `LootTable.EMPTY`.

O `apply` vanilla constrói `ImmutableMap` e `ImmutableMultimap`, valida os
elementos e publica `elements` antes de `typeKeys`. Esta spec usa um bridge
dedicado aos dois campos exatos; incompatibilidade falha com
`LOOT_DATA_MANAGER_LAYOUT_UNSUPPORTED` antes de qualquer mutação.

## Invariantes

- A identidade de `MinecraftServer`, `ReloadableServerResources` e
  `LootDataManager` não muda.
- `PreparedLootData.COMPLETE_SCOPE` contém exatamente PREDICATES,
  ITEM_MODIFIERS e LOOT.
- A candidata é reconstruída integralmente do artefato; IDs removidos não são
  preservados e não existe patch incremental.
- `minecraft:empty` aponta para `LootTable.EMPTY`.
- `elements` e `typeKeys` pertencem à mesma geração lógica.
- A publicação ocorre somente na server thread, no `ServerTickEvent.END`, com
  preflight repetido imediatamente antes da primeira mutação.
- Jogadores conectados não bloqueiam, não têm menus fechados, não ficam stale e
  não recebem sincronização ou aviso.
- O `LootModifierManager` e sua geração ativa não são alterados.
- Uma geração anterior confirmada é retida para rollback único.

## Preflight

Os preflights validam artefato aplicável, snapshot atual, identidade do manager,
layout do bridge, mapa completo, índice coerente, `minecraft:empty`, fingerprint
ativo, ausência de transação concorrente e estado diferente de `DEGRADED`.

## Publicação, verificação e rollback

Na janela crítica, o serviço captura a geração ativa, publica o mapa completo,
publica o índice completo e verifica identidade, lookups, remoções, chaves por
tipo, contagens, `minecraft:empty` e fingerprint. Falha depois da primeira
mutação restaura ambos os campos e verifica a geração anterior. Falha de
restauração termina em `DEGRADED`. O rollback manual restaura somente a geração
anterior retida e pode ocorrer com jogadores conectados.

## Semântica observável

Lookups futuros usam a nova geração. Itens, drops, inventários e containers já
desempacotados não mudam retroativamente. Referências diretas retidas por mods
externos podem continuar apontando para objetos antigos.

## Fora de escopo

Global Loot Modifiers, LootJS/KubeJS, integrações externas, regeneração de
conteúdo, reload global, troca do manager, client sync, Mixins e reflexão
genérica.

## Critérios de aceitação

Testes unitários, GameTests, acceptance dedicada comportamental, runner
consolidado, clean build e inspeção do JAR devem passar antes da promoção.

## Evidência de aceitação

Em 2026-08-04, 24/24 GameTests da Fase 4G passaram dentro do total global de
60/60. A acceptance dedicada publicou a geração B com jogador conectado,
comprovou predicate, item modifier, loot table, adição e remoção, restaurou A
por rollback manual e encerrou sem processos residuais. O runner consolidado
terminou com `ALL_ACCEPTANCE_PASSED`.
