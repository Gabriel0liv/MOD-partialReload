# Modelo de threads

## Ownership

- command registration e execução inicial: server thread;
- transições, publicação de snapshots/diffs/planos/artefatos e mensagens de conclusão: server thread;
- enumeração/leitura/hash de recursos: executor de background fornecido pelo `MinecraftServer`;
- parsing Brigadier de functions: mesmo executor de preparação; esta é a
  divisão usada por `ServerFunctionLibrary.reload` 1.20.1;
- leitura, hashing, Gson/Forge parsing, grafo, delta e validação de loot:
  executor de background; esta é a divisão de `LootDataManager.reload` 1.20.1.
  Serializers modded sem esse contrato continuam não suportados;
- captura do dispatcher, permission level e sets ativos de tick/load: server
  thread antes de iniciar o worker;
- modelos imutáveis podem atravessar a fronteira.

O serviço não cria pool próprio e não guarda `MinecraftServer` permanentemente. Cada scan recebe `ResourceManager`, executor e callback no contexto da operação.

## Timeout

Timeouts de scan, functions e loot são cooperativos: os loaders verificam deadline
antes/durante enumeração, leitura e compilação. Não interrompem uma chamada de
IO bloqueada no meio. Resultado após timeout não é publicado.

## Exclusão

A máquina impede duas preparações, inclusive de providers diferentes, e também
scan/plan concorrente com prepare. A implementação usa sincronização curta apenas para estado/modelos;
nunca mantém lock durante IO ou compilação. Completion sempre sai de PREPARING
para VALIDATING/READY ou FAILED_SAFE.

## Commit de functions

O comando apenas cria a transação. `ServerTickEvent.END`, prioridade LOWEST,
executa quiesce, troca da library, supressão de load, atualização de tick e
verificação na server thread, sem espera ativa. O bridge recusa a operação se
`ExecutionContext` estiver ativo. A geração anterior é retida em memória para
rollback no mesmo safe point.

Sincronização e commit de outras categorias permanecem futuros.

Para loot, commit futuro também precisa de barreira contra criação/uso de
`LootContext`, consultas de containers/entidades e interação atômica com o
manager GLM separado.

Recipes seguem o mesmo ownership: enumeração, leitura de bytes, condições,
desserialização e índices são executados no executor de background; publicação
não existe nesta fase. O `ResourceManager` é apenas a visão fornecida pelo
servidor, e o artefato é entregue ao executor proprietário para transição de
estado. Os limites de recipes e bytes são cooperativos e o resultado não é
publicado após timeout.

O scanner KubeJS, quando usado, só lê arquivos e calcula SHA-256 no executor de
background. Classificação é conservadora e não interpreta JavaScript. Execução
Rhino/event handlers permanece desabilitada até um runtime versionado fornecer
staging seguro.

Tags capturam `RegistryAccess` na server thread, mas leitura, parsing, merge de
packs, grafo e validação rodam no executor de background. Nenhuma chamada de
`bindTags` ocorre em qualquer thread. O artefato é entregue ao executor owner
somente para transição de estado.
## Tags + recipes

A leitura de bytes, parsing JSON e construção de `PreparedTagsResolutionView`
ocorrem no executor de background. A continuação no executor owner apenas
instala ou descarta o artefato imutável e atualiza o estado; nenhum binding,
RecipeManager ou holder é acessado para publicação.
