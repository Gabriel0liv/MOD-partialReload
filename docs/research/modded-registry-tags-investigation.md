# Investigação de registries modded

`RegistryAccess.registries()` permite enumerar registries presentes no servidor,
mas não prova que todo mod expõe loader de tags compatível. O provider aceita
somente caminhos conhecidos e consulta `RegistryAccess.registry` para validação;
paths desconhecidos produzem `TAG_REGISTRY_UNKNOWN`/`TAG_REGISTRY_UNSUPPORTED`.
Custom registries Forge permanecem preparados somente quando sua chave e
elementos estão disponíveis sem reflection.

