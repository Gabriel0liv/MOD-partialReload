# Spec 005 — Planejamento de reload

## 1. Contexto

Antes de qualquer futura alteração, o sistema deve explicar escopo e bloqueios.

## 2. Problema

Uma lista de arquivos não informa suporte, risco ou dependências.

## 3. Objetivos

Criar `ReloadPlan` imutável e exclusivamente read-only.

## 4. Não objetivos

Reservar recursos, preparar managers, aplicar ou garantir rollback.

## 5. Terminologia

`ProviderPlan` é contribuição interna; blocker impede apply; warning não impede; risco resume pior caso; suporte informa maturidade.

## 6. Requisitos funcionais

- RF-005-1: plano inclui ID/timestamp/categorias/providers/mudanças/risco/dependências/warnings/blockers/suporte.
- RF-005-2: todos os planos da fase 1 incluem `APPLY_NOT_IMPLEMENTED`.
- RF-005-3: DYNAMIC_REGISTRIES gera `RESTART_REQUIRED` e blocker.
- RF-005-4: UNKNOWN gera suporte UNKNOWN e blocker.
- RF-005-5: ORIGINS/KUBEJS/SILENTGEAR são PLANNED e bloqueados.
- RF-005-6: filtro `changed` e filtro por categoria são suportados.

## 7. Requisitos não funcionais

Planejamento é puro após o ChangeSet, determinístico exceto UUID/clock injetáveis.

## 8. Invariantes

Plano não concede autoridade para commit. Risco nunca é reduzido ao agregar providers.

## 9. Modelo de erros

Categoria inválida é input error. Ausência de scan/reference gera estado inválido seguro.

## 10. Riscos

Subestimar dependência; mitigação: warnings explícitos e suporte conservador.

## 11. Critérios de aceitação

Plano contém mudanças corretas, propaga blockers e marca registries dinâmicos como restart.

## 12. Cenários de teste

Plano vazio; categoria suportada; mistura com dynamic registry; UNKNOWN; provider planned.

## 13. Decisões pendentes

Formato persistente/TTL e resolução de dependency graph.

## 14. Relação com outras specs

Consome 002/004 e aparece em 007; estados em 006.
