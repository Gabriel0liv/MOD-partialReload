# Spec 013 — preparação de recipes com KubeJS

## 1. Contexto

Recipes vanilla/Forge produzem `PreparedRecipes` read-only. A Fase 4J auditou
o runtime exato KubeJS Forge `2001.6.5-build.26` para Minecraft 1.20.1 e os
builds de comparação `.16` e `.24`. O runtime existe, mas não oferece uma
fronteira isolada para recompilar `server_scripts` e capturar somente handlers
de recipes.

## 2. Problema

`ScriptManager.reload` limpa caches de plugins e os listeners SERVER globais.
`ScriptType.SERVER` resolve `ServerScriptManager.instance`; a chamada
`ServerEvents.recipes(...)` registra callbacks nos `EventHandler` estáticos.
`RecipesEventJS.post` usa esses callbacks e publica diretamente em um
`RecipeManager`. Um manager Rhino secundário não recebe um registry de eventos
local e os bindings normais expõem o servidor ativo.

## 3. Objetivos

- identificar scripts e dependências relevantes sem executá-los;
- preservar hashes e classificações em snapshots imutáveis;
- manter `VanillaRecipesProvider` como baseline;
- auditar versões reais do runtime opcional;
- produzir candidato somente quando houver staging isolado comprovado;
- preservar `RecipeManager`, script manager, listeners, globals e plugins ativos.

## 4. Não objetivos

Não chamar `reload`, `fullReload`, `/reload`, `/kubejs reload server_scripts` ou
`RecipesEventJS.post` como preparação. Não trocar temporariamente globals,
listeners ou managers; não executar startup/client scripts; não implementar
tags, loot, addons, client sync ou reflection exploratória.

## 5. Terminologia

`baseline`: `PreparedRecipes` vanilla/Forge; `script snapshot`: hashes e
classificação; `active runtime`: `ServerScriptManager.instance`, seus contextos,
listeners e globals; `staging`: execução descartável sem escrita fora do
candidato; `candidate`: coleção independente de recipes.

## 6. Requisitos funcionais

RF-013-1 detectar ausência/versão; RF-013-2 classificar scripts; RF-013-3 gerar
snapshot SHA-256 e grafo conservador; RF-013-4 preservar o baseline; RF-013-5
manter provider separado; RF-013-6 bloquear startup/client/mixed/unsafe; RF-013-7
falhar fechado quando staging não for isolável; RF-013-8 não produzir candidato
nem permitir apply nessa condição.

## 7. Requisitos não funcionais

Java 17, server-side, sem dependência obrigatória, sem `setAccessible`, Unsafe,
Mixin genérico ou swap global; tipos imutáveis, limites configuráveis e IO fora
da server thread.

## 8. Invariantes

O baseline nunca é mutado; scripts não recebem o manager ativo; preparação não
limpa listeners/caches; `ServerScriptManager.instance`, `ScriptType.SERVER`,
`EventGroup`, `UtilsJS.staticServer`, `KubeJSReloadListener.resources`, schemas e
plugins mantêm identidade e conteúdo. Falta de prova de isolamento bloqueia.

## 9. Modelo de erros

`KUBEJS_NOT_PRESENT`, `KUBEJS_VERSION_UNSUPPORTED`,
`KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`,
`KUBEJS_RECIPE_SCRIPT_SIDE_EFFECT_UNSAFE`,
`KUBEJS_STARTUP_SCRIPT_CHANGED_RESTART_REQUIRED`,
`KUBEJS_CLIENT_SCRIPT_IGNORED`, `KUBEJS_MIXED_SCRIPT_UNSAFE`,
`KUBEJS_RECIPE_IMPORT_UNSAFE` e `KUBEJS_SCRIPT_CHANGED_DURING_PREPARATION`.

## 10. Evidência da Fase 4J

Os POMs `.16`, `.24` e `.26` declaram Architectury Forge `9.1.12` e Rhino Forge
`2001.2.2-build.17`. Nas três versões, `ServerScriptManager`, `ScriptManager`,
`ScriptType`, `EventHandler`, `RecipesEventJS` e `RecipeManagerMixin` são
idênticos. O contrato necessário — contexto Rhino com listeners locais,
bindings restritos e captura de callbacks — não existe.

## 11. Critérios de aceitação

Runtime e dependências exatos auditados; blocker identificado por classe e
estado; ausência de KubeJS não quebra recipes vanilla; scripts não são
executados; provider permanece não aplicável; nenhuma acceptance de commit é
simulada; builds e testes existentes permanecem verdes.

## 12. Cenários de teste

KubeJS ausente, script recipe-only, misto, startup/client, import, limites e
snapshot continuam cobertos. Execução, equivalência, isolamento e commit só
serão adicionados quando uma API upstream ou adapter externo fornecer staging
real.

## 13. Decisão

`KUBEJS_RECIPE_RUNTIME_2001_AUDITED` e
`KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`. O commit KubeJS não foi implementado.
Rollback do runtime ativo não tornaria a preparação atômica: scripts poderiam
ter observado/mutado mundo, IO, threads ou globals antes da restauração.

## 14. Relação com outras specs

Evolui 012 e ADR-014–018; a análise detalhada está na Spec 021 e ADR-050.
Preserva integralmente os commits vanilla/Forge das fases 4E/4F-R.
