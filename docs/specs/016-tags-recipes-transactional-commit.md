# Spec 016 — commit transacional conjunto de tags + recipes

## 1. Contexto

`PreparedTagsAndRecipes` já fornece uma visão candidata imutável. Esta spec
define a primeira publicação real, limitada ao servidor dedicado sem jogadores.

## 2. Problema

Preparação não altera bindings de tags nem o `RecipeManager`; publicar apenas
um dos dois deixaria recipes e ingredients incoerentes.

## 3. Objetivos

- publicar tags e recipes no mesmo safe point;
- preservar uma geração anterior completa;
- invalidar caches de `Ingredient`;
- disparar o evento Forge de tags;
- verificar e fazer rollback server-side;
- recusar players conectados, menus e registries não suportados.

## 4. Não objetivos

Não inclui sincronização com clientes, recipe book, menus abertos, KubeJS, loot,
predicates, registries dinâmicos, biomas, Mixin ou `/reload`.

## 5. Terminologia

`PreparedTagsAndRecipes`: artefato candidato; `ActiveTagRecipeGeneration`:
snapshot vinculável de tags + recipes; `preflight`: validação sem mutação;
`SERVER_ONLY_NO_PLAYERS`: único nível suportado nesta fase.

## 6. Requisitos funcionais

1. Somente artefato conjunto aplicável pode ser aplicado.
2. Players conectados recusam com `TAG_RECIPE_COMMIT_PLAYERS_CONNECTED`.
3. Tags suportadas são resolvidas para holders antes da mutação.
4. `Registry.bindTags` recebe mapas completos, nunca patches entrada a entrada.
5. Recipes são publicadas por `RecipeManager.replaceRecipes` com coleção completa.
6. `Ingredient.invalidateAll()` é chamado após a publicação de tags.
7. `TagsUpdatedEvent` é disparado no commit e rollback.
8. Falha pós-mutação inicia rollback automático.
9. Rollback manual retém somente uma geração.
10. Artefatos isolados continuam recusados.

## 7. Requisitos não funcionais

Commit somente na server thread/safe point; preflight não muta; sem reflection
genérica; compatibilidade exata MC 1.20.1/Forge 47.4.10; falha fechada.

## 8. Invariantes

Tags e recipes nunca ficam publicadas parcialmente; snapshot e registries devem
ser os mesmos do preflight; geração anterior é capturada antes da primeira
mutação; nenhum cliente conectado é aceito; não há reload global.

## 9. Modelo de erros

Erros incluem `TAG_RECIPE_COMMIT_NOT_COMPATIBLE`,
`TAG_RECIPE_COMMIT_SNAPSHOT_STALE`, `TAG_RECIPE_COMMIT_PLAYERS_CONNECTED`,
`TAG_REGISTRY_COMMIT_UNSUPPORTED`, `TAG_COMMIT_BIND_FAILED`,
`RECIPE_COMMIT_PUBLICATION_FAILED`, `INGREDIENT_CACHE_INVALIDATION_FAILED`,
`TAG_UPDATE_EVENT_FAILED`, `TAG_RECIPE_ROLLBACK_FAILED` e
`TAG_RECIPE_TRANSACTION_DEGRADED`.

## 10. Riscos

Listeners Forge podem possuir efeitos externos; mods podem reter referências a
recipes; serializers modded podem não ser reversíveis; protocolo de clientes
não é atomicamente compensável.

## 11. Critérios de aceitação

Commit A→B e rollback B→A devem ser observados em dedicated server sem players;
tag, recipe, cache, identidades laterais e geração devem ser verificados;
players conectados, menus e registries não suportados devem ser recusados;
falhas injetadas após bind/publicação devem restaurar A.

## 12. Cenários de teste

Sucesso conjunto, registry ausente, member missing, snapshot stale, player
conectado, menu aberto, falha após bind, falha após recipes, cache invalidation,
evento, rollback manual, concorrência, artefato isolado e estado `DEGRADED`.

## 13. Decisões pendentes

Suporte de clientes, política de menus com players e listeners externos exigem
aceitação própria e permanecem fora do nível server-only.

## 14. Relação com outras specs

Depende de 014, 015, 011 e 012; preserva os contratos de scan, snapshot,
preparation e commit de functions.

## 15. Evidência de aceitação

O harness `scripts/run-dedicated-tags-recipes-commit-acceptance.py` foi
executado no Forge 47.4.10 sem jogadores conectados. A execução observou a
tag ativa A (`minecraft:stone`), publicou a candidata B
(`minecraft:dirt`) em `SUCCESS`, preservou as identidades laterais e restaurou
A em `ROLLED_BACK`. Sincronização para clientes não foi exercitada e continua
fora do contrato.

## 16. Endurecimento de segurança implementado

O safe point repete o preflight antes de qualquer mutação: identidade do
artefato, players, `RecipeManager`, `RegistryAccess`, fingerprint de
compatibilidade e hashes de tags/recipes são comparados. O escopo é derivado
dos recursos de tags realmente modificados; registries fora da allowlist
falham com `TAG_REGISTRY_COMMIT_UNSUPPORTED`. A geração anterior captura
somente esse escopo, incluindo mapas vazios, e o rollback só rebinds registries
que tiveram `bindTags` concluído. A transação registra registries mutados,
publicação de recipes, invalidação de Ingredient e despacho do evento.

Fault injection é userdev-only e permite testar falhas antes/depois de cada
etapa sem expor comando de produção. O relatório distingue core state
verificado de efeitos externos de listeners, que não são genericamente
reversíveis.
