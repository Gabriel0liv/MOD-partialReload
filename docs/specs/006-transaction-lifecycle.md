# Spec 006 — Lifecycle transacional

## 1. Contexto

Mesmo a fase read-only precisa impedir operações concorrentes e representar falhas honestamente.

## 2. Problema

Sem máquina de estados, comandos podem sobrepor scans/planos e estados futuros podem ser fingidos.

## 3. Objetivos

Definir lifecycle completo e executar apenas o subconjunto da fase 1.

## 4. Não objetivos

Implementar prepare, quiesce, commit, sync, verify ou rollback.

## 5. Terminologia

Estados completos: IDLE, SCANNING, PLANNING, PREPARING, VALIDATING, READY, QUIESCING, COMMITTING, SYNCHRONIZING, VERIFYING, SUCCESS, ROLLED_BACK, FAILED_SAFE, DEGRADED.

## 6. Requisitos funcionais

- RF-006-1: fase 1 permite IDLE→SCANNING→IDLE/FAILED_SAFE.
- RF-006-2: permite IDLE→PLANNING→READY/FAILED_SAFE.
- RF-006-3: READY→IDLE inicia nova operação/acknowledgement.
- RF-006-4: FAILED_SAFE→IDLE permite recuperação.
- RF-006-5: demais estados existem na enum/spec, mas não têm operações executáveis.
- RF-006-6: transições inválidas são rejeitadas.

## 7. Requisitos não funcionais

Transições são serializadas e observáveis; estado não substitui detalhes do último erro.

## 8. Invariantes

No máximo uma operação ativa. Falha read-only nunca chega a DEGRADED, pois não houve commit.

## 9. Modelo de erros

Transição inválida gera `InvalidStateTransitionException`; falha de operação registra erro tipado e move a FAILED_SAFE.

## 10. Riscos

Callback assíncrono tardio publicar resultado após nova operação; mitigado por serialização/operation ID futuro.

## 11. Critérios de aceitação

Transições válidas e inválidas têm testes; comandos status mostram estado real; estados futuros não são anunciados como funcionais.

## 12. Cenários de teste

Fluxo scan sucesso/falha, planning sucesso/falha, tentativa IDLE→COMMITTING, recuperação.

## 13. Decisões pendentes

Cancellation, operation IDs, persistência de DEGRADED e política de recuperação.

## 14. Relação com outras specs

Orquestra 003/005 e implementa ADR-005.
