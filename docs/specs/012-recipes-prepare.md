# Spec 012 — Preparação de recipes

## 1. Contexto
Functions possuem commit transacional; recipes entram nesta fase somente como
artefato preparado e passivo.

## 2. Problema
Alterações em `data/*/recipes/**/*.json` precisam ser reconstruídas com os
serializers e condições Forge reais, sem copiar ou alterar o RecipeManager.

## 3. Objetivos
Detectar e reconstruir a visão completa vencedora de recipes, avaliar
conditions, indexar por ID/tipo, registrar dependências e produzir um artefato
imutável associado ao snapshot.

## 4. Não objetivos
Commit, swap, rollback, sincronização, recipe book, menus, tags, KubeJS, loot,
advancements, client code, `/reload`, `reloadResources`, Mixin ou reflection.

## 5. Terminologia
Recipe resource é o JSON lógico; PreparedRecipe é seu wrapper imutável;
PreparedRecipes é o candidato completo; condição false é recipe omitida com
diagnóstico INFO; baseline é o snapshot usado pelo diff.

## 6. Requisitos funcionais
RF-012-1 descobrir recipes vencedoras via ResourceManager; RF-012-2 parsear com
RecipeManager.fromJson e contexto Forge; RF-012-3 construir índices por ID e
RecipeType; RF-012-4 registrar items/tags extraíveis; RF-012-5 gerar delta/grafo;
RF-012-6 bloquear tags relevantes alteradas; RF-012-7 detectar TOCTOU; RF-012-8
recusar apply preservando o artefato; RF-012-9 manter functions/loot/managers.

## 7. Requisitos não funcionais
Java 17, server-side, preparação assíncrona, limites configuráveis, tipos
imutáveis, sem IO pesado na server thread e sem API de publicação.

## 8. Invariantes
O candidato é completo e ligado a um snapshot; condition false não invalida;
erro de JSON/serializer/type/reference invalida; RecipeManager ativo nunca é
modificado; recipes são mutuamente exclusivas com outros prepared artifacts.

## 9. Modelo de erros
Usar `RECIPE_JSON_SYNTAX_ERROR`, `RECIPE_DESERIALIZATION_ERROR`,
`RECIPE_CONDITION_FALSE`, `RECIPE_TAG_DEPENDENCY_CHANGED` e
`RECIPE_LIMIT_EXCEEDED`. Serializers/tipos desconhecidos são reportados pelo
erro de desserialização do parser real.

## 10. Riscos
Serializers modded podem não ser thread-safe; dependências ocultas não são
extraíveis; tags não têm provider nesta fase; RecipeManager futuro exige sync.

## 11. Critérios de aceitação
Spec/ADRs precedem código; shaped/shapeless/cooking válidas são preparadas;
condition false vira INFO; inválida gera blocker; índices/grafo/delta são
imutáveis; apply é recusado; manager e recipe ativa permanecem iguais; tests,
GameTests e dedicated passam.

## 12. Cenários de teste
JSON válido/inválido, condition true/false/erro, override, remoção, índices,
tag dependency, TOCTOU, timeout, concorrência, discard e apply recusado.

## 13. Decisões pendentes
Integração KubeJS e commit/sync de recipes exigem specs futuras; serializers
modded só são aceitos quando o parser real estiver disponível.

## 14. Relação com outras specs
Evolui 001, 004, 006, 007, 009 e 010; preserva 011 e ADR-002/003.

## Implementação verificada

No alvo Forge 47.4.10, a aceitação dedicada preparou 1175 recipes, observando
23 tipos e 7 serializers, sem warnings/errors. `apply prepared` foi recusado
explicitamente e o fingerprint do `RecipeManager` permaneceu igual. Nenhuma
publicação ou sincronização foi introduzida.
