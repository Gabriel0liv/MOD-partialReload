# Modelo de erros

## Tipos

- `PartialReloadException`: base checked para operação de provider/scan;
- `ResourceScanException`: IO, hashing, limite ou timeout com recurso quando conhecido;
- `DuplicateProviderException`: configuração/programação do registry;
- `InvalidStateTransitionException`: violação do lifecycle;
- `ValidationIssue`: problema agregado com severity/code/message/resource;
- blockers de plano: incompatibilidade operacional, não exceções.

## Política

1. Nenhuma exception é engolida.
2. Operador recebe mensagem curta; log recebe stack e contexto.
3. Snapshot parcial não substitui último snapshot válido.
4. Falha de scan/planning leva a `FAILED_SAFE`.
5. `FAILED_SAFE` confirma ausência de mutação.
6. Unsupported/restart/unknown são resultados de plano, não tentativa seguida de exception.

## Códigos iniciais

`SCAN_IO`, `SCAN_TIMEOUT`, `SCAN_LIMIT`, `DUPLICATE_PROVIDER`, `INVALID_STATE`, `APPLY_NOT_IMPLEMENTED`, `RESTART_REQUIRED`, `UNKNOWN_RESOURCE`, `PROVIDER_PLANNED`.
