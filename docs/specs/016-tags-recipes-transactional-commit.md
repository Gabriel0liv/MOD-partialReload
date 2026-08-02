# Spec 016 — commit transacional conjunto de tags + recipes

## 1. Contexto

`PreparedTagsAndRecipes` já fornece uma visão candidata imutável. Esta spec
define a publicação real server-authoritative. O modo padrão continua limitado
ao servidor dedicado sem jogadores; a Fase 4F-R acrescenta um modo explícito
que publica com jogadores conectados e adia o refresh visual dos clientes até
o relog.

## 2. Problema

Preparação não altera bindings de tags nem o `RecipeManager`; publicar apenas
um dos dois deixaria recipes e ingredients incoerentes.

## 3. Objetivos

- publicar tags e recipes no mesmo safe point;
- preservar uma geração anterior completa;
- invalidar caches de `Ingredient`;
- disparar o evento Forge de tags;
- verificar e fazer rollback server-side;
- recusar players conectados no modo padrão;
- no modo deferred, fechar todos os menus antes da primeira mutação e marcar os
  clientes presentes como stale somente depois do sucesso confirmado.

## 4. Não objetivos

Não inclui sincronização live com clientes, payloads, ACK, atualização de recipe
book/viewers durante a sessão, rollback distribuído, KubeJS, loot, predicates,
registries dinâmicos, biomas, Mixin ou `/reload`.

## 5. Terminologia

`PreparedTagsAndRecipes`: artefato candidato; `ActiveTagRecipeGeneration`:
snapshot vinculável de tags + recipes; `preflight`: validação sem mutação;
`SERVER_ONLY_NO_PLAYERS`: nível padrão; `SERVER_COMMIT_DEFERRED_CLIENT_REFRESH`:
nível opt-in que publica no servidor imediatamente e exige relog para atualizar
informações client-side; `STALE_UNTIL_RELOGIN`: marcador server-side de uma
sessão presente no safe point de um commit deferred confirmado.

## 6. Requisitos funcionais

1. Somente artefato conjunto aplicável pode ser aplicado.
2. A política `REJECT` recusa players conectados com
   `TAG_RECIPE_COMMIT_PLAYERS_CONNECTED`.
3. A política `DEFER_CLIENT_REFRESH_UNTIL_RELOGIN` permite players conectados,
   mas deve ser solicitada explicitamente por
   `/partialreload apply prepared deferred`.
4. No safe point deferred, a lista real de players é recapturada, todos os menus
   não-inventário são fechados e validados antes da primeira mutação; falha gera
   `TAG_RECIPE_DEFERRED_MENU_CLOSE_FAILED` sem stale marker.
5. Tags suportadas são resolvidas para holders antes da mutação.
6. `Registry.bindTags` recebe mapas completos, nunca patches entrada a entrada.
7. Recipes são publicadas por `RecipeManager.replaceRecipes` com coleção completa.
8. `Ingredient.invalidateAll()` é chamado após a publicação de tags.
9. `TagsUpdatedEvent` é disparado no commit e rollback.
10. Falha pós-mutação inicia rollback automático.
11. Rollback manual retém somente uma geração e continua bloqueado com players.
12. Artefatos isolados continuam recusados.
13. Só depois de verificação e `SUCCESS`, o tracker incrementa uma vez a geração,
    marca os UUIDs capturados no safe point e envia um aviso literal de relog.
14. Login e logout removem defensivamente o stale marker; players que entram
    depois do commit não são marcados.

## 7. Requisitos não funcionais

Commit somente na server thread/safe point; preflight não muta; sem reflection
genérica; compatibilidade exata MC 1.20.1/Forge 47.4.10; falha fechada.

## 8. Invariantes

Tags e recipes nunca ficam publicadas parcialmente; snapshot e registries devem
ser os mesmos do preflight; geração anterior é capturada antes da primeira
mutação; `REJECT` permanece o default; deferred não envia packets e não altera a
autoridade imediata do servidor; rollback bem-sucedido não incrementa geração
nem marca stale; não há reload global.

## 9. Modelo de erros

Erros incluem `TAG_RECIPE_COMMIT_NOT_COMPATIBLE`,
`TAG_RECIPE_COMMIT_SNAPSHOT_STALE`, `TAG_RECIPE_COMMIT_PLAYERS_CONNECTED`,
`TAG_REGISTRY_COMMIT_UNSUPPORTED`, `TAG_COMMIT_BIND_FAILED`,
`RECIPE_COMMIT_PUBLICATION_FAILED`, `INGREDIENT_CACHE_INVALIDATION_FAILED`,
`TAG_UPDATE_EVENT_FAILED`, `TAG_RECIPE_ROLLBACK_FAILED` e
`TAG_RECIPE_TRANSACTION_DEGRADED` e
`TAG_RECIPE_DEFERRED_MENU_CLOSE_FAILED`.

## 10. Riscos

Listeners Forge podem possuir efeitos externos; mods podem reter referências a
recipes; serializers modded podem não ser reversíveis; protocolo de clientes
não é atomicamente compensável.

## 11. Critérios de aceitação

Commit A→B e rollback B→A devem ser observados em dedicated server sem players;
tag, recipe, cache, identidades laterais e geração devem ser verificados;
players conectados devem ser recusados no modo normal; no modo deferred, menus
devem ser fechados e o servidor deve aplicar imediatamente enquanto clientes
permanecem online e stale até relog; registries não suportados devem ser recusados;
falhas injetadas após bind/publicação devem restaurar A.

## 12. Cenários de teste

Sucesso conjunto, registry ausente, member missing, snapshot stale, player
conectado em `REJECT`, deferred com/sem players, fechamento de menu e falha de
fechamento, join/logout race, dois players, receita/tag nova e removida,
stale/login/logout, falha antes/depois da mutação, geração, cache invalidation,
evento, rollback manual, concorrência, artefato isolado e estado `DEGRADED`.

## 13. Decisões pendentes

Sincronização live, recipe book/viewers durante a sessão, ACK e rollback
distribuído permanecem decisões futuras. O modo deferred não satisfaz esses
contratos: o relog é a fronteira explícita de refresh do cliente.

## 14. Relação com outras specs

Depende de 014, 015, 011 e 012; preserva os contratos de scan, snapshot,
preparation e commit de functions.

## 15. Evidência de aceitação

O harness `scripts/run-dedicated-tags-recipes-commit-acceptance.py` foi
executado no Forge 47.4.10 sem jogadores conectados. A execução observou a
tag ativa A (`minecraft:stone`), publicou a candidata B
(`minecraft:dirt`) em `SUCCESS`, preservou as identidades laterais e restaurou
A em `ROLLED_BACK`.

Em 02/08/2026, a Fase 4F-R acrescentou evidência dedicada com cliente Forge
real sem o mod principal: um `CraftingMenu` foi fechado antes do commit; o
servidor rejeitou a receita removida, aceitou a receita nova e observou a tag
nova imediatamente; o cliente permaneceu online e na geração visual anterior,
foi marcado stale, deixou de estar stale no logout e recebeu a geração nova no
relog. O runner consolidado, agora com oito suítes, terminou com
`ALL_ACCEPTANCE_PASSED`.

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

## Safety gate closure

Os hooks `TagRecipeFaultPoint` são armáveis somente em userdev por
`/partialreload debug fault tags_recipes`, consumidos uma vez e limpos no
startup/shutdown. Isso fornece o mecanismo de teste, mas não substitui a
aceitação: fault injection crítica, player race, registry inicialmente vazio,
registries não suportados e `DEGRADED` ainda precisam de GameTests e dedicated
acceptance com observação direta. A execução de 26/07/2026 comprovou os nove
faults recoverable e a sequência `AFTER_RECIPE_PUBLICATION` +
`DURING_ROLLBACK`, incluindo lockout de `DEGRADED`; esses resultados estão no
relatório dedicado e no transcript completo. A política continua
`SERVER_ONLY_NO_PLAYERS`.
O plano `phase-four-e-s-safety-gate-tasks.md` registra a matriz concluída; a
promoção server-side foi autorizada, enquanto a Fase 4F permanece fora desta
implementação.

## Evidência de fechamento do safety gate

Em 2026-07-27, a matriz foi aceita no Forge 47.4.10: GameTests 24/24,
`phase4e-tag-recipe-transaction` completo, safety dedicada completa com seis
grupos `passed` e runner consolidado com sete suítes e
`ALL_ACCEPTANCE_PASSED`. A política padrão permanece server-only sem players.
O opt-in deferred foi aprovado posteriormente com 36/36 GameTests e acceptance
real; nenhuma sincronização client-side durante a sessão foi implementada.
