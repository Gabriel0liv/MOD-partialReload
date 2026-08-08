# ADR-016 — isolamento de execução KubeJS

Status: bloqueio confirmado no KubeJS Forge 2001.6.5-build.26.

Não chamar `ServerScriptManager.reload`, `fullReload` ou
`RecipesEventJS.post` no runtime ativo. A integração só pode executar handlers
quando houver staging oficial ou clone completo de schemas, plugins, registries,
globals e contexto Rhino, com event object candidato e descarte integral.
Reflection/Mixin não são atalhos aceitos.

A Fase 4J confirmou que `ScriptType.SERVER` resolve o singleton e que
`ServerEvents.RECIPES` guarda listeners globalmente. Não existe clone completo
possível pela API publicada; veja ADR-050.
