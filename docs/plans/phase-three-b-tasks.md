# Plano de tarefas — Fase 3B

## Gate SDD

- [x] auditar repositório, docs e testes existentes;
- [x] investigar loader exato de loot data;
- [x] investigar GLM e registrar ADR-007;
- [x] inventariar padrões MineDev;
- [x] aprovar Spec 010 antes do código.

## Implementação

- [ ] criar modelo imutável `PreparedLootData` e wrappers opacos;
- [ ] implementar tipos, snapshot/delta e stack de packs;
- [ ] implementar grafo de dependências;
- [ ] implementar parser/resolver/validator conjunto;
- [ ] implementar `VanillaLootDataProvider`;
- [ ] integrar lifecycle, exclusão, TOCTOU, timeout e limites;
- [ ] integrar planner e comandos;
- [ ] reportar GLM e loaders externos.

## Verificação

- [ ] testes unitários do domínio/provider;
- [ ] fixtures dos três tipos e cenários inválidos;
- [ ] GameTests de preparação e não mutação;
- [ ] `.\gradlew.bat clean build`;
- [ ] `.\gradlew.bat runGameTestServer`;
- [ ] `.\gradlew.bat runServer` e comandos dedicados;
- [ ] auditoria por `reloadResources`, `/reload`, cliente, Mixin, AT e reflection;
- [ ] atualizar README, AGENTS, arquitetura e matriz com resultados reais.

