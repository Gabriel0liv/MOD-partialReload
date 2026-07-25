# Ingredient e tags

`Ingredient` mantém valores lógicos de item/tag e usa cache de itens para
operações de matching. A resolução de membros depende do registry/tag binding
ativo e pode ocorrer em `getItems`, `test` e serialização de rede. A preparação
conjunta não expõe `HolderSet.Named`, não chama essas operações e conserva IDs
de tags/membros somente para diagnóstico e dependências.
