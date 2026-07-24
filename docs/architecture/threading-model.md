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

## Futuro

Quiesce/commit/sync e eventual troca de referências ocorrerão na server thread.
Essa divisão ainda precisa de operation ID/cancellation e barreira contra
`ExecutionContext` ativo antes de qualquer commit.

Para loot, commit futuro também precisa de barreira contra criação/uso de
`LootContext`, consultas de containers/entidades e interação atômica com o
manager GLM separado.
