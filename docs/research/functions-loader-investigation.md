# Investigação do loader de functions

## Fonte exata

Análise realizada sobre o source JAR mapeado oficial
`net.minecraftforge:forge:1.20.1-47.4.10_mapped_official_1.20.1-sources.jar`.
Os nomes abaixo são os nomes efetivos usados pelo projeto, não nomes inferidos de
outra versão.

## Classes e contratos confirmados

| Classe | Membros relevantes observados | Papel |
|---|---|---|
| `ServerFunctionLibrary` | `reload`, `getFunction`, `getTag`, fields volatile `functions`/`tags` | prepara e armazena a geração de functions |
| `ServerFunctionManager` | `replaceLibrary`, `postReload`, `tick`, `execute`, `get`, `getTag`, `getFunctionNames`, `getTagNames`; fields privados `library`, `ticking`, `postReload`, `context` | manager ativo e executor |
| `CommandFunction` | `fromLines`, `getEntries`, `CommandEntry` | compilação/representação de linhas |
| `CommandSourceStack` | construtor usado pela library | source sintético de compilação |
| `Commands` | `getParseException`, `getDispatcher` | validação Brigadier e árvore ativa |
| `CommandBuildContext` | `Configurable` usado ao construir `Commands` em `ReloadableServerResources` | lookup de registries na construção global de comandos |
| `ReloadableServerResources` | construtor, `loadResources`, `updateRegistryTags` | agrega listeners/managers do reload global; fora do pipeline parcial |
| `ResourceManager` | `listResources`, `listResourceStacks` por meio dos converters | visão ordenada dos datapacks ativos |
| `TagLoader` | `load`, `build`, `buildUpdatedLookups` | merge e resolução vanilla de tags |

## Descoberta e compilação

`ServerFunctionLibrary` é um `PreparableReloadListener`. Ele usa
`FileToIdConverter("functions", ".mcfunction")`, portanto
`data/<namespace>/functions/<path>.mcfunction` vira `<namespace>:<path>`.

Durante `reload`:

1. `TagLoader<CommandFunction>` lê `tags/functions`;
2. o `FileToIdConverter` obtém o recurso vencedor de cada function;
3. um `CommandSourceStack` sintético é criado com `CommandSource.NULL`, posição e
   rotação zero, nível/server/entity nulos e o `function-permission-level`;
4. cada arquivo é lido em UTF-8 e compilado por
   `CommandFunction.fromLines(id, dispatcher, source, lines)`;
5. tags e functions são preparados no executor de preparação;
6. após a `PreparationBarrier`, os maps `volatile functions` e `volatile tags`
   da library candidata são publicados no executor de aplicação.

`CommandFunction.fromLines` ignora linhas vazias e comentários `#`, recusa `/`
inicial, chama `CommandDispatcher.parse` para cada comando e falha quando sobra
input ou Brigadier produz `CommandSyntaxException`. A mensagem vanilla contém o
número da linha, mas não preserva de forma estruturada pack, comando e cursor.

O Partial Reload pode reproduzir a fase de preparação com as APIs públicas
`CommandDispatcher.parse`, `Commands.getParseException`, `CommandFunction`,
`CommandFunction.CommandEntry`, `FileToIdConverter`, `TagFile` e `TagEntry`.
Não é necessário chamar o listener ativo, acessar fields privados ou executar
qualquer entry.

## Dispatcher e contexto

`MinecraftServer.getCommands().getDispatcher()` é o dispatcher ativo. Ele já
contém comandos vanilla e comandos registrados por mods pelo hook Forge de
registro. `MinecraftServer.getFunctionCompilationLevel()` fornece o mesmo nível
usado pelo reload normal.

O construtor de `ReloadableServerResources` cria outro `Commands` usando
`CommandBuildContext.Configurable`; isso faz parte do reload global e não deve ser
reproduzido nesta fase. Para preparar functions contra a geração ativa de
comandos, o contrato correto é capturar o dispatcher ativo após o registro de
comandos.

O reload vanilla chama `dispatcher.parse` no executor de preparação. Assim,
parsing contra a árvore já construída é o precedente de thread ownership.
Captura do dispatcher, manager, sets ativos e parâmetros do servidor ocorre na
server thread; leitura, hashing, parsing e construção dos candidatos ocorrem no
worker. Publicação do artefato e transições voltam à server thread.

## Function tags

`TagLoader` usa `FileToIdConverter.json("tags/functions")` e
`listMatchingResourceStacks`, preservando todas as camadas de pack. Para cada
stack, `replace=true` limpa entradas anteriores. Forge 47.4.10 também processa o
array `remove`, e remoções ausentes são opcionais.

`TagEntry` representa element/tag e required/optional. `DependencySorter` ordena
referências entre tags, mas evita arestas cíclicas e reporta problemas apenas em
log. Para um artefato auditável, a preparação precisa manter a mesma semântica de
merge e produzir issues estruturadas para JSON inválido, referência obrigatória
ausente e ciclo.

`minecraft:tick` e `minecraft:load` são somente tags de functions com IDs `tick`
e `load`. Não são categorias públicas.

## Tick, load e manager ativo

`ServerFunctionManager` contém:

- `private ServerFunctionLibrary library`;
- `private List<CommandFunction> ticking`;
- `private boolean postReload`;
- `@Nullable ExecutionContext context`.

`replaceLibrary(candidate)` troca `library` e chama `postReload(candidate)`.
`postReload` copia a tag `minecraft:tick` para `ticking` e define
`postReload=true`. No tick seguinte, `tick()` executa uma vez a tag
`minecraft:load` e depois executa `ticking` em todos os ticks.

Logo, preparar uma library não executa load; chamar `replaceLibrary` já altera
tick e agenda execução de load para o próximo tick. Esse método permanece fora
da fase.

Políticas futuras de load:

- `DO_NOT_RUN` (padrão recomendado);
- `RUN_NEWLY_ADDED`;
- `RUN_CHANGED_AND_ADDED`;
- `RUN_ALL`.

O MineDev usa load functions para setup de scoreboards e outros estados, então
execução implícita não é aceitável.

## Resolução durante execução

O comando `function` usa `FunctionArgument`. O argumento é parseado como ID/tag,
mas sua resolução efetiva chama `source.getServer().getFunctions()` quando o
comando é executado. O mesmo vale para `schedule function`.

`FunctionCallback` e `FunctionTagCallback` persistem apenas o ID e consultam o
`ServerFunctionManager` quando o timer dispara. Schedules existentes, portanto,
não armazenam a implementação compilada.

Já uma function em execução é expandida para `QueuedCommand` contendo
`CommandFunction.Entry`; chamadas aninhadas adicionam entries da implementação
resolvida à fila. Uma futura troca não deve interromper essa fila.

Em Minecraft 1.20.1, `return` aceita apenas um inteiro. A sintaxe
`return run function ...` não existe nesta versão e deve ser rejeitada pelo
Brigadier, não reconhecida como dependência válida.

## Dependências

O `ParseResults` do Brigadier conserva `ParsedArgument` com range do argumento
`FunctionArgument.Result`, inclusive em filhos criados por
`execute ... run`. A API não expõe diretamente o ID interno do resultado
anônimo. É possível extrair conservadoramente o token já validado pelo Brigadier
usando o range do argumento, sem regex sobre a linha inteira.

Relações suportadas:

- chamada direta `function`;
- chamada por tag `function #...`;
- `schedule function`;
- chamadas sob `execute ... run`;
- membership em `minecraft:tick` e `minecraft:load`.

Ciclos entre functions são diagnóstico, não erro automático, porque execução é
limitada por `maxCommandChainLength`.

## TOCTOU

Functions usam o recurso vencedor; tags fazem merge de toda a stack. Um snapshot
correto para preparação deve fingerprintar bytes e pack ID de cada function e a
sequência completa das camadas de cada tag. Capturar novamente essa visão antes
de publicar permite emitir `RESOURCE_CHANGED_DURING_PREPARATION`.

## Fronteira para commit futuro

O ponto público de troca é `ServerFunctionManager.replaceLibrary`. Ele substitui
a field privada `library`, reconstrói `ticking` e arma `postReload`. Não é
necessário Mixin/AT para chamar o método, mas um commit transacional precisaria:

1. barreira na server thread sem `ExecutionContext` ativo;
2. snapshot da library/tick/load anteriores para rollback — não exposto por API;
3. política explícita de load;
4. decisão sobre schedules por ID;
5. verificação pós-troca e rollback seguro;
6. testes de fila em execução e ticks concorrentes.

Como a library antiga não possui getter público, rollback por referência exigiria
nova evidência e possivelmente acesso versionado. Nada disso é implementado na
fase 2.
