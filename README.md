# Partial Reload

Framework estritamente server-side para reloads parciais seguros, categorizados
e transacionais em servidores Forge 1.20.1.

Versão atual: `0.3.0-SNAPSHOT` — commits transacionais de functions,
tags+recipes, loot data, GLM e do bundle conjunto loot+GLM no Forge 47.4.10.

A fundação opcional de handshake client-side da Fase 4F-A foi aceita: o canal `partialreload:client_sync` é client-optional, a presença é iniciada pelo cliente, clientes sem o mod continuam entrando, clientes com o mod entram em servidor sem Partial Reload, e reconnects/timeout SILENT foram validados. A Fase 4F-R acrescenta um commit opt-in com refresh do cliente adiado até o relog: o servidor muda imediatamente, fecha menus abertos e mantém os jogadores online, mas recipe book, JEI/REI e informação visual podem permanecer antigos. Não há payloads de tags/recipes, ACK transacional, live sync, quiescência ou rollback distribuído.

## Implementado

- scan read-only de recursos de datapacks;
- fingerprints SHA-256, snapshots e diff;
- classificação por categorias públicas e planejamento;
- preparação e validação de functions com o dispatcher real do servidor;
- merge e resolução de function tags, incluindo `minecraft:tick` e
  `minecraft:load`;
- grafo de dependências e artefato preparado imutável;
- detecção de timeout, limites, concorrência e mudança durante preparação.
- preparação conjunta de predicates, item modifiers e loot tables;
- parsers/registries reais, resolver candidato e validator do `LootDataManager`;
- stack de datapacks, grafo de loot, deltas e restauração de pack inferior.
- commit transacional de functions vanilla em safe point da server thread;
- supressão `DO_NOT_RUN` de load functions, verificação e rollback em memória.
- preparação read-only de recipes com serializers reais, conditions, índices,
  dependências e delta.
- snapshot/classificação read-only de scripts KubeJS e auditoria fechada do
  runtime Forge 2001.6.5.
- preparação read-only de tags gerais por registry, com stack de datapacks,
  `replace`, entries opcionais, nested tags, grafo e delta imutável.
- preparação conjunta read-only de tags + recipes com snapshot compartilhado,
  resolução candidata, revalidação cross-provider, grafo e delta combinados.
- commit transacional conjunto server-only de tags + recipes sem jogadores,
  com bind por registry, publicação completa de recipes, invalidação de
  Ingredient, evento de tags, verificação e rollback em memória; o caminho
  safety gate 4E-S aprovado após a matriz completa, GameTests e acceptance
  dedicada;
- fundação opcional client-side 4F-A aceita por evidência funcional com handshake compatível, cliente ausente, servidor Forge independente sem Partial Reload, reconnects, SILENT, acceptance composta e cleanup fail-closed.
- modo opt-in `SERVER_COMMIT_DEFERRED_CLIENT_REFRESH`: commit imediato no servidor com jogadores conectados, fechamento fail-closed de menus, rastreamento de clientes stale e atualização pelo fluxo normal de relog.
- commit transacional conjunto de predicates, item modifiers e loot tables,
  preservando a identidade do `LootDataManager`, permitindo jogadores
  conectados e retendo uma geração para rollback.
- commit transacional de GLM ordenado e commit conjunto obrigatório loot+GLM,
  preservando ambos os managers, permitindo jogadores conectados e retendo uma
  geração anterior para rollback.

## Não implementado

- sincronização live de recipes/tags, atualização de recipe book/viewers durante a sessão ou rollback de clientes;
- políticas de load diferentes de `DO_NOT_RUN`;
- rollback após restart ou histórico de gerações;
- integrações KubeJS, Origins e Silent Gear.
- execução de handlers KubeJS e candidato combinado de recipes (**bloqueado**:
  o runtime 2001.6.5 não oferece staging isolável);

Os commits transacionais de functions e loot data foram validados em servidor
dedicado headless no alvo exato Forge 47.4.10.
Recipes agora possuem preparação completa para o contrato server-only
(serializers reais, conditions, índices, dependências e delta); sincronização
de clientes permanece não implementada.

## Comandos

Requerem nível de operador configurável (padrão 4):

```mcfunction
/partialreload status
/partialreload categories
/partialreload providers
/partialreload scan
/partialreload changed
/partialreload plan changed
/partialreload plan functions
/partialreload prepare changed
/partialreload prepare functions
/partialreload prepare recipes
/partialreload prepare tags
/partialreload prepare tags_recipes
/partialreload prepare predicates
/partialreload prepare item_modifiers
/partialreload prepare loot
/partialreload prepared
/partialreload discard
/partialreload apply prepared
/partialreload apply prepared deferred
/partialreload transaction
/partialreload rollback functions
/partialreload rollback loot
/partialreload active functions
```

`reload` continua bloqueado. `apply prepared` publica `PreparedFunctions` ou
`PreparedTagsAndRecipes` ou `PreparedLootData` conforme seus contratos. O subcomando `deferred` é
exclusivo de tags + recipes, mantém os jogadores online e exige relog para o
refresh visual. Loot data publica os três tipos juntos, aceita jogadores
conectados e não requer client sync.
Quando KubeJS não está presente, `prepared` mantém apenas o baseline
vanilla/Forge. A Fase 4J auditou KubeJS Forge `2001.6.5-build.26`, Rhino Forge
`2001.2.2-build.17` e Architectury Forge `9.1.12`. O lifecycle usa manager,
listeners e bindings globais e não oferece um registry de staging injetável.
Estado: `KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`; nenhum handler foi executado,
nenhum adapter/candidato/commit foi criado e a dependência continua opcional.

Tags gerais possuem preparação read-only; binding, sincronização e commit ainda
não são implementados.
Quando tags e recipes são preparadas juntas, `PreparedTagsAndRecipes` é
atômico no servidor. O comando normal exige zero jogadores; o modo `deferred`
é opt-in e não fornece atomicidade distribuída nem live sync.

Na preparação conjunta, recipes só são revalidadas quando dependem
direta/transitivamente de tags realmente alteradas e o hash do JSON permanece
igual. A saída `prepared` separa recipes que usam tags, impactadas,
revalidadas e invalidadas; serializers e conditions recebem classificação
conservadora. Uma falha de tags não publica subartefatos parciais.

Em userdev, os comandos read-only
`/partialreload debug prepared_tag`, `active_tag`, `prepared_recipe` e
`active_recipe` fornecem evidência direta para a aceitação; eles não são
registrados como API de produção.

## Desenvolvimento

Requisitos: Java 17 e PowerShell/Gradle Wrapper.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runGameTestServer
.\gradlew.bat runServer
python scripts/run-dedicated-function-acceptance.py
python scripts/run-dedicated-recipe-acceptance.py
python scripts/run-dedicated-tag-acceptance.py
python scripts/run-dedicated-tags-recipes-acceptance.py
python scripts/run-dedicated-tags-recipes-commit-acceptance.py
python scripts/run-dedicated-loot-data-commit-acceptance.py
python scripts/run-dedicated-glm-commit-acceptance.py
python scripts/run-dedicated-loot-glm-joint-acceptance.py
python scripts/run-dedicated-kubejs-recipe-acceptance.py
python scripts/run-all-acceptance.py
```

O teste dedicado de functions e o harness conjunto usam RCON temporário em
`127.0.0.1`, com senha/porta efêmeras,
backup e restauração de `run/server.properties`. O servidor chegou a `Done`,
publicou a geração B, voltou à geração A por rollback e encerrou normalmente.
O relatório também cobre deltas de tick, schedules por ID/tag, target removido
e fingerprints dos managers laterais em ambiente userdev.
Schedules são testados de forma determinística: B é preparada enquanto A está
ativa, os callbacks são agendados imediatamente antes de `apply`, a transação
chega a `SUCCESS` antes do polling dos callbacks e a fila vanilla resolve IDs e
tags no momento do disparo. Targets removidos tornam-se no-op sem crash.
Os harnesses controlam o PID do wrapper Gradle e sua árvore (`taskkill /PID /T`)
somente em caso de timeout, aguardam a thread de captura e removem apenas locks
stale do mundo userdev quando nenhum processo próprio está vivo. O runner
consolidado executa as suítes sequencialmente e grava
`build/reports/all-acceptance.json`. O relatório específico do commit conjunto
fica em `build/reports/dedicated-tags-recipes-commit-acceptance.json` e comprova
`SUCCESS`, troca da tag candidata, `ROLLED_BACK`, e preservação das identidades
de LootDataManager, RecipeManager e AdvancementManager.

O projeto segue Spec-Driven Development. Leia `AGENTS.md`,
`docs/specs/010-loot-data-prepare.md` e as ADRs antes de alterar
comportamento.

## Advancements transacionais (Fase 4I)

Advancements são preparados como uma geração completa e publicados no
`ServerAdvancementManager` ativo no safe point. Jogadores conectados são
revinculados pelo fluxo vanilla: progresso e aba compatíveis são preservados,
rewards não são repetidas e somente packets vanilla são enviados. Falha após a
publicação executa rollback automático; rollback manual não é suportado.

A fase foi promovida após 92 testes Java, 172 testes Python, 116/116 GameTests
(32/32 da 4I), acceptance dedicada e runner consolidado com 12 suítes. O
provider `VanillaAdvancementProvider` é `COMMIT_SUPPORTED`. Não há packet
customizado de advancement nem compatibilidade Arclight declarada.

A acceptance de loot data mantém um cliente real sem o mod principal online,
publica uma geração completa nova, prova comportamento determinístico pelos
comandos vanilla de predicate/item/loot, remove IDs e executa rollback manual.
Itens já gerados não são reescritos e referências externas previamente retidas
podem continuar apontando para objetos antigos. A Fase 4H mantém GLMs num
manager separado, exige transação conjunta quando loot data também mudou e foi
promovida após 84/84 GameTests, acceptances GLM, conjunta e 4G, runner
consolidado com 11 suítes e clean build.

## Safety gate 4E-S

Encerrado em 2026-07-27: 24 GameTests passaram, a safety acceptance completa
aprovou os seis grupos e o runner consolidado aprovou as sete suítes com
`ALL_ACCEPTANCE_PASSED`. A sincronização client-side permanece não
implementada; a Spec 017 está pronta para implementação futura.
