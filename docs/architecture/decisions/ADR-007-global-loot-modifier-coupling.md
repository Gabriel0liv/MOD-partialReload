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
`ITEM_MODIFIERS` e `LOOT`. A preparação 3B informa `GLM_NOT_INCLUDED` porque
esse artefato não inclui GLM. A Fase 4H fornece o provider separado que prepara e
declarar dependência/atomicidade com o provider de loot.

## Consequências

- a Spec 010 não executa, publica ou afirma validar GLM;
- GLM permanece fora do artefato `PreparedLootData`;
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

## Evolução aceita na Fase 4H

A Spec 019 mantém o manager e o provider separados, mas acrescenta publicação
transacional. GLM isolado pode ser aplicado isoladamente; uma captura que altera
loot data e GLM exige transação conjunta entre os dois managers. A ordem dos
modifiers integra a geração e a identidade dos dois managers é preservada.
