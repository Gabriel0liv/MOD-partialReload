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

## Pesquisa da Fase 4I — advancements

O JAR mapped oficial e as sources de Forge 47.4.10 confirmam que
`ServerAdvancementManager` mantém `AdvancementList advancements`,
`LootDataManager lootData` e o contexto Forge. O `apply` desserializa via
`Advancement.Builder.fromJson`, constrói uma lista completa, resolve roots e
executa `TreeNodePosition` antes de substituir o campo.

`AdvancementList` mantém map por ID, roots e tasks. As APIs públicas permitem
listar roots, listar todos e resolver ID, mas não substituir a lista do manager.

`PlayerAdvancements.reload` chama `stopListening`, limpa progresso/visibilidade,
define `isFirstPacket=true`, limpa `lastSelectedTab`, relê o JSON do jogador,
chama `checkForAutomaticTriggers` e registra listeners. O check automático
concede rewards a advancements sem critérios; portanto candidatos automáticos
com reward são inseguros para uma transação compensável e devem falhar antes da
mutação. `flushDirty` constrói `ClientboundUpdateAdvancementsPacket` e
`setSelectedTab` envia `ClientboundSelectAdvancementsTabPacket`.

`AdvancementProgress.update` preserva `CriterionProgress` de nomes ainda
existentes e remove/adiciona critérios conforme a definição nova; as datas são
serializadas no arquivo vanilla. Essa é a base do rebind por ID sem parser de
progresso próprio.
