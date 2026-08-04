# Spec 019 — transação de Global Loot Modifiers

Status: implementação concluída; promoção pendente do gate consolidado final

## Objetivo

Preparar, publicar e reverter a cadeia ordenada de Forge Global Loot Modifiers
(GLM). Quando a mesma captura também altera predicates, item modifiers ou loot
tables, a publicação deve ser uma única transação loot + GLM.

## Runtime suportado

Minecraft 1.20.1, Forge 47.4.10, Java 17 e mappings oficiais. O contrato é
específico para `ForgeInternalHandler` e `LootModifierManager` dessa versão.
Layout incompatível falha com `LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED` antes
de qualquer mutação.

## Descoberta e stack

`GLOBAL_LOOT_MODIFIERS` reconhece o nome administrativo `glm` e o alias
`global_loot_modifiers`. A lista autoritativa é exclusivamente
`data/forge/loot_modifiers/global_loot_modifiers.json`; os candidatos são
`data/<namespace>/loot_modifiers/<path>.json`.

A stack da lista é processada da menor para a maior prioridade. `replace=true`
limpa os IDs acumulados. Cada entrada remove uma ocorrência anterior e é
reinserida no fim. Assim, a última configuração válida determina a posição.
Cada ID final exige arquivo vencedor, `type` registrado, codec válido e
conditions válidas. Qualquer erro torna todo o artefato não aplicável.

## Artefato

`PreparedGlobalLootModifiers` contém preparation ID, instante, snapshot,
ordered IDs, mapa ordenado e imutável de instâncias, delta ordenado e relatório
de validação. O delta distingue `added`, `removed`, `modified`, `moved`,
`restoredFromLowerPack` e `unchanged`.

## Publicação GLM

O commit ocorre no safe point `ServerTickEvent.END`, na server thread. Ele
preserva a identidade do `LootModifierManager`, valida por referência exata a
geração ativa esperada, publica o mapa completo de uma vez e verifica IDs,
ordem, referências e `getAllLootMods()`. Uma geração anterior confirmada é
retida para rollback manual único.

Jogadores conectados não bloqueiam, menus não são fechados, clientes não são
marcados stale e não há packet ou sincronização client-side.

## Acoplamento loot + GLM

Mudanças simultâneas nos três tipos do `LootDataManager` e em GLM exigem
`PreparedLootAndGlobalModifiers`. Os subartefatos compartilham o mesmo snapshot
e preparation ID lógico e não podem ser aplicados separadamente.

No safe point conjunto:

1. validar os dois managers e as duas gerações ativas esperadas;
2. publicar `LootDataManager.elements`;
3. publicar `LootDataManager.typeKeys`;
4. publicar `LootModifierManager.registeredLootModifiers`;
5. verificar os dois managers;
6. concluir como `SUCCESS`.

Rollback conjunto usa ordem inversa: GLM anterior, depois loot anterior. Falha
de restauração ou verificação entra em `LOOT_GLM_TRANSACTION_DEGRADED` e bloqueia
novas mutações até restart.

## Invariantes TOCTOU

Fingerprints e digests são apenas diagnóstico. O safe point compara mesma
instância de cada manager, mesmos IDs, mesma ordem onde aplicável, mesmas
referências por ID, mesmo índice `typeKeys` e mesmo `minecraft:empty`.

## Erros

- `GLM_ENTRY_FILE_MISSING`
- `GLM_TYPE_MISSING`
- `GLM_TYPE_UNKNOWN`
- `GLM_CODEC_ERROR`
- `GLM_REGISTRY_REFERENCE_MISSING`
- `GLM_CONDITION_INVALID`
- `GLM_DECODE_FAILED`
- `GLM_COMMIT_REQUIRES_JOINT_LOOT_TRANSACTION`
- `LOOT_COMMIT_REQUIRES_JOINT_GLM_TRANSACTION`
- `LOOT_MODIFIER_MANAGER_LAYOUT_UNSUPPORTED`
- `GLM_COMMIT_WRONG_THREAD`
- `LOOT_GLM_TRANSACTION_DEGRADED`

## Semântica temporal

Lookups e aplicações iniciados depois do safe point usam integralmente a nova
geração. Trabalho já iniciado pode terminar com a anterior. Itens, drops,
inventários e containers já desempacotados não são alterados retroativamente.
Referências retidas por mods externos também não são reescritas.

## Fora do escopo

LootJS, KubeJS loot handlers, regeneração retroativa, reload global,
`reloadResources`, mixins, troca de managers e sincronização client-side.

## Aceitação

Testes unitários e 24 GameTests da Fase 4H cobrem stack, codec, delta, gerações,
TOCTOU, fault injection e rollback. As acceptances dedicadas comprovaram GLM
isolado e loot + GLM com jogador conectado. A primeira execução consolidada
terminou com `ALL_ACCEPTANCE_PASSED`; a repetição final encontrou identity
mismatch por PID reutilizado numa suíte anterior de loot data. Até novo gate
integral limpo, o provider permanece `PREPARE_SUPPORTED`.
