# Investigação de isolamento de scripts

No KubeJS Forge 2001.6.5, `ServerScriptManager.wrapResourceManager` instala
packs virtuais e chama `ScriptManager.reload`. Esse método limpa caches de
plugins, descarrega listeners SERVER globais, recria contexto/top-level Rhino e
executa todos os arquivos. `ScriptType.SERVER` sempre resolve a instância
estática ativa; `EventHandler.listen` não aceita registry alternativo.

Não existe API oficial de clone, staging ou event facade limitada a recipes.
Um clone tipado também não é possível sem trocar o singleton/handlers globais.
Bindings expõem `Utils.server`, Java, IO e async, de modo que rollback de estado
KubeJS não desfaria side effects externos. Resultado:
`KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`.
