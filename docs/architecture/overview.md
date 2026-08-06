# Visão de arquitetura

```text
commands
   |
PartialReloadService ---- PartialReloadStateMachine
   |          |
   |          +---- activeReference / latestScan / changes / plan / prepared
   |
ProviderRegistry
   +---- VanillaDatapackProvider -> ResourceScanner
   |
   +---- VanillaFunctionsProvider
            |---- FunctionResourceLoader
            |---- FunctionCompiler (dispatcher real)
            |---- FunctionTagResolver
            +---- FunctionDependencyGraph -> PreparedFunctions
   |
   +---- VanillaLootDataProvider (predicates + item_modifiers + loot)
            |---- LootResourceLoader (winners + stacks + SHA-256)
            |---- parsers Forge/vanilla + candidate LootDataResolver
            |---- ValidationContext / LootDataType validators
            +---- LootDependencyGraph + LootDataDelta -> PreparedLootData
   |
   +---- ForgeGlobalLootModifierProvider
            |---- stack forge:loot_modifiers (replace + ordem)
            |---- IGlobalLootModifier.DIRECT_CODEC
            +---- PreparedGlobalLootModifiers -> LootModifierManagerBridge
```

## Fronteiras

- `api`: categorias, provider SPI, compatibilidade e contextos experimentais;
- `resource`: descriptors, fingerprints, snapshots e leitura;
- `change`: diff puro;
- `plan`: agregação read-only e blockers;
- `function`: captura, compilação, tags, grafo e candidato passivo;
- `loot`: captura conjunta, parsers, resolver, validator, grafo, delta,
  geração ativa imutável e bridge transacional do `LootDataManager`;
- `glm`: stack ordenada Forge, codecs, geração imutável, bridge do
  `LootModifierManager` e transação conjunta com loot data;
- `validation`: issues/reports estruturados;
- `core`: registry, estado e orquestração;
- `command`: adaptação Brigadier;
- `config`: validação ForgeConfigSpec.

Categoria, provider, recurso e transação/plano não são intercambiáveis. Os
boundaries Minecraft da fase 2 são o `ResourceManager`, o dispatcher ativo
capturado e os IDs ativos de tick/load; nenhum manager é retido ou substituído.

## Fluxo fase 1

1. comando obtém `ResourceManager` atual sem retê-lo;
2. serviço entra SCANNING e delega scan no executor de background;
3. resultado completo retorna à server thread;
4. primeiro resultado estabelece `activeReference`; todo resultado atualiza `latestScan`;
5. diff é sempre `activeReference` versus `latestScan`;
6. planning agrega contribuições conservadoras e entra READY;
7. functions e loot data aplicáveis podem ser enfileirados para commit no END
   do tick conforme suas specs transacionais.

## Extensão futura

Providers futuros poderão adicionar contratos `PreparedReload`, quiesce, commit, sync, verify e rollback apenas quando as respectivas specs existirem. Esses métodos não pertencem à SPI inicial para não prometer capacidade inexistente.

## Fluxo de preparação de functions

1. command captura dispatcher, permission level e tick/load ativos na server
   thread;
2. serviço entra PREPARING e executa no worker uma captura consistente de todas
   as functions e stacks de tags;
3. cada linha é validada pelo Brigadier e encapsulada sem API de execução;
4. tags, tick/load, dependências, ciclos e deltas são calculados;
5. uma segunda captura compara fingerprints e bloqueia TOCTOU;
6. na server thread o serviço entra VALIDATING e publica somente o artefato
   imutável em READY;
7. o `ServerFunctionManager` ativo nunca recebe o candidato.

## Fluxo de preparação de loot data

1. a categoria solicitada é preservada e o provider expande internamente para
   predicates, item modifiers e loot;
2. o worker captura vencedores e stacks completas, bytes, pack e fingerprints;
3. parsers reais constroem maps temporários dos três tipos;
4. um `LootDataResolver` candidato e `ValidationContext` validam o grafo inteiro;
5. grafo, delta e diagnóstico de acoplamento com o provider GLM são agregados;
6. uma segunda captura bloqueia TOCTOU;
7. a server thread publica `PreparedLootData` em READY sem mutar o manager;
8. somente `apply prepared` cria a transação 4G e publica a geração completa no
   safe point.

## Commit transacional de Global Loot Modifiers

`LootModifierManagerBridge` preserva a identidade do manager Forge e publica o
mapa ordenado completo. Mudança GLM isolada usa transação própria; mudança da
mesma captura em loot data exige `PreparedLootAndGlobalModifiers`, publicação
loot→GLM e rollback GLM→loot. Jogadores conectados são permitidos, sem client
sync e sem reescrever itens ou loot já produzidos.

Esse caminho está promovido como `COMMIT_SUPPORTED`. O gate final aprovou
84/84 GameTests, as acceptances dedicadas GLM, loot+GLM e 4G, e o runner
consolidado com 11 suítes. Ownership dos harnesses é por identidade e geração,
não pelo número do PID isolado.

## Commit transacional de loot data

`LootDataManagerBridge` captura cópias imutáveis de `elements` e `typeKeys`,
reconstrói exclusivamente do `PreparedLootData` o bundle completo e o publica
no `ServerTickEvent.END`. A identidade do manager e do `LootModifierManager`
permanece estável. Verificação por lookup público e chaves precede `SUCCESS`;
falha após a primeira atribuição restaura os dois campos ou entra em
`DEGRADED`. Jogadores conectados são permitidos e não recebem sincronização.

O baseline do delta é `activeReference`, estabelecido pela primeira captura
read-only conhecida enquanto nenhum commit existe. Sem baseline anterior, a
captura corrente é tratada como referência ativa e o delta inicial é zero; ela
não é promovida por uma preparação.

## Commit transacional de functions

`apply prepared` valida a compatibilidade, cria uma transação e entra em
QUIESCING. O listener `ServerTickEvent.END` em prioridade LOWEST confirma que
não há `ExecutionContext`, captura a geração anterior, constrói maps
independentes de `ServerFunctionLibrary`, chama `replaceLibrary`, desativa
`postReload` imediatamente e verifica library/tick/load. O sucesso promove
somente os descritores de functions e consome o artefato. Falhas após a troca
publicam a geração retida e restauram o baseline; uma falha nessa restauração
entra em DEGRADED. A política de load implementada é sempre `DO_NOT_RUN`.

## Preparação de recipes (Fase 4A)

`VanillaRecipesProvider` enumera a visão vencedora de `recipes/**/*.json`,
avalia condições Forge, desserializa com `RecipeManager.fromJson` e produz
`PreparedRecipes` imutável, com índices por ID/tipo, hashes e grafo de itens e
tags. Isoladamente, recipes permanecem `PREPARE_ONLY`. No artefato conjunto da
Fase 4E, tags suportadas são vinculadas com `Registry.bindTags` e a coleção
completa é publicada por `RecipeManager.replaceRecipes` no safe point, sem
players conectados; Ingredient é invalidado, o evento de tags é emitido e uma
geração anterior fica retida para rollback. Sincronização de clientes continua
fora do escopo.

## KubeJS recipes (Fase 4B)

O provider KubeJS é uma fronteira opcional. O ambiente atual não contém o
runtime Forge 1.20.1: o único JAR local é NeoForge 2101.7.2. Scripts podem ser
fingerprintados/classificados, mas não são executados. `prepare recipes` mantém
o baseline vanilla e informa que a integração não está carregada; nenhum
runtime ativo, listener ou `RecipeManager` é tocado.

O estado oficial da integração KubeJS é `KUBEJS_RECIPE_PREPARATION_BLOCKED`:
não há runtime Forge 1.20.1 nem API de staging comprovada.

## Preparação de tags (Fase 4C)

`VanillaTagsProvider` usa `listResourceStacks("tags", ...)`, exclui
`tags/functions`, reconstrói cada registry em mapas independentes, aplica
`replace`, `values` e operações `remove` representadas, resolve referências,
detecta ciclos, valida elementos via `RegistryAccess` e produz `PreparedTags`.
Nenhum holder é bindado e `apply` permanece recusado.

## Preparação conjunta tags + recipes

`PreparedTagsAndRecipes` agrega dois candidatos imutáveis derivados do mesmo
snapshot. `PreparedTagsResolutionView` resolve IDs e membros logicamente,
sem holders ativos. Tags são preparadas antes de recipes; serializers e
conditions que exigirem contexto ativo falham fechado. O artefato não pode
ser publicado ou sincronizado.
