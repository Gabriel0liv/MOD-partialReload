# Modelo de threads

## Ownership

- command registration e execução inicial: server thread;
- transições, publicação de snapshots/diffs/planos e mensagens de conclusão: server thread;
- enumeração/leitura/hash de recursos: executor de background fornecido pelo `MinecraftServer`;
- modelos imutáveis podem atravessar a fronteira.

O serviço não cria pool próprio e não guarda `MinecraftServer` permanentemente. Cada scan recebe `ResourceManager`, executor e callback no contexto da operação.

## Timeout

O timeout da fase 1 é cooperativo: o scanner verifica deadline antes e durante a enumeração/leitura. Ele não interrompe uma chamada de IO bloqueada no meio. Resultado após falha não deve ser publicado.

## Exclusão

A máquina de estados impede duas operações simultâneas. A implementação usa sincronização curta apenas para state/snapshots; nunca mantém lock durante IO.

## Futuro

Prepare continuará em background; quiesce/commit/sync e troca de referências ocorrerão na server thread. Essa divisão precisará de operation ID/cancellation antes de qualquer commit.
