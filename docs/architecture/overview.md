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
```

## Fronteiras

- `api`: categorias, provider SPI, compatibilidade e contextos experimentais;
- `resource`: descriptors, fingerprints, snapshots e leitura;
- `change`: diff puro;
- `plan`: agregação read-only e blockers;
- `function`: captura, compilação, tags, grafo e candidato passivo;
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
7. nenhuma API de commit é exposta.

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
