# ADR-003 — Proibição de reload global

Status: Aceito — 2026-07-24

## Contexto

`MinecraftServer.reloadResources` reconstrói e troca todos os managers, atualiza tags, jogadores, commands, structures e packets.

## Decisão

Nunca chamar esse método, `/reload` ou listener isolado como implementação de partial reload.

## Consequências

Cada provider futuro precisa de contrato de candidato, dependências, commit e sync comprovados. A fase 1 só lê.

## Alternativas rejeitadas

Filtrar pack list e chamar reload global; chamar `listener.reload` diretamente. Ambos mantêm os custos/riscos ou quebram pressupostos de outros managers.
