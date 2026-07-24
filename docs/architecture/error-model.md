# Modelo de erros

## Tipos

- `PartialReloadException`: base checked para operação de provider/scan;
- `ResourceScanException`: IO, hashing, limite ou timeout com recurso quando conhecido;
- `DuplicateProviderException`: configuração/programação do registry;
- `InvalidStateTransitionException`: violação do lifecycle;
- `FunctionPreparationException`: falha tipada de infraestrutura, limite ou
  timeout;
- `ValidationIssue`: problema agregado com severity, code, category, provider,
  resource, pack, message, source location e causa;
- blockers de plano: incompatibilidade operacional, não exceções.

## Política

1. Nenhuma exception é engolida.
2. Operador recebe mensagem curta; log recebe stack e contexto.
3. Snapshot parcial não substitui último snapshot válido.
4. Falha de scan/planning/preparation leva a `FAILED_SAFE`.
5. Erro esperado no conteúdo de function produz artefato READY não aplicável;
   não é exception de infraestrutura.
6. `FAILED_SAFE` confirma ausência de mutação.
7. Unsupported/restart/unknown são resultados de plano, não tentativa seguida de exception.

## Códigos iniciais

`SCAN_IO`, `SCAN_TIMEOUT`, `SCAN_LIMIT`, `DUPLICATE_PROVIDER`, `INVALID_STATE`, `APPLY_NOT_IMPLEMENTED`, `RESTART_REQUIRED`, `UNKNOWN_RESOURCE`, `PROVIDER_PLANNED`.

## Códigos da preparação de functions

`FUNCTION_COMMAND_ERROR`, `FUNCTION_TAG_PARSE_ERROR`,
`FUNCTION_REFERENCE_MISSING`, `FUNCTION_TAG_REFERENCE_MISSING`,
`FUNCTION_TAG_CYCLE`, `FUNCTION_RECURSION_DETECTED`,
`LOAD_FUNCTION_SET_CHANGED`, `TICK_FUNCTION_SET_CHANGED`,
`RESOURCE_CHANGED_DURING_PREPARATION`, `PREPARATION_TIMEOUT`,
`PREPARATION_LIMIT`, `PREDICATES_COUPLED_TO_LOOT`.

WARNING não invalida candidato. ERROR e BLOCKER tornam
`PreparedReloadArtifact.isApplicable()` falso.
