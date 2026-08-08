# Compatibilidade de addons KubeJS

| addon | versão | schemas/hooks | testado | status |
| --- | --- | --- | --- | --- |
| KubeJS core | 2001.6.5-build.26 | schemas e hooks core | auditoria estática/bytecode | `STAGING_UNSUPPORTED` |
| LootJS | não instalado | loot/hooks | não | `UNSUPPORTED` |
| MoreJS | não instalado | recipes/hooks | não | `UNKNOWN` |
| Create/Ars/Botania/Thermal addons | não instalados | recipes modded | não | `UNKNOWN` |

O core já bloqueia no registry global de handlers; portanto instalar addons não
resolveria isolamento. Addons podem ainda registrar schemas, wrappers, caches e
`injectRuntimeRecipes`, ampliando o estado compartilhado. Nenhum addon é
declarado suportado e nenhuma recipe custom/modded KubeJS foi promovida.

Uma futura reabertura exige primeiro staging isolado no core e, depois, matriz
individual por addon/versão. JSON final não é prova suficiente de compatibilidade.
