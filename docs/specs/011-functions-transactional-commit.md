# Spec 011 — Commit transacional de functions

## 1. Contexto

Specs 009 e 010 produzem candidatos passivos. A pesquisa e ADR-008–011 aprovam
o primeiro commit real exclusivamente para functions no alvo Forge exato.

## 2. Problema

Publicar functions precisa coordenar chain, tick/load, schedules, snapshot,
verificação e rollback sem acionar o pipeline global nem afetar outros managers.

## 3. Objetivos

Construir uma library real; publicar `PreparedFunctions` válido no safe point da
server thread; preservar anterior; atualizar functions/tags/tick; aplicar
`DO_NOT_RUN`; verificar; promover somente baseline FUNCTIONS; rollback
automático/manual; journal e status em memória.

## 4. Não objetivos

Commit de loot/predicates/modifiers/recipes/tags gerais/advancements, integrações
externas, sync customizada, journal em disco, rollback após restart, múltiplas
gerações, outras políticas load, commit multi-provider, `/reload`,
`reloadResources` ou cliente obrigatório.

## 5. Terminologia

Prepared generation é o artefato passivo. Candidate generation contém uma
`ServerFunctionLibrary` integral ainda inativa. Active generation é a library
do manager. Previous retained generation é a única reversão disponível.
`DO_NOT_RUN` é a única `FunctionCommitPolicy`. Transaction journal é a sequência
imutável de eventos observados.

## 6. Requisitos funcionais

- RF-011-1: aceitar somente `PreparedFunctions`, READY, aplicável, sessão e
  dispatcher correspondentes.
- RF-011-2: recapturar snapshot no worker e rejeitar stale antes da mutação.
- RF-011-3: construir library real completa com cópias imutáveis e ordem de tag.
- RF-011-4: somente uma operação mutável; artifact em uso não pode ser
  descartado.
- RF-011-5: apply entra QUIESCING e publica apenas no próximo tick END elegível.
- RF-011-6: recusar solicitação dentro de `ExecutionContext`.
- RF-011-7: swap, atualização de ticking e supressão load ocorrem sem yield.
- RF-011-8: verificar identidade/maps/tags/tick/load flag/schedules e managers
  laterais sem executar functions.
- RF-011-9: sucesso promove apenas descritores FUNCTIONS no baseline, recalcula
  diff, consome prepared e retém uma geração.
- RF-011-10: falha pós-swap restaura library/tick/load/baseline; falha de
  restauração entra DEGRADED.
- RF-011-11: rollback manual usa o mesmo safe point, consome a única retenção e
  restaura baseline.
- RF-011-12: schedules existentes não são escritos ou migrados.
- RF-011-13: journal registra REQUESTED, VALIDATED, QUEUED,
  SAFE_POINT_REACHED, PREVIOUS_GENERATION_CAPTURED, CANDIDATE_BUILT,
  LIBRARY_SWAPPED, LOAD_SUPPRESSED, TICK_SET_UPDATED,
  VERIFICATION_STARTED/PASSED, BASELINE_PROMOTED, SUCCESS ou rollback/falha.
- RF-011-14: comandos mínimos: `apply prepared`, `transaction`,
  `rollback functions`, `active functions`.
- RF-011-15: prepared loot é recusado e preservado.
- RF-011-16: self-check produz COMPATIBLE, DISABLED_INCOMPATIBLE_TARGET ou
  DISABLED_UNVERIFIED.
- RF-011-17: timeout separado para quiesce/commit/verify/rollback.

## 7. Requisitos não funcionais

Java 17; Forge 47.4.10 exato; server-side; AT mínimo; nenhuma reflection; IO e
construção candidata no worker; mutação apenas server thread; sem sleep/busy
wait; objetos transacionais imutáveis em suas exposições; logs incluem
requester, IDs e resultado. Versão permanece `0.1.0-SNAPSHOT` até toda validação
da fase passar; só então passa a `0.2.0-SNAPSHOT`.

## 8. Invariantes

Somente artifact aplicável e atual; server thread/safe point; uma transação;
nenhuma outra categoria; anterior retida até verify; falha pré-swap não muda;
falha pós-swap tenta rollback; rollback falho degrada; DEGRADED bloqueia
mutação; baseline só após verify; artifact consumido após commit; loot nunca
chega ao publisher; zero load executada; zero reload global.

## 9. Modelo de erros

Códigos: `FUNCTION_COMMIT_NOT_COMPATIBLE`, `FUNCTION_PREPARATION_REQUIRED`,
`FUNCTION_PREPARATION_STALE`, `FUNCTION_PREPARATION_INVALID`,
`FUNCTION_PREPARATION_IN_USE`, `FUNCTION_COMMIT_WRONG_CATEGORY`,
`FUNCTION_TRANSACTION_ALREADY_RUNNING`,
`FUNCTION_APPLY_FROM_ACTIVE_CHAIN_REJECTED`,
`FUNCTION_SAFE_POINT_TIMEOUT`, `FUNCTION_LIBRARY_BUILD_FAILED`,
`FUNCTION_LIBRARY_SWAP_FAILED`, `FUNCTION_LOAD_SUPPRESSION_FAILED`,
`FUNCTION_TICK_SET_MISMATCH`, `FUNCTION_LOAD_SET_MISMATCH`,
`FUNCTION_ACTIVE_FUNCTION_MISMATCH`, `FUNCTION_VERIFICATION_FAILED`,
`FUNCTION_ROLLBACK_STARTED/SUCCEEDED/FAILED`, `FUNCTION_COMMIT_DEGRADED`,
`FUNCTION_BASELINE_PROMOTION_FAILED`, `FUNCTION_COMMIT_SUCCEEDED`,
`FUNCTION_MANUAL_ROLLBACK_UNAVAILABLE`.

Issues/eventos carregam transaction/preparation/generation/state, mensagem,
causa e flags de mutação/rollback quando aplicável. Falha pré-mudança é
FAILED_SAFE; rollback exitoso é ROLLED_BACK; rollback falho é DEGRADED.

## 10. Riscos

Mudança do alvo quebra AT; serializers/commands modded podem reter objetos;
snapshot pode mudar; tags são ordenadas; callback de tick de outro mod pode
executar depois; memória dobra o grafo de functions. Mitigações: self-check
fechado, recaptura, priority LOWEST, cópias/ordem, uma retenção e testes.

## 11. Critérios de aceitação

Research/ADRs/spec precedem código; candidata real; apply fora da chain e na
server thread; anterior preservada; functions/tags/tick mudam; load não roda;
schedules persistem; verify/baseline funcionam; rollback automático/manual;
DEGRADED fechado; loot/stale/incompatível recusados; managers laterais idênticos;
unit/GameTests/dedicated/clean build/diff check passam; nenhum reload global,
commit Git ou push.

## 12. Cenários de teste

Artefato errado/inválido/stale; bridge compatível/incompatível; ordem de tags;
política load; sucesso completo; falhas antes/durante/depois do swap; rollback e
DEGRADED; exclusão/timeout/journal; baseline promote/restore; prepared consumido;
chain solicitante; function add/remove/change; tag/tick/load; schedules ID/tag;
identidade manager/library e managers laterais; comandos em dedicated.

## 13. Decisões pendentes

Políticas load adicionais, referências retidas por mods, persistência, mais de
uma geração, API de permission nodes e suporte a outra versão. Commit de loot
continua sem bridge/barreira aprovada.

## 14. Relação com outras specs

Evolui 001, 004, 006, 007 e 009; preserva 010 PREPARE_ONLY; implementa
ADR-008–011 e mantém ADR-002/003. Plano:
`docs/plans/phase-three-a-tasks.md`.

## Implementação verificada nesta revisão

O escopo aprovado foi implementado para functions vanilla: o Access
Transformer usa os nomes SRG exatos, `FunctionLibraryBridge` constrói e publica
uma candidata no `ServerTickEvent.END` de prioridade LOWEST, a política efetiva
é `DO_NOT_RUN`, e uma única geração anterior é retida para rollback. Loot data
continua sem publisher. GameTests exercitam apply, troca de library e rollback;
qualquer extensão além desses limites exige nova revisão desta spec.
