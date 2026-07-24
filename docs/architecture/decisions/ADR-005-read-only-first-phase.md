# ADR-005 — Primeira fase read-only

Status: Aceito — 2026-07-24

## Contexto

Pesquisa confirmou contratos acoplados, estado vivo e sync específico em todos os loaders de interesse.

## Decisão

Fase 1 implementa identidade, scan, fingerprint, diff, plano e comandos observacionais. Apply, reload e rollback são recusados explicitamente. Baseline fica em memória e scan não o promove.

## Consequências

Entrega valor diagnóstico sem risco de gameplay e produz evidência para fase 2. Não há alegação de hot reload funcional.

## Alternativas rejeitadas

Começar por recipes/KubeJS ou funções sem modelo transacional.
