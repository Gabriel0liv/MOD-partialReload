# Investigação do LootDataManager — Minecraft 1.20.1 / Forge 47.4.10

Data: 2026-07-24  
Mappings: oficiais 1.20.1  
Fonte principal: `forge-1.20.1-47.4.10_mapped_official_1.20.1-sources.jar`

## Escopo e classes confirmadas

Foram lidas as implementações mapeadas de `LootDataManager`,
`LootDataType`, `LootDataId`, `LootDataResolver`, `LootTable`,
`ValidationContext`, `LootContext`, `LootContextUser`,
`LootItemCondition`, `LootItemFunction`, `LootContextParamSet`,
`LootContextParamSets`, `ReloadableServerResources`,
`SimpleJsonResourceReloadListener`, `ResourceManager` e os adapters em
`Deserializers`. Também foram inspecionadas as referências
`ConditionReference`, `FunctionReference`, `LootTableReference` e os hooks de
loot do Forge.

## Descoberta e preparação normal

`LootDataManager.reload` cria um map temporário por `LootDataType` e agenda
três parses no preparation executor:

| Tipo | Diretório | Parser |
|---|---|---|
| `LootDataType.PREDICATE` | `predicates` | Gson de conditions |
| `LootDataType.MODIFIER` | `item_modifiers` | Gson de functions |
| `LootDataType.TABLE` | `loot_tables` | Gson de loot tables com deserializer Forge |

Cada tarefa chama `SimpleJsonResourceReloadListener.scanDirectory`. Esse
mecanismo usa a visão lógica vencedora do `ResourceManager`; o ID é derivado
removendo o prefixo do diretório e o sufixo `.json`. O reload normal não mantém
uma cópia incremental do manager anterior.

Predicates e item modifiers aceitam um objeto único ou um array. Arrays viram,
respectivamente, `LootDataManager.createComposite` e
`LootDataManager.createComposite`. Loot tables são desserializadas pelo
`ForgeHooks.getLootTableDeserializer`; o hook chama o Gson real, atribui o ID e
congela a tabela. Para tabelas built-in (`custom == false`), também dispara o
`LootTableLoadEvent` antes do `freeze`. Portanto, reproduzir fielmente o parse
Forge pode executar callbacks de preparação registrados por mods, mas não gera
loot nem executa GLM.

Após a barreira, `LootDataManager.apply`:

1. rejeita redefinição de `minecraft:empty`;
2. agrega os três maps num `Map<LootDataId<?>, ?>` imutável;
3. adiciona `LootTable.EMPTY`;
4. cria um `LootDataResolver` sobre esse candidato;
5. cria `ValidationContext(LootContextParamSets.ALL_PARAMS, resolver)`;
6. valida cada elemento pelo respectivo `LootDataType`;
7. só então substitui seus campos privados `elements` e `typeKeys`.

Esta fase reproduz os passos 1–6 em estruturas próprias e omite o passo 7.
Chamar `LootDataManager.reload` não é aceitável, pois o future culmina em
`apply` sobre a instância alvo e os erros de deserialização são apenas logados.

## Serializers e registries

`Deserializers.createConditionSerializer`, `createFunctionSerializer` e
`createLootTableSerializer` registram adapters para os tipos hierárquicos de
conditions, functions, entries, number providers e score providers. Os adapters
consultam os registries de runtime usados pelo servidor, incluindo tipos Forge e
tipos de mods já registrados. A tabela usa ainda o deserializer Forge, necessário
para ID, nomes de pools, freeze e `LootTableLoadEvent`.

Isso permite reconhecer serializers modded registrados, mas não torna seguro
qualquer loader externo nem código de callback arbitrário. O parse Forge de
tabelas deve ocorrer em contexto controlado do servidor; IO, hashing, JSON
preliminar, grafo e deltas podem ocorrer no worker.

`LootContextParamSets` contém, nesta versão, `EMPTY`, `CHEST`, `COMMAND`,
`SELECTOR`, `FISHING`, `ENTITY`, `ARCHAEOLOGY`, `GIFT`, `BARTER`,
`ADVANCEMENT_REWARD`, `ADVANCEMENT_ENTITY`, `ADVANCEMENT_LOCATION`,
`GENERIC`, `ALL_PARAMS` e `BLOCK`. `LootTable.Serializer` resolve o campo
`type` por esse catálogo. Não há contrato público vanilla para registrar param
sets arbitrários; portanto a fase não promete param sets modded.

## Resolução e validação

`ValidationContext` carrega:

- o `LootDataResolver` candidato;
- o param set atual;
- o caminho textual de validação;
- o conjunto imutável de `LootDataId` já visitados;
- um multimap de problemas.

Predicates e modifiers são validados em `ALL_PARAMS`. Cada loot table muda o
contexto para `table.getParamSet()` antes de validar. `LootContextUser` delega a
compatibilidade dos parâmetros ao param set.

`ConditionReference`, `FunctionReference` e `LootTableReference` consultam o
resolver pelo par tipo/ID. Ausência produz problema de validação. Um ID já
visitado produz diagnóstico recursivo e interrompe a descida daquela aresta.
Recursão não deve ser redefinida por uma heurística própria; o validator real é
autoritativo. O grafo adicional serve para explicação, dependentes, impacto e
caminhos.

`random_sequence` é um `ResourceLocation` opcional armazenado na tabela. A
desserialização valida sua sintaxe, mas não há um recurso externo correspondente
a resolver. Referências de tabela, predicate e modifier são resolvidas
dinamicamente pelo `LootDataResolver` contido no `LootContext`.

## Stack de packs e deltas

O candidato usa apenas os vencedores para reproduzir o manager, mas a captura
também deve observar `ResourceManager.getResourceStack` para registrar pack
vencedor e detectar quando o desaparecimento de um override revela uma versão
inferior. O estado `RESTORED_FROM_LOWER_PACK` exige:

- o mesmo ID lógico no snapshot de referência;
- mudança do pack vencedor;
- ausência do antigo pack vencedor na stack capturada;
- conteúdo vencedor ainda existente.

O último scan não é automaticamente o estado ativo. O baseline de delta deve ser
o snapshot de referência associado à geração ativa conhecida, quando disponível.

## Threads e não mutação

O reload vanilla faz leitura/parsing no preparation executor e `apply` no apply
executor. Isso não é uma garantia universal de thread safety para serializers de
mods. Contrato adotado:

- server thread: capturar manager ativo, registries/contexto, resource manager,
  baseline e identidade da geração; iniciar/finalizar a operação;
- worker: ler bytes, hash, parse JSON preliminar, construir snapshot, grafo,
  deltas e coleções imutáveis;
- contexto controlado/server thread: desserialização real Forge/modded e
  validação quando callbacks ou registries não têm contrato de thread safety;
- server thread: recapturar fingerprints, validar TOCTOU e publicar somente o
  artefato passivo.

O artefato não expõe métodos que avaliem predicate, apliquem modifier ou gerem
loot.

## Referências ativas e commit futuro

`ReloadableServerResources` mantém um campo privado final
`LootDataManager lootData` e o expõe por `getLootData()`. O servidor e a criação
de `LootContext` obtêm o resolver a partir dos recursos ativos. O próprio manager
mantém `elements` e `typeKeys` privados e os substitui no `apply`.

Um commit futuro não possui API pública de swap. Ele precisa provar se trocará
os maps do manager, o campo de `ReloadableServerResources` ou a instância inteira;
mapear mods que retêm manager/elementos; definir barreira para loot em execução;
tratar GLM separadamente; e oferecer rollback para a referência anterior.
Nenhuma dessas ações pertence à Spec 010.

## MineDev e loaders externos

O checkout público read-only de `Gabriel0liv/MineDev` foi inspecionado no commit
`5b3405c82b17946e2943b8604d2c39b50fa6ae52`.

| Padrão | Evidência | Classificação nesta fase |
|---|---|---|
| `DrathosOrigins_2.0.0/data/*/predicates/**` | JSON de conditions vanilla/modded sob diretório do manager | `CONSUMED_BY_VANILLA_LOOT_DATA_MANAGER` |
| `DrathosOrigins_2.0.0/data/*/item_modifiers/**` | JSON de functions sob diretório do manager | `CONSUMED_BY_VANILLA_LOOT_DATA_MANAGER` |
| loot tables usuais de Drathos | formato/IDs do manager | `CONSUMED_BY_VANILLA_LOOT_DATA_MANAGER` |
| `data/silentgear/loot_tables/inject/**` | tabelas parseáveis pelo manager, mas descobertas por convenção do `SgLoot` e ligadas via `LootTableLoadEvent` | `FORMAT_COMPATIBLE_BUT_EXTERNAL` |
| `data/starcatcher/loot_tables/**` | formato vanilla consumido pelo manager; consumidor Starcatcher não foi provado | `CONSUMED_BY_VANILLA_LOOT_DATA_MANAGER`, com integração externa não afirmada |

O branch 1.20.x do Silent Gear foi consultado no commit
`8af569e...`. `SgLoot.Injector` mapeia uma tabela alvo para
`silentgear:inject/<namespace>/<path>` e injeta uma referência durante
`LootTableLoadEvent`. A Spec 010 reporta esse padrão como externo e não declara
suporte completo ao Silent Gear.

