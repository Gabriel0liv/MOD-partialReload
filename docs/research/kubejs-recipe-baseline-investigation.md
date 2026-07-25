# KubeJS recipe baseline

KubeJS não está presente como dependência nesta fase. A fronteira aprovada é
um provider separado: `VanillaRecipesProvider` produz primeiro um candidato
independente; uma fase futura poderá executar scripts sobre essa coleção, nunca
sobre o RecipeManager ativo. `startup_scripts` e addons permanecem fora do
escopo. A versão concreta deve ser pesquisada antes de adicionar compileOnly ou
runtime opcional.
