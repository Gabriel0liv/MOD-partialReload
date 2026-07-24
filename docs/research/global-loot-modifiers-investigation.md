# Investigação de Forge Global Loot Modifiers

Data: 2026-07-24  
Versão: Forge 47.4.10 para Minecraft 1.20.1

## Loader real

`ForgeInternalHandler.onResourceReload(AddReloadListenerEvent)` cria uma
instância de `LootModifierManager`, guarda-a num campo estático privado e a
registra como listener separado. `LootModifierManager` estende
`SimpleJsonResourceReloadListener` com diretório `loot_modifiers`.

Ele primeiro combina, na ordem dos packs, todas as versões de
`forge:loot_modifiers/global_loot_modifiers.json`. `replace` limpa a lista;
`entries` removem/reinserem IDs para preservar a ordem final. Apenas IDs
habilitados por essa lista são decodificados.

Cada modifier é lido por `IGlobalLootModifier.DIRECT_CODEC`/`JsonOps`, que
resolve serializers em `ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS`.
Conditions embutidas usam o Gson de functions exposto como
`LootModifierManager.GSON_INSTANCE`.

## Relação com LootDataManager

GLM não faz parte de `LootDataType.values()`, não é armazenado no
`LootDataManager`, não participa do resolver candidato dos três tipos e não é
validado por seu `ValidationContext`. Em runtime, `ForgeHooks.modifyLoot` consulta
o manager Forge ativo e aplica a cadeia após a geração da tabela.

Um candidato GLM passivo parece tecnicamente possível, mas requer contrato
próprio para:

- merge ordenado do arquivo global;
- codecs e serializers registrados;
- validação das conditions;
- encapsulamento do conjunto ordenado;
- referência ativa estática do Forge;
- atomicidade conjunta ou ordenada com loot tables;
- callbacks/addons e rollback.

Essa prova não existe nesta fase. Executar GLM para “testar” o candidato também
é proibido em produção.

## Resultado

`GLM_SEPARATE_PROVIDER`.

A preparação conjunta vanilla cobre predicates, item modifiers e loot tables.
Recursos `loot_modifiers` são detectados e reportados com `GLM_NOT_INCLUDED`;
permanecem `PLANNED`. A ausência de GLM não deve ser ocultada nem interpretada
como suporte.

