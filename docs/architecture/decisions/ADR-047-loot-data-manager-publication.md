# ADR-047 — publicação interna do LootDataManager

## Estado

Aceita e validada pela Fase 4G.

## Contexto

Predicates, item modifiers e loot tables compartilham o `LootDataManager`. No
runtime alvo, os lookups públicos leem `elements` e `typeKeys`, ambos privados,
e não existe API pública para substituir uma geração já preparada.

## Decisão

Usar um Access Transformer mínimo para os campos exatos `elements` e
`typeKeys`, encapsulado por `LootDataManagerBridge`. O bridge valida classe e
tipos, captura cópias imutáveis, publica uma geração completa em duas
atribuições consecutivas no safe point da server thread e verifica pelas APIs
públicas. A identidade do manager é preservada.

## Evidência do runtime

`javap -private -c` sobre o JAR mapped oficial 1.20.1 confirmou:

- `elements` é `Map<LootDataId<?>, ?>`;
- `typeKeys` é `Multimap<LootDataType<?>, ResourceLocation>`;
- `apply` usa builders imutáveis, adiciona `EMPTY_LOOT_TABLE_KEY`, valida e
  escreve `elements` nas instruções 129–132 e `typeKeys` nas 135–141;
- `getElement` e `getKeys` leem diretamente esses campos.

## Consequências

Lookups futuros observam a geração nova e recursos removidos desaparecem.
Referências externas já retidas não são atualizadas. GLMs permanecem fora da
transação. Incompatibilidade falha com `LOOT_DATA_MANAGER_LAYOUT_UNSUPPORTED`;
não há `setAccessible`, `Unsafe`, Mixin ou heurística de nomes.

Os campos confirmados nos mappings oficiais são `f_278415_` (`elements`) e
`f_278404_` (`typeKeys`). O AT amplia somente esses dois campos.
