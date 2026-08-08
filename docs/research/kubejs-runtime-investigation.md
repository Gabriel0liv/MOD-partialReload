# Investigação do runtime KubeJS Forge 2001

## Artefatos auditados

Em 2026-08-08 foram obtidos do
[Maven publicado pelo projeto](https://maven.latvian.dev/releases/dev/latvian/mods/kubejs-forge/)
e confrontados com o [source oficial da branch 2001](https://github.com/KubeJS-Mods/KubeJS/tree/2001):

| artefato | SHA-256 |
| --- | --- |
| `kubejs-forge-2001.6.5-build.16.jar` | `5862fd4f9d53b9e486ae990588ee210d79ec82f6ebbd00d9310763b05f7d73c2` |
| `kubejs-forge-2001.6.5-build.24.jar` | `1ae9330a163bbda704461d79fe2d3de44db63c271a1ec30fd83e6d1d598b8e72` |
| `kubejs-forge-2001.6.5-build.26.jar` | `e9cc7fb745d5edeca7c0edb3cbf2313ff080f705ba18b7865e4d64f7be831e08` |
| `rhino-forge-2001.2.2-build.17.jar` | `b9302053f7ac8b25738423f12cae937663d681b7d2f46d109b6e0b5812e52cbf` |
| `architectury-forge-9.1.12.jar` | `61d6ec4d5e1362ec2581bca60cf637ef3d9565d61437a2356654645a8c5cce87` |

POM e Gradle Module Metadata dos três builds KubeJS declaram exatamente
Architectury Forge `9.1.12`, Rhino Forge `2001.2.2-build.17` e MixinExtras
`0.2.0-rc.2`. O `mods.toml` do `.26` exige Forge `[47.1.0,)`, Rhino e
Architectury. Portanto o runtime corresponde ao alvo Minecraft 1.20.1/Forge
47.4.10; a premissa antiga de que só havia NeoForge 1.21.1 foi removida.

## Compatibilidade entre builds

Os sources de `ServerScriptManager`, `ScriptManager`, `ScriptType`,
`EventHandler`, `RecipesEventJS` e `RecipeManagerMixin` possuem SHA-256 idêntico
nos builds `.16`, `.24` e `.26`. As mudanças entre eles estão fora da fronteira
de staging de recipes. Isso comprova estabilidade do blocker, não suporte de uma
faixa: nenhuma das três versões é promovível.

## Pipeline observado

Os Mixins de world/server criam e atribuem `ServerScriptManager.instance` e
envolvem o resource manager. `wrapResourceManager` chama `reload`, dispara packs
virtuais, hooks de plugins/pre-tags e cria a instância global de
`RecipesEventJS`. O Mixin de `RecipeManager` chama `post` e cancela o apply
vanilla quando há listeners.

`ScriptManager.reload` chama `KubeJSPlugin.clearCaches`, `unload`, lê scripts,
cria novo contexto/top-level Rhino, registra bindings e executa cada arquivo.
`ScriptType.unload` remove containers SERVER de todos os `EventGroup` globais.

## Blocker de isolamento

`ScriptType.SERVER.manager` é fixo e resolve `ServerScriptManager.instance`.
`EventHandler.listen` consulta esse manager para `canListenEvents` e contexto,
depois grava em containers pertencentes aos handlers estáticos. Um manager
secundário não possui handlers locais.

`RecipesEventJS.post` depende de `KubeJSReloadListener.resources`,
`UtilsJS.staticRegistryAccess`, schemas/plugins globais, limpa caches estáticos e
por fim substitui os mapas do `RecipeManager` recebido. O evento pode ser usado
com um manager candidato, mas não há forma segura de compilar os scripts
editados e obter os novos callbacks sem alterar os listeners ativos.

Os bindings normais expõem `Utils.server`, IO/async e `Java.loadClass`. O class
filter é permissivo por padrão, exceto denies explícitos; não existe sandbox de
staging que impeça acesso a mundo, arquivos ou threads.

## Conclusão

`KUBEJS_RECIPE_RUNTIME_2001_AUDITED`.
`KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`.

Nenhum método do runtime foi executado contra o servidor ativo. Os artefatos
foram usados apenas para inspeção de metadata, source e bytecode.
