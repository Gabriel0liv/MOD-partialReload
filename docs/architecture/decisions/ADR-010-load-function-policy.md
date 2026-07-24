# ADR-010 — Política de load functions

Status: Aceito — 2026-07-24

## Contexto

`replaceLibrary` sempre define `postReload=true`; o tick seguinte executaria
`minecraft:load`, com possíveis resets e setup não idempotente.

## Decisão

Somente `DO_NOT_RUN` é implementado. No mesmo safe point, imediatamente após
cada swap de commit ou rollback, o bridge define `postReload=false` e a
verificação confirma a supressão.

`RUN_NEWLY_ADDED`, `RUN_CHANGED_AND_ADDED` e `RUN_ALL` são
`NOT_IMPLEMENTED`.

## Consequências

O candidato preserva a tag load para consultas futuras, mas nunca a executa
implicitamente. Falha de supressão inicia rollback; falha no rollback degrada.

## Alternativas rejeitadas

Deixar para o próximo tick; remover a tag load da candidata; executar load
automaticamente; substituir o método `tick`.

