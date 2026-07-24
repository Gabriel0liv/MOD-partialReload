# Spec 002 — Categorias e providers

## 1. Contexto

Uma categoria pública pode receber dados de múltiplos loaders.

## 2. Problema

Acoplar comandos a loaders/arquivos torna a interface instável e impede transações cruzadas.

## 3. Objetivos

Definir categorias públicas e uma SPI read-only extensível.

## 4. Não objetivos

Registrar providers KubeJS, Origins ou Silent Gear na fase 1; expor commit/rollback na SPI.

## 5. Terminologia

`ReloadProvider` descobre/valida/planeja; `ProviderCompatibility` descreve suporte no ambiente; provider ausente é condição válida.

## 6. Requisitos funcionais

- RF-002-1: categorias: FUNCTIONS, ADVANCEMENTS, PREDICATES, RECIPES, LOOT, ITEM_MODIFIERS, TAGS, ORIGINS, KUBEJS, SILENTGEAR, DYNAMIC_REGISTRIES, UNKNOWN.
- RF-002-2: SPI expõe `id`, `categories`, `compatibility`, `scan`, `validate`, `createPlan`.
- RF-002-3: registry rejeita IDs duplicados e consulta por categoria.
- RF-002-4: status possíveis: SUPPORTED_READ_ONLY, PLANNED, RESTART_REQUIRED, UNKNOWN e INCOMPATIBLE.
- RF-002-5: fase 1 registra somente provider estrutural vanilla/read-only.

## 7. Requisitos não funcionais

Registry não conhece classes de mods opcionais e preserva ordem determinística de registro/saída.

## 8. Invariantes

Um provider declara ao menos uma categoria; ID é único; compatibilidade não implica capacidade de apply.

## 9. Modelo de erros

ID duplicado gera exceção tipada de registro. Falha de provider é associada a seu ID e não engolida.

## 10. Riscos

Provider “universal” concentrar lógica; categoria ampla esconder dependências — mitigado por ProviderPlan interno.

## 11. Critérios de aceitação

Duplicatas falham; lookup funciona; providers opcionais podem estar ausentes; nenhum import de mods externos.

## 12. Cenários de teste

Registrar, duplicar, consultar categoria sem provider, reportar compatibilidade e ordem.

## 13. Decisões pendentes

Discovery por ServiceLoader/evento e versionamento da SPI.

## 14. Relação com outras specs

Implementa ADR-001 e ADR-004; alimenta 003 e 005.
