# Plano de tarefas — Fase 3B

## Gate SDD

- [x] auditar repositório, docs e testes existentes;
- [x] investigar loader exato de loot data;
- [x] investigar GLM e registrar ADR-007;
- [x] inventariar padrões MineDev;
- [x] aprovar Spec 010 antes do código.

## Implementação

- [x] criar modelo imutável `PreparedLootData` e wrappers opacos;
- [x] implementar tipos, snapshot/delta e stack de packs;
- [x] implementar grafo de dependências;
- [x] implementar parser/resolver/validator conjunto;
- [x] implementar `VanillaLootDataProvider`;
- [x] integrar lifecycle, exclusão, TOCTOU, timeout e limites;
- [x] integrar planner e comandos;
- [x] reportar GLM e loaders externos.

## Verificação

- [x] testes unitários do domínio/provider;
- [x] fixtures dos três tipos e cenários inválidos;
- [x] GameTests de preparação e não mutação;
- [x] `.\gradlew.bat clean build`;
- [x] `.\gradlew.bat runGameTestServer`;
- [x] `.\gradlew.bat runServer` e comandos dedicados;
- [x] auditoria por `reloadResources`, `/reload`, cliente, Mixin, AT e reflection;
- [x] atualizar README, AGENTS, arquitetura e matriz com resultados reais.

## Próxima fase recomendada

Recomenda-se **Fase 3A — commit transacional de functions**, sem implementá-la
neste plano.

Evidência:

- `ServerFunctionManager.replaceLibrary(ServerFunctionLibrary)` já delimita o
  ponto de troca, enquanto loot data não possui swap público;
- schedules persistem IDs e resolvem a function no momento da execução;
- execuções já iniciadas carregam entries/`CommandFunction` da geração antiga e
  precisam terminar sob uma barreira explícita;
- tick/load exigem delta atômico e política de load default `DO_NOT_RUN`;
- rollback de functions pode reter a library anterior, desde que nenhum estado
  de gameplay seja executado implicitamente;
- commit de loot exigiria acessar o campo privado final de
  `ReloadableServerResources` ou os maps privados do manager, coordenar
  `LootContext` já criado, GLM e mods que guardam instâncias.

Antes da Fase 3A, a spec deve provar a barreira contra `ExecutionContext`,
comportamento de chains em andamento, schedules, troca/reversão da library,
tick/load, falha entre swap e verificação e necessidade exata de AT/Mixin.

Para um commit futuro de loot:

- o manager ativo está no campo privado final `lootData` de
  `ReloadableServerResources`;
- `LootDataManager` guarda `elements` e `typeKeys` privados e não expõe replace;
- `LootContext` captura um `LootDataResolver`, portanto contexts em andamento
  continuam ligados à geração que receberam;
- containers/entidades vanilla não abertos normalmente preservam IDs e consultam
  o manager ao gerar, mas mods podem reter instâncias e precisam de pesquisa;
- a troca precisa coordenar GLM separado, rollback da referência/maps anteriores
  e eventuais extensões versionadas; não há sincronização cliente vanilla de
  loot data a reutilizar nesta fase.
