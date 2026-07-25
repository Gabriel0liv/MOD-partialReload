# Fase 4C — tarefas

- [x] pesquisa TagLoader/TagManager/RegistryAccess e sincronização;
- [x] Spec 014 e ADR-019–022 antes do provider;
- [x] provider geral read-only e artefatos imutáveis;
- [x] exclusão de function tags;
- [x] stack, replace, values, remove representado e nested graph;
- [x] comando prepare/prepared/discard e recusa de apply;
- [x] testes unitários básicos e GameTests existentes;
- [x] dedicated acceptance com fixture de tags gerais Forge (735 arquivos, 15 registries, candidato aplicável, apply recusado e bindings preservados);
- [ ] validar semântica Forge remove em custom registries reais;
- [ ] composição conjunta tags+recipes (Fase 4D).

## Evidência da aceitação dedicada

`python scripts/run-dedicated-tag-acceptance.py` foi executado em Forge
47.4.10/Minecraft 1.20.1. O servidor alcançou `Done`, preparou 735 arquivos
em 15 registries (3.947 membros resolvidos e 317 arestas), produziu candidato
`Technically applicable: true`, recusou `apply prepared` sem mutar bindings e
encerrou via RCON com salvamento normal. O relatório está em
`build/reports/dedicated-tag-acceptance.json`.

Os avisos informativos sobre `worldgen/biome` refletem a ausência desse
registry em `RegistryAccess` do ambiente de preparação; não são blockers.
Binding, sincronização e semântica completa de `remove` continuam fora do
escopo desta fase.
