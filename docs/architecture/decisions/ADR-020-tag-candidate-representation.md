# ADR-020 — representação candidata de tags

Status: aceito.

Usa representação composta: IDs lógicos ordenados são canônicos; packs, hashes,
referências e deltas preservam proveniência. `PreparedTag` não contém holders ou
`HolderSet.Named` ativos.

