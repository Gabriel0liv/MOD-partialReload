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
```

## Fronteiras

- `api`: categorias, provider SPI, compatibilidade e contextos experimentais;
- `resource`: descriptors, fingerprints, snapshots e leitura;
- `change`: diff puro;
- `plan`: agregação read-only e blockers;
- `function`: captura, compilação, tags, grafo e candidato passivo;
- `loot`: captura conjunta, parsers, resolver, validator, grafo, delta e
  candidato passivo;
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
7. functions aplicáveis podem ser enfileiradas para commit no END do tick;
   loot data nunca é publicada.

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
5. grafo, delta e diagnósticos de GLM/loaders externos são agregados;
6. uma segunda captura bloqueia TOCTOU;
7. a server thread publica apenas `PreparedLootData` em READY;
8. o `LootDataManager` ativo e seus elementos nunca recebem o candidato.

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
tags. A preparação é `PREPARE_ONLY`: `RecipeManager.apply`, substituição do
manager e sincronização nunca são chamados. Tags relevantes alteradas geram
`RECIPE_TAG_DEPENDENCY_CHANGED` (BLOCKER), preservando a semântica segura até
que uma fase de tags defina um contrato conjunto.

## KubeJS recipes (Fase 4B)

O provider KubeJS é uma fronteira opcional. O ambiente atual não contém o
runtime Forge 1.20.1: o único JAR local é NeoForge 2101.7.2. Scripts podem ser
fingerprintados/classificados, mas não são executados. `prepare recipes` mantém
o baseline vanilla e informa que a integração não está carregada; nenhum
runtime ativo, listener ou `RecipeManager` é tocado.
