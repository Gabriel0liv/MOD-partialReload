# ADR-007 — Acoplamento de Global Loot Modifiers

Status: Aceito — 2026-07-24

## Contexto

Forge 47.4.10 registra `LootModifierManager` como reload listener separado.
Seus recursos, codecs, ordenação e estado ativo não pertencem ao
`LootDataManager`, embora suas conditions reutilizem serializers de loot e sua
execução ocorra depois da geração de uma tabela.

## Decisão

Selecionar `GLM_SEPARATE_PROVIDER`.

`VanillaLootDataProvider` prepara conjuntamente apenas `PREDICATES`,
`ITEM_MODIFIERS` e `LOOT`. Ele informa `GLM_NOT_INCLUDED` quando encontra
configuração ou recursos GLM. Um provider interno futuro poderá preparar GLM e
declarar dependência/atomicidade com o provider de loot.

## Consequências

- a Spec 010 não executa, publica ou afirma validar GLM;
- GLM permanece `PLANNED`;
- o plano administrativo conserva a categoria pública `LOOT`;
- um commit futuro não poderá trocar loot data ignorando a consistência dos GLM;
- recursos de loaders externos semelhantes são reportados, não assumidos.

## Alternativas rejeitadas

- `GLM_PREPARE_WITH_LOOT`: misturaria listeners, formatos e estados ativos sem
  contrato/testes de atomicidade.
- `GLM_RESTART_REQUIRED`: o listener Forge demonstra reload normal; falta
  pesquisa de preparação/commit, não uma impossibilidade comprovada.
- `GLM_RESEARCH_INCONCLUSIVE`: a separação arquitetural foi confirmada no código
  da versão exata.

