# Modelo de erros

## Tipos

- `PartialReloadException`: base checked para operação de provider/scan;
- `ResourceScanException`: IO, hashing, limite ou timeout com recurso quando conhecido;
- `DuplicateProviderException`: configuração/programação do registry;
- `InvalidStateTransitionException`: violação do lifecycle;
- `FunctionPreparationException`: falha tipada de infraestrutura, limite ou
  timeout;
- `LootPreparationException`: IO, limite ou timeout da reconstrução conjunta;
- `ValidationIssue`: problema agregado com severity, code, category, provider,
  resource, pack, message, source location, causa e detalhes de tipo/path/serializer;
- blockers de plano: incompatibilidade operacional, não exceções.

## Política

1. Nenhuma exception é engolida.
2. Operador recebe mensagem curta; log recebe stack e contexto.
3. Snapshot parcial não substitui último snapshot válido.
4. Falha de scan/planning/preparation leva a `FAILED_SAFE`.
5. Erro esperado no conteúdo de function produz artefato READY não aplicável;
   não é exception de infraestrutura.
6. A mesma política vale para conteúdo de loot; erro preserva issues e não
   publica candidato parcial.
7. `FAILED_SAFE` confirma ausência de mutação.
8. Unsupported/restart/unknown são resultados de plano, não tentativa seguida de exception.

## Códigos iniciais

`SCAN_IO`, `SCAN_TIMEOUT`, `SCAN_LIMIT`, `DUPLICATE_PROVIDER`, `INVALID_STATE`, `APPLY_NOT_IMPLEMENTED`, `RESTART_REQUIRED`, `UNKNOWN_RESOURCE`, `PROVIDER_PLANNED`.

## Códigos da preparação de functions

`FUNCTION_COMMAND_ERROR`, `FUNCTION_TAG_PARSE_ERROR`,
`FUNCTION_REFERENCE_MISSING`, `FUNCTION_TAG_REFERENCE_MISSING`,
`FUNCTION_TAG_CYCLE`, `FUNCTION_RECURSION_DETECTED`,
`LOAD_FUNCTION_SET_CHANGED`, `TICK_FUNCTION_SET_CHANGED`,
`RESOURCE_CHANGED_DURING_PREPARATION`, `PREPARATION_TIMEOUT`,
`PREPARATION_LIMIT`, `PREDICATES_COUPLED_TO_LOOT`.

## Códigos da preparação de loot

`LOOT_CATEGORY_SCOPE_EXPANDED`, `LOOT_JSON_SYNTAX_ERROR`,
`LOOT_DESERIALIZATION_ERROR`, `LOOT_UNKNOWN_ENTRY_TYPE`,
`LOOT_UNKNOWN_CONDITION_TYPE`, `LOOT_UNKNOWN_FUNCTION_TYPE`,
`LOOT_REGISTRY_REFERENCE_MISSING`, `LOOT_TABLE_REFERENCE_MISSING`,
`PREDICATE_REFERENCE_MISSING`, `ITEM_MODIFIER_REFERENCE_MISSING`,
`LOOT_RECURSIVE_REFERENCE`, `LOOT_CONTEXT_INCOMPATIBLE`,
`LOOT_RANDOM_SEQUENCE_INVALID`, `LOOT_VALIDATION_ERROR`,
`LOOT_EXTERNAL_PROVIDER_UNSUPPORTED`,
`LOOT_RESOURCE_CHANGED_DURING_PREPARATION`, `LOOT_PREPARATION_TIMEOUT`,
`LOOT_LIMIT_EXCEEDED`, `GLM_NOT_INCLUDED`, `PREPARATION_ALREADY_RUNNING`.

## Códigos do commit de loot data

`LOOT_COMMIT_ARTIFACT_INVALID`, `LOOT_COMMIT_SNAPSHOT_STALE`,
`LOOT_COMMIT_MANAGER_CHANGED`, `LOOT_DATA_MANAGER_LAYOUT_UNSUPPORTED`,
`LOOT_COMMIT_CANDIDATE_INCOMPLETE`, `LOOT_COMMIT_TRANSACTION_RUNNING`,
`LOOT_COMMIT_WRONG_THREAD`, `LOOT_COMMIT_VERIFICATION_FAILED`,
`LOOT_DATA_COMMIT_FAILED_ROLLED_BACK` e `LOOT_DATA_TRANSACTION_DEGRADED`.
Falha antes da primeira mutação termina fail-safe; falha posterior exige
rollback integral e verificado.

## Códigos de Global Loot Modifiers

`GLM_ENTRY_FILE_MISSING`, `GLM_TYPE_MISSING`, `GLM_TYPE_UNKNOWN`,
`GLM_CODEC_ERROR`, `GLM_REGISTRY_REFERENCE_MISSING`, `GLM_CONDITION_INVALID`,
`GLM_DECODE_FAILED`, `LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED`,
`GLM_COMMIT_WRONG_THREAD`, `GLM_COMMIT_REQUIRES_JOINT_LOOT_TRANSACTION`,
`LOOT_COMMIT_REQUIRES_JOINT_GLM_TRANSACTION` e
`LOOT_GLM_TRANSACTION_DEGRADED`. Conteúdo inválido bloqueia o artefato inteiro;
falha posterior à primeira atribuição exige rollback verificado dos managers
afetados, sem aceitar restauração parcial.

## Erros e diagnóstico do harness Windows

`IDENTITY_MISMATCH` permanece `HARNESS_FAILURE` quando a identidade atual não
pode ser separada com segurança da identidade owned. Somente exit previamente
confirmado, `creation_time` posterior e ausência de descendência, referências
ao run, TCP, reader threads, locks e manifests residuais permitem classificar
`PID_REUSED_AFTER_OWNED_PROCESS_EXIT`. Esse evento fica em `pid_reuse_events` e
não é processo residual.

WARNING não invalida candidato. ERROR e BLOCKER tornam
`PreparedReloadArtifact.isApplicable()` falso.

## Códigos de recipes

`RECIPE_JSON_SYNTAX_ERROR`, `RECIPE_DESERIALIZATION_ERROR`,
`RECIPE_CONDITION_FALSE` (INFO), `RECIPE_TAG_DEPENDENCY_CHANGED` e
`RECIPE_LIMIT_EXCEEDED`. Recipes com erro ou blocker permanecem somente em
memória e `apply prepared` responde explicitamente que commit de recipes ainda
não foi implementado.

## Códigos de KubeJS

`KUBEJS_NOT_PRESENT`, `KUBEJS_VERSION_UNSUPPORTED`,
`KUBEJS_RUNTIME_UNAVAILABLE`, `KUBEJS_STARTUP_SCRIPT_CHANGED_RESTART_REQUIRED`,
`KUBEJS_CLIENT_SCRIPT_IGNORED`, `KUBEJS_MIXED_SCRIPT_UNSAFE` e
`KUBEJS_SCRIPT_CHANGED_DURING_PREPARATION` são diagnósticos da fronteira
opcional. Ausência do runtime torna somente a camada KubeJS não aplicável; não
invalida o baseline vanilla já preparado.

## Códigos de tags

`TAG_JSON_SYNTAX_ERROR`, `TAG_ENTRY_INVALID`,
`TAG_ELEMENT_REFERENCE_MISSING`, `TAG_REFERENCE_CYCLE`,
`TAG_REGISTRY_UNKNOWN`, `TAG_REGISTRY_UNSUPPORTED`,
`TAG_FUNCTION_DOMAIN_DELEGATED`, `TAG_LIMIT_EXCEEDED` e
`TAG_PREPARATION_TIMEOUT`. `TAG_BINDING_NOT_PERFORMED` é informativo; ausência
de binding não é falha porque commit está fora do escopo.
## Erros cross-provider

`JOINT_PREPARATION_SNAPSHOT_MISMATCH`, `RECIPE_CANDIDATE_TAG_MISSING`,
`RECIPE_CANDIDATE_TAG_EMPTY`, `RECIPE_SERIALIZER_CANDIDATE_TAGS_UNSUPPORTED` e
`RECIPE_CONDITION_CANDIDATE_TAGS_UNSUPPORTED` são blockers. A preparação é
atômica: erros em tags ou recipes invalidam o composto inteiro.

## Hardening conjunto tags + recipes

`recipesUsingAnyTag` é apenas um índice de diagnóstico. Os conjuntos
`recipesImpactedByTagChanges`, `recipesRevalidatedWithoutJsonChange` e
`invalidatedByTagChange` são calculados por fechamento transitivo sobre as tags
realmente adicionadas, modificadas ou removidas e pela comparação do hash do
JSON. Serializer desconhecido, serializer que lê membros ativos ou condition
com comportamento de tags desconhecido só bloqueiam uma recipe quando ela é
afetada por uma tag candidata alterada.

Falha na resolução de tags (ausente, vazia, ciclo ou membro obrigatório
ausente), serializer inseguro ou condition insegura não produz um
`PreparedRecipes` vazio: a preparação conjunta entra em `FAILED_SAFE` e nenhum
subartefato é publicado. A view candidata expõe `TagResolutionStatus` para
distinguir esses diagnósticos de uma tag resolvida.
