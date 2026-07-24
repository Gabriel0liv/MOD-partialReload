# Pipeline de reload Forge/Minecraft 1.20.1

## Fonte exata

Fonte mapeada oficial do artefato `net.minecraftforge:forge:1.20.1-47.4.10`, obtida pelo ForgeGradle local. Referência upstream: [MinecraftForge 1.20.x](https://github.com/MinecraftForge/MinecraftForge/tree/1.20.x).

## Managers e ordem

`ReloadableServerResources` cria um conjunto coeso contendo:

1. `TagManager`;
2. `LootDataManager`;
3. `RecipeManager`;
4. `ServerFunctionLibrary`;
5. `ServerAdvancementManager`;
6. listeners adicionais de `AddReloadListenerEvent`.

O construtor compartilha `TagManager` com `ConditionContext`, e `ServerAdvancementManager` depende do novo `LootDataManager`. Essa topologia é evidência de que chamar um listener arbitrário isoladamente não preserva o contrato global.

## Preparação e aplicação

`PreparableReloadListener.reload` recebe barrier, `ResourceManager`, profilers e dois executors. `SimplePreparableReloadListener` executa `prepare` no executor de preparação, aguarda a barrier que coordena todos os listeners, e executa `apply` no executor de reload. `SimpleReloadInstance` permite preparação concorrente, mas serializa a conclusão/aplicação segundo a cadeia de listeners.

`ServerFunctionLibrary` prepara tags e compila `.mcfunction` assíncronamente. `LootDataManager` parseia tipos de loot em tarefas, aplica o mapa agregado e só então valida referências. Recipes usam o `ConditionContext` ligado às tags.

## Commit global real

`MinecraftServer.reloadResources` abre os packs, cria um `MultiPackResourceManager`, constrói um novo `ReloadableServerResources` e, na server thread:

- fecha e troca o conjunto antigo;
- atualiza packs e configuração do mundo;
- vincula tags aos registries e dispara `TagsUpdatedEvent`;
- salva jogadores;
- recarrega advancements de jogadores;
- dispara `OnDatapackSyncEvent`;
- envia tags e recipes;
- substitui a library do `ServerFunctionManager`;
- notifica `StructureTemplateManager`;
- reenvia comandos/permissões.

Por isso o método e o comando `/reload` são explicitamente proibidos para partial reload.

## Sincronização

- login e reload enviam `ClientboundUpdateTagsPacket`;
- recipes usam `ClientboundUpdateRecipesPacket` e atualização do recipe book;
- advancements usam `ClientboundUpdateAdvancementsPacket` por `PlayerAdvancements`;
- Forge filtra/divide esses packets para compatibilidade de conexão;
- listeners adicionais podem depender de `OnDatapackSyncEvent` e `TagsUpdatedEvent`.

## Lifecycle e comandos

Forge expõe `RegisterCommandsEvent` para registro no dispatcher do servidor. Dedicated server usa `Commands.CommandSelection.DEDICATED`. A fase 1 deve registrar comandos por esse evento e manter toda mutação do estado do serviço serializada pela server thread.

## Conclusão

O único contrato seguro na fase 1 é leitura da visão `ResourceManager`. Preparação/commit parciais exigirão managers candidatos, dependências explícitas, quiesce, sincronização e verificação especificados por provider; não serão simulados.
