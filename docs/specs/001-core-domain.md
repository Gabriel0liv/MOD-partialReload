# Spec 001 — Domínio central

## 1. Contexto

O produto precisa impedir que arquivo, loader e operação sejam tratados como o mesmo objeto.

## 2. Problema

Sem separação de níveis, comandos proliferam, dependências somem e “scan” pode ser confundido com aplicação.

## 3. Objetivos

Definir tipos imutáveis para categoria, provider, recurso, snapshot, change set, plano e estado.

## 4. Não objetivos

Definir managers candidatos, rollback real ou API pública estável.

## 5. Terminologia

`ResourceSnapshot` é uma fotografia; `activeReference` é a fotografia conhecida como ativa; `latestScan` é observação; `ChangeSet` compara as duas; `ReloadPlan` é recomendação sem autoridade de commit.

## 6. Requisitos funcionais

- RF-001-1: IDs de recurso/provider usam `ResourceLocation`.
- RF-001-2: snapshots e planos não expõem coleções mutáveis.
- RF-001-3: mudanças são ADDED, MODIFIED, REMOVED ou UNCHANGED.
- RF-001-4: planos têm UUID, timestamp, categorias, providers, recursos, risco, dependências, avisos, blockers, suporte e `APPLY_NOT_IMPLEMENTED`.
- RF-001-5: referência ativa e último scan são armazenados separadamente apenas em memória.

## 7. Requisitos não funcionais

Tipos devem ser testáveis sem iniciar Minecraft quando possível; igualdade de recursos é por ID lógico e fingerprint.

## 8. Invariantes

Atualizar `latestScan` não atualiza `activeReference`. O primeiro scan pode inicializar a referência; nenhum scan subsequente simula commit.

## 9. Modelo de erros

Construtores rejeitam null, hash inválido, tamanho negativo e coleções inconsistentes. Estado ausente produz resultado explícito, não NPE.

## 10. Riscos

IDs colidirem entre providers, snapshot excessivo, timestamp não determinístico em testes.

## 11. Critérios de aceitação

Os quatro níveis são tipos distintos; coleções são imutáveis; diff contempla quatro kinds; plano afirma apply indisponível.

## 12. Cenários de teste

Construção defensiva; diff vazio; add/modify/remove/unchanged; agrupar mudanças por categoria.

## 13. Decisões pendentes

Persistência/journal e namespaces de IDs compostos por provider.

## 14. Relação com outras specs

Base para 002, 004, 005, 006 e 008.
