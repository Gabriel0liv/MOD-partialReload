# Spec 009 — Preparação de functions e decisão de predicates

## 1. Contexto

A fase 1 observa recursos e cria planos, mas não reconstrói candidatos. A
pesquisa do loader de functions encontrou uma preparação pública e passiva
baseada em Brigadier; a ADR-006 concluiu que predicates pertencem ao grafo de
loot.

## 2. Problema

É necessário provar que uma geração completa de functions pode ser lida,
compilada, relacionada e validada sem executar comandos, trocar library, mudar
tick/load ou tocar em qualquer manager ativo.

## 3. Objetivos

- preparar uma nova geração completa de functions quando a categoria for alvo;
- usar o dispatcher real e reconhecer comandos vanilla/modded;
- compilar sem executar e rejeitar a geração inteira se uma function falhar;
- reportar arquivo, pack, linha, cursor/coluna, comando, mensagem e causa;
- aplicar merge/replace/remove/optional e referências de function tags;
- resolver todos os tags, `minecraft:tick` e `minecraft:load`;
- construir grafo de chamadas, memberships, referências ausentes e ciclos;
- produzir artefato imutável, identificável, descartável e ligado ao snapshot;
- invalidar TOCTOU, timeout e limites;
- executar PREPARING e VALIDATING, com somente uma preparação por vez;
- manter predicates classificados, mas bloqueados conforme ADR-006.

## 4. Não objetivos

Publicar a geração; chamar `replaceLibrary`; alterar `ServerFunctionManager`;
executar load/functions/comandos; mudar tick ativo; migrar schedules; alterar
scoreboards, entity tags, advancements, recipes ou loot; sincronizar clientes;
commit, quiesce, verify ou rollback; `/reload`; `reloadResources`; listener
ativo isolado; Mixin; Access Transformer; `PreparedPredicates`.

## 5. Terminologia

`PreparedReloadArtifact` é candidato passivo. `PreparedFunctions` agrega
functions compiladas encapsuladas, tags resolvidas, sets tick/load, deltas contra
o estado ativo, grafo e validação. “Aplicável” significa tecnicamente elegível
para um commit futuro; não significa que commit exista.

Relações: `DIRECT_FUNCTION_CALL`, `FUNCTION_TAG_CALL`,
`SCHEDULED_FUNCTION_CALL`, `TICK_MEMBERSHIP`, `LOAD_MEMBERSHIP`.

Políticas futuras de load: `DO_NOT_RUN` (default), `RUN_NEWLY_ADDED`,
`RUN_CHANGED_AND_ADDED`, `RUN_ALL`.

## 6. Requisitos funcionais

- RF-009-1: adicionar contrato `PreparedReloadArtifact` sem métodos de commit.
- RF-009-2: `VanillaFunctionsProvider` prepara todas as functions e function
  tags da visão atual, mesmo quando só um arquivo mudou.
- RF-009-3: parsing usa o dispatcher ativo e sem execução.
- RF-009-4: qualquer erro de function, tag obrigatório ou TOCTOU torna
  `isApplicable=false`; warnings não.
- RF-009-5: encapsular objetos compilados sem expor método de execução.
- RF-009-6: snapshot fingerprinta functions vencedoras e stacks completas de
  tags, incluindo pack/order.
- RF-009-7: tag resolver suporta `replace`, `values`, `remove`, optional,
  referências, missing e cycles.
- RF-009-8: preparar todos os tags e sets `minecraft:tick`/`minecraft:load`,
  sem executar load nem alterar ticking.
- RF-009-9: registrar dependências via argumentos já validados pelo Brigadier;
  parser textual conservador usa apenas o range do `FunctionArgument`.
- RF-009-10: detectar ciclos de chamada como warning.
- RF-009-11: calcular added/removed/retained de tick e load e emitir
  `TICK_FUNCTION_SET_CHANGED`/`LOAD_FUNCTION_SET_CHANGED`.
- RF-009-12: implementar `prepare changed`, `prepare functions`, `prepared` e
  `discard`; manter stubs mutáveis.
- RF-009-13: transições executáveis:
  IDLE→PREPARING→VALIDATING→READY, com falha inesperada para FAILED_SAFE.
- RF-009-14: impedir preparação concorrente, permitir descarte e nunca deixar
  PREPARING após completion.
- RF-009-15: config valida `prepare_timeout_seconds=60`,
  `max_function_count=100000`, `max_function_lines=1000000`.
- RF-009-16: predicates produzem blocker `PREDICATES_COUPLED_TO_LOOT`, sem
  candidato parcial.
- RF-009-17: um artefato READY não altera manager/library/tick/load/schedules ou
  outros managers ativos.
- RF-009-18: `return run ...` é inválido em 1.20.1 e fica a cargo do Brigadier.

## 7. Requisitos não funcionais

Java 17; coleções defensivas; UUID/clock injetáveis; sem singleton novo; nenhum
`MinecraftServer` retido pelo artefato/provider; IO e parsing no worker seguindo
o precedente vanilla; captura/publicação/transições na server thread; deadlines
cooperativos; mensagens administrativas limitadas; logs com provider/operação.

## 8. Invariantes

O artefato nunca executa entries e nunca é passado ao manager ativo. A referência
ativa da fase 1 não é promovida. `replaceLibrary`, `/reload`,
`reloadResources`, Mixin, AT e classes cliente não aparecem na implementação.
Load policy efetiva é sempre `DO_NOT_RUN`. Uma geração parcialmente compilada
nunca é aplicável.

## 9. Modelo de erros

`ValidationIssue` passa a transportar severity, code, category, provider,
resource, pack, message, source location e causa quando disponíveis.

Códigos: `FUNCTION_PARSE_ERROR`, `FUNCTION_COMMAND_ERROR`,
`FUNCTION_REFERENCE_MISSING`, `FUNCTION_TAG_REFERENCE_MISSING`,
`FUNCTION_TAG_CYCLE`, `FUNCTION_RECURSION_DETECTED`,
`LOAD_FUNCTION_SET_CHANGED`, `TICK_FUNCTION_SET_CHANGED`,
`RESOURCE_CHANGED_DURING_PREPARATION`, `PREPARATION_TIMEOUT`,
`PREPARATION_LIMIT`, `PREPARATION_IN_PROGRESS`,
`PREDICATES_COUPLED_TO_LOOT`.

Erro esperado de conteúdo produz artefato inválido em READY. Exceção de
infraestrutura/timeout leva a FAILED_SAFE e não publica candidato parcial.

## 10. Riscos

Argument parsers modded podem violar o precedente vanilla e acessar server no
parse; tag stacks podem mudar durante leitura; timeout é cooperativo; grafo
estático não representa comandos gerados por macros (inexistentes em 1.20.1) ou
dados dinâmicos; library ativa é privada e impede rollback público futuro.

Mitigações: dispatcher real, source igual ao vanilla, snapshot duplo, validação
conservadora, limites e ausência total de commit.

## 11. Critérios de aceitação

1. research/spec/ADR precedem código;
2. functions válidas compilam sem execução;
3. erro por linha/comando invalida toda geração;
4. tags e tick/load são resolvidos com pack merge;
5. dependências/missing/cycles são reportados;
6. snapshot é exato, imutável e verificado contra TOCTOU;
7. timeout/limites e concorrência são testados;
8. PREPARING/VALIDATING são executáveis; estados futuros continuam bloqueados;
9. prepare/prepared/discard funcionam e stubs mutáveis permanecem;
10. manager/tick/load/schedules/scoreboards/recipes/loot permanecem inalterados;
11. predicates ficam bloqueados conforme ADR-006;
12. unit tests, GameTests, dedicated startup e `clean build` passam;
13. docs finais refletem somente evidência executada.

## 12. Cenários de teste

Artefato/coleções imutáveis; snapshot associado; function válida/inválida/comando
desconhecido; chamada direta/tag/schedule/execute; missing; merge replace e
optional; tag cycle; recursion warning; delta tick/load; mudança entre captures;
timeout/limites; duas preparações; descarte; falha segura; predicate blocker;
GameTests com functions/tags reais, load não reexecutado, tick/manager e estado
representativo inalterados.

## 13. Decisões pendentes

Fase 3 deve escolher entre commit transacional de functions e candidato conjunto
de loot. Commit de functions ainda precisa resolver acesso à library anterior,
barreira com `ExecutionContext`, rollback, load policy, schedules e verificação.
Paginação/grafo por comando ficam opcionais.

## 14. Relação com outras specs

Evolui 001, 002, 005, 006, 007 e 008; implementa a pesquisa de functions e
ADR-006; não altera ADR-002/003/005. Plano em
`docs/plans/phase-two-tasks.md`.
