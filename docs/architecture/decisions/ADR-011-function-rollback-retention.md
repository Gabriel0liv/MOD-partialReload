# ADR-011 — Retenção para rollback de functions

Status: Aceito — 2026-07-24

## Contexto

Rollback seguro requer a library e o baseline realmente ativos antes do swap.

## Decisão

Reter uma geração anterior completa, somente em memória, enquanto o servidor
estiver em execução e `retain_previous_generation=true`. Novo commit
bem-sucedido substitui a retenção. Rollback manual consome a retenção e não cria
histórico/redo. Reinício perde a retenção.

Falha pós-mudança tenta rollback automático. Sucesso termina `ROLLED_BACK`;
falha termina `DEGRADED`, bloqueia prepare/apply/rollback mutáveis e exige
restart. Comandos read-only continuam disponíveis.

## Consequências

Memória inclui uma library, CommandFunctions, tags e snapshot anteriores. O
baseline anterior é restaurado sem alterar os arquivos; portanto o diff volta a
mostrar a geração presente no filesystem.

## Alternativas rejeitadas

Histórico arbitrário, journal persistente, reconstrução por arquivos atuais e
rollback sem verificação.

