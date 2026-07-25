# Investigação de isolamento de scripts

`ServerScriptManager.reload()` limpa packs virtuais, recarrega scripts e pode gerar dados/registries; `fullReload()` delega ao comando global `reload`. `RecipesKubeEvent` carrega globals, plugins, schemas e `RegistryOpsContainer`.

Não foi encontrada API oficial de clone, staging ou event facade limitada a recipes. Executar handlers compilados do runtime ativo seria inseguro; um clone sem registries/plugins reais seria semanticamente incompleto. Resultado: nenhuma execução nesta fase; scripts serão apenas classificados e fingerprintados.

