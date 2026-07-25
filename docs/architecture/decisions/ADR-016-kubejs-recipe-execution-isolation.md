# ADR-016 — isolamento de execução KubeJS

Status: bloqueio de implementação até API comprovada.

Não chamar `ServerScriptManager.reload`, `fullReload` ou `RecipesKubeEvent.post` no runtime ativo. A integração só poderá executar handlers quando houver staging oficial ou clone completo de schemas, plugins, registries, globals e contexto Rhino, com event object candidato e descarte integral. Reflection/Mixin não são atalhos aceitos.

