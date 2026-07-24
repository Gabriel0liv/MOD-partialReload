# Spec 004 — Detecção de mudanças

## 1. Contexto

Operadores precisam saber o delta entre o estado ativo conhecido e os bytes observados agora.

## 2. Problema

Usar “último scan” como baseline apagaria mudanças ainda não aplicadas.

## 3. Objetivos

Diff puro, determinístico e agrupável por categoria.

## 4. Não objetivos

Inferir dependências semânticas, commit ou persistência na fase 1.

## 5. Terminologia

ADDED existe só no novo; REMOVED só na referência; MODIFIED mantém ID e muda fingerprint/metadado relevante; UNCHANGED é equivalente.

## 6. Requisitos funcionais

- RF-004-1: comparar por ID lógico.
- RF-004-2: produzir quatro kinds.
- RF-004-3: ordenar saída por ID.
- RF-004-4: permitir filtro/agrupamento por categoria.
- RF-004-5: primeiro scan inicializa referência ativa e produz delta sem alterações.

## 7. Requisitos não funcionais

Algoritmo O(n), sem IO e sem mutação dos snapshots.

## 8. Invariantes

`latestScan` pode mudar; `activeReference` só muda por inicialização ou futuro commit bem-sucedido.

## 9. Modelo de erros

Snapshot nulo é proibido; inconsistência de mesmo ID em snapshot é rejeitada na construção.

## 10. Riscos

Mudança de pack com bytes iguais; decisão: metadados de origem fazem parte do descriptor e podem marcar MODIFIED.

## 11. Critérios de aceitação

Testes de add/modify/remove/unchanged e agrupamento passam; scans repetidos não promovem baseline.

## 12. Cenários de teste

Baseline A → A, A → B, A → vazio, vazio → A e segundo scan após mudança ainda reporta a mudança.

## 13. Decisões pendentes

Política futura para renames e persistência do baseline.

## 14. Relação com outras specs

Consome 003 e alimenta 005/007.
