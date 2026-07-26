# Fase 4E — tarefas

- [x] especificar escopo server-only e decisões 027–034;
- [x] investigar bind público, RecipeManager, Ingredient e TagsUpdatedEvent;
- [x] implementar geração ativa conjunta;
- [x] implementar preflight fail-closed;
- [x] implementar commit/rollback em safe point;
- [x] aceitar dedicated server sem players;
- [x] repetir dedicated commit com recipe realmente alterada e fingerprints de managers;
- [x] revalidar snapshot, identidade de RecipeManager/RegistryAccess e escopo de registries no safe point;
- [x] rastrear mutações parciais por registry e preservar mapas vazios na geração anterior;
- [x] adicionar fault-injection seams userdev-only e journal transacional;
- [ ] executar matriz dedicada de fault injection, player race, registry não suportado e registry inicialmente vazio;
- [ ] concluir regressão consolidada sem lacunas de cobertura;
- [ ] manter client sync para a Fase 4F.
