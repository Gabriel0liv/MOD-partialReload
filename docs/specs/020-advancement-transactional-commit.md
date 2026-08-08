# Spec 020 — commit transacional de advancements

## Estado

`IMPLEMENTED_AND_ACCEPTED`

## Objetivo

Preparar e publicar a visão completa de `data/*/advancements/*.json` no
`ServerAdvancementManager` ativo, preservando a identidade do manager e
revinculando jogadores conectados pelo fluxo vanilla. Adições, remoções,
critérios, requirements, rewards e relações parent/child passam a valer no
mesmo safe point. Não há packet próprio nem relog.

## Runtime e layout autorizados

O contrato é específico para Minecraft 1.20.1, Forge 47.4.10 e mappings
oficiais. `ServerAdvancementManager` possui o campo `advancements` do tipo
`AdvancementList`; `getAdvancement` e `getAllAdvancements` leem essa lista. O
`apply` vanilla desserializa com `Advancement.Builder.fromJson`,
`DeserializationContext` ligado ao `LootDataManager` ativo e o contexto Forge,
constrói uma lista completa, resolve parents e executa `TreeNodePosition`.

`PlayerAdvancements.reload` remove listeners, limpa os maps/sets, marca
`isFirstPacket`, limpa a aba, relê o arquivo de progresso, chama
`checkForAutomaticTriggers` e registra listeners. `flushDirty` envia
`ClientboundUpdateAdvancementsPacket`; `setSelectedTab` usa
`ClientboundSelectAdvancementsTabPacket`.

Os bridges não dependem de o `javac` enxergar membros transformados. O AT torna
o contrato mínimo público no runtime e o acesso refletivo tipado usa somente
`getField`/`getMethod`, sem `setAccessible`, validando owner e tipo antes de
qualquer mutação.

## Artefato e dependências

`PreparedAdvancements` é imutável e contém UUID, instante, snapshot de recursos,
map completo, árvore, delta, snapshot das dependências e validação. A preparação
captura identidade e geração exata de loot, recipes, functions,
`RegistryAccess` e tags. Mudanças simultâneas em ADVANCEMENTS e qualquer outra
família mutável falham com `ADVANCEMENT_PREPARATION_DEPENDENCIES_CHANGED`.

O parser é o parser real do runtime. Qualquer JSON, parent, ciclo, requirement,
trigger, registry ou reward inválido torna o artefato não aplicável. A stack de
packs registra vencedor, sobrescritos e restauração de pack inferior.

## Invariantes

- a identidade de servidor, resources e `ServerAdvancementManager` não muda;
- a candidata é completa e não é patch sobre a lista ativa;
- IDs e referências ativas são comparados exatamente; digest é diagnóstico;
- o commit roda somente em `ServerTickEvent.END` na server thread;
- nenhum tick ocorre entre `stopListening` e o rebind final;
- progresso de critérios com mesmo nome é preservado por ID e data;
- critérios removidos somem e novos começam pendentes;
- rewards já concluídas não são executadas novamente;
- aba selecionada é remapeada por ID ou limpa;
- clientes recebem somente packets vanilla;
- rollback manual não existe.

## Segurança de rewards automáticas

O runtime concede rewards durante `checkForAutomaticTriggers` para todo
advancement sem critérios. Como XP, loot, recipes e functions não são
compensáveis com segurança, um candidato automático com reward não vazia é
bloqueado antes da mutação por
`ADVANCEMENT_AUTOMATIC_REWARD_NOT_TRANSACTION_SAFE`. Advancements automáticos
sem reward podem ser revinculados pelo caminho vanilla.

## Transação

Estados: PREPARING, READY, QUIESCING, COMMITTING, REBINDING_PLAYERS,
SYNCING_CLIENTS, VERIFYING, SUCCESS, ROLLING_BACK, ROLLED_BACK, FAILED e
DEGRADED.

No preflight inicial e novamente no safe point são validados: artefato,
snapshot, manager, geração ativa exata, dependências, árvore, estado global,
arquivos dos jogadores e ausência de reward automática insegura.

Ordem crítica:

1. capturar jogadores do safe point;
2. salvar progresso vanilla e capturar arquivo/estado lógico/aba;
3. parar listeners;
4. publicar e verificar a lista candidata;
5. recarregar cada `PlayerAdvancements` contra o manager ativo;
6. remapear aba, recalcular visibilidade e enviar reset vanilla;
7. verificar manager, progresso e conexões;
8. concluir SUCCESS.

Falha depois da publicação restaura a lista anterior, os bytes de progresso,
recarrega jogadores contra a geração antiga e reenvia o reset vanilla. Falha
nessa restauração termina em `ADVANCEMENT_TRANSACTION_DEGRADED`.

## Fault injection

Userdev-only e one-shot: BEFORE_MANAGER_PUBLICATION,
AFTER_MANAGER_PUBLICATION, DURING_MANAGER_VERIFICATION,
BEFORE_PLAYER_REBIND, AFTER_FIRST_PLAYER_REBIND, DURING_CLIENT_SYNC,
DURING_ROLLBACK_MANAGER e DURING_ROLLBACK_PLAYERS.

## Critérios de aceitação

Testes unitários e GameTests cobrem parser, árvore, delta, dependências,
publicação, progresso, aba, rewards, dois jogadores, faults e rollback. Uma
acceptance com servidor e cliente Forge real sem o mod principal comprova
árvore A→B, progresso, reward não repetida, sync vanilla e rollback. O runner,
clean build e inspeção do JAR precisam passar antes de promover o provider a
`COMMIT_SUPPORTED`.

Gate final de 2026-08-08: 92 testes Java e 172 testes Python passaram; o
servidor executou 116/116 GameTests, incluindo 32/32 da Fase 4I; a acceptance
dedicada comprovou commit, rollback, progresso, aba, ausência de reward
duplicada, dois jogadores revinculados e terceiro jogador na geração atual; o
runner consolidado aprovou 12 suítes. O provider foi então promovido a
`COMMIT_SUPPORTED`.

## Fora do escopo

Rollback manual, packets próprios, KubeJS, Origins, Silent Gear, registries
dinâmicos, reload global, `reloadResources`, histórico persistente e suporte
Arclight não executado.
