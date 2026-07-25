# Spec 013 — preparação de recipes com KubeJS

## 1. Contexto

Recipes vanilla/Forge já produzem `PreparedRecipes` read-only. KubeJS pode transformar recipes por scripts, mas o runtime disponível no ambiente é NeoForge 1.21.1 e não corresponde ao alvo Forge 1.20.1.

## 2. Problema

Executar `server_scripts` ou `RecipesKubeEvent.post` sem runtime isolado pode disparar comandos, eventos, registries e mutações no servidor ativo.

## 3. Objetivos

- identificar scripts e dependências relevantes;
- preservar hashes e classificações em snapshots imutáveis;
- manter `VanillaRecipesProvider` como baseline;
- definir provider KubeJS versionado e isolável;
- produzir candidato final somente quando houver contrato seguro;
- preservar `RecipeManager` e runtime ativos.

## 4. Não objetivos

Não executar KubeJS sem runtime Forge 1.20.1; não chamar `ServerScriptManager.reload`, `RecipesKubeEvent.post`, startup ou client scripts; não fazer commit, sincronização, tags, registries ou reflection genérica.

## 5. Terminologia

`baseline`: `PreparedRecipes` vanilla/Forge; `script snapshot`: hashes e classificação dos scripts; `handler`: callback de recipe; `candidate`: coleção independente; `side effect`: alteração fora da coleção candidata.

## 6. Requisitos funcionais

RF-013-1 detectar ausência/versão do KubeJS; RF-013-2 classificar `server_scripts`, `startup_scripts` e `client_scripts`; RF-013-3 gerar snapshots SHA-256 e grafo conservador de imports; RF-013-4 usar sempre `PreparedRecipes` como baseline; RF-013-5 separar `KubeJsRecipesProvider`; RF-013-6 bloquear startup/client/mixed scripts perigosos; RF-013-7 diagnosticar runtime indisponível; RF-013-8 recusar apply e manter runtime/manager ativos.

## 7. Requisitos não funcionais

Java 17, server-side, sem dependência obrigatória, sem reflection genérica, hash SHA-256, tipos imutáveis, limites configuráveis e IO fora da server thread.

## 8. Invariantes

O baseline nunca é mutado; scripts não recebem manager ativo; preparação não executa scripts; startup/client scripts nunca são executados; ausência de runtime torna o resultado não aplicável; `RecipeManager` e listeners não mudam; candidate e snapshots são descartáveis e imutáveis.

## 9. Modelo de erros

`KUBEJS_NOT_PRESENT`, `KUBEJS_VERSION_UNSUPPORTED`, `KUBEJS_RUNTIME_UNAVAILABLE`, `KUBEJS_RECIPE_API_UNAVAILABLE`, `KUBEJS_STARTUP_SCRIPT_CHANGED_RESTART_REQUIRED`, `KUBEJS_CLIENT_SCRIPT_IGNORED`, `KUBEJS_MIXED_SCRIPT_UNSAFE`, `KUBEJS_IMPORT_MISSING`, `KUBEJS_SCRIPT_CHANGED_DURING_PREPARATION` e `KUBEJS_PREPARATION_TIMEOUT`.

## 10. Riscos

O event atual é mutável, o runtime possui globals/caches e o lifecycle completo faz reload global. Addons podem registrar handlers sem contrato. APIs do build 2101 não são evidência para Forge 1.20.1.

## 11. Critérios de aceitação

Versão alvo e incompatibilidade documentadas; snapshot e classificação testados; ausência de KubeJS não quebra recipes vanilla; scripts não são executados; provider retorna blocker seguro; apply continua recusado; manager e runtime permanecem inalterados.

## 12. Cenários de teste

KubeJS ausente, versão incompatível, script recipe-only, script misto, startup/client script, hash/import ausente, TOCTOU, limite, timeout, artefato imutável e apply recusado.

## 13. Decisões pendentes

Obter e auditar uma distribuição KubeJS Forge 1.20.1 exata; identificar API de staging oficial ou definir módulo compat versionado. Só então testar handlers, addons, mutações e candidate final.

## 14. Relação com outras specs

Evolui 012 e ADR-012–014; depende de 003/004/006/007; preserva 009–011. Preparação de tags continua requisito anterior à integração completa.

