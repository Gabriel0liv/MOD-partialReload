# Spec 021 — integração runtime de recipes KubeJS 2001

## Status

Auditoria concluída; implementação bloqueada por ausência de staging isolável.

## Alvo e artefatos

- Minecraft 1.20.1, Forge 47.4.10 e Java 17;
- `dev.latvian.mods:kubejs-forge:2001.6.5-build.26`;
- `dev.latvian.mods:rhino-forge:2001.2.2-build.17`;
- `dev.architectury:architectury-forge:9.1.12`;
- builds KubeJS `.16` e `.24` usados para comparação.

As dependências vieram dos POMs e Gradle Module Metadata publicados, não de
versões inferidas.

## Contrato pretendido

O candidato deveria partir do JSON vanilla/Forge independente, compilar apenas
`server_scripts`, registrar callbacks de recipes num registry local, executar o
evento sobre mapas locais e entregar recipes novamente desserializadas ao
commit existente. O runtime ativo inteiro teria de manter identidade e conteúdo.

## Resultado da auditoria

Esse contrato não é expressável pela API 2001.6.5:

1. `ScriptType.SERVER.manager` é um supplier de
   `ServerScriptManager.getScriptManager()`, que retorna a instância estática.
2. `EventGroup` e `EventHandler` mantêm registry/containers process-wide.
3. `EventHandler.listen` consulta o manager ativo para `canListenEvents`,
   contexto Rhino e origem do callback.
4. `ScriptManager.reload` chama `KubeJSPlugin.clearCaches`, `unload` e limpa os
   listeners SERVER globais antes de carregar arquivos.
5. `ServerScriptManager.wrapResourceManager` também muta packs virtuais,
   globals, pre-tags, serializers especiais e hooks de plugins.
6. `RecipesEventJS.post` usa `KubeJSReloadListener.resources`,
   `UtilsJS.staticRegistryAccess`, caches estáticos e escreve nos dois maps do
   `RecipeManager` recebido.
7. Os bindings normais incluem `Utils.server`, IO/async e `Java.loadClass`; não
   há policy de staging capaz de negar o servidor ativo e side effects gerais.

Instanciar um segundo `ServerScriptManager` não resolve os itens 1–3. Trocar a
instância estática temporariamente, copiar containers privados ou restaurar
globals depois da execução violaria o contrato fail-closed e não desfaria side
effects externos.

## Stop gate

Resultado: `KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`.

Não existem `PreparedKubeJsRecipes`, adapter runtime, comando
`prepare kubejs_recipes`, commit, GameTests ou acceptance funcional. O harness
KubeJS registra o blocker como evidência de pesquisa e não inicia servidor.

## Requisito para reabrir

Uma futura versão do KubeJS ou adapter oficial precisa fornecer, de forma
tipada/versionada:

- `ScriptManager` com `ScriptType`/event registry injetáveis;
- bindings de staging sem acesso ao servidor, IO, rede ou threads;
- plugins/schemas read-only explicitamente clonáveis;
- resultado de recipes em mapas independentes, sem publicar no manager;
- descarte verificável do contexto e callbacks.

Somente então serão escritos testes de equivalência, isolamento, commit e
addons. Tags KubeJS e scripts gerais permanecem fora de escopo.
