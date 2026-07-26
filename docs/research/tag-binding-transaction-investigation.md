# Tag binding transacional

No source mapeado 1.20.1, `MappedRegistry` mantém `volatile Map<TagKey<T>,
HolderSet.Named<T>> tags`, expõe `getTags`, `getTag`, `resetTags` e
`bindTags(Map<TagKey<T>, List<Holder<T>>>)`. `bindTags` reconstrói o binding e
atualiza as referências dos holders; portanto o commit usa mapas completos e
captura `getTags()` antes da mutação. A API é server-thread safe quando chamada
no safe point. Registries sem `RegistryAccess` correspondente são bloqueados.
