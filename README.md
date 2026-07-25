# Partial Reload

Framework estritamente server-side para reloads parciais seguros, categorizados
e transacionais em servidores Forge 1.20.1.

Versão atual: `0.2.0-SNAPSHOT` — preparação de recipes adicionada; commit de functions vanilla suportado no alvo
exato Forge 47.4.10.

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
- snapshot/classificação read-only de scripts KubeJS e diagnóstico fechado.
- preparação read-only de tags gerais por registry, com stack de datapacks,
  `replace`, entries opcionais, nested tags, grafo e delta imutável.
- preparação conjunta read-only de tags + recipes com snapshot compartilhado,
  resolução candidata, revalidação cross-provider, grafo e delta combinados.

## Não implementado

- commit de loot/predicates/item modifiers;
- commit de tags, recipes ou do artefato conjunto tags + recipes;
- commit ou sincronização de recipes;
- políticas de load diferentes de `DO_NOT_RUN`;
- rollback após restart ou histórico de gerações;
- Global Loot Modifiers (provider separado, planejado);
- integrações KubeJS, Origins e Silent Gear.
- execução de handlers KubeJS e candidato combinado de recipes (**bloqueado** sem runtime Forge 1.20.1 exato);

O commit transacional de functions vanilla foi validado em servidor dedicado
headless no alvo exato Forge 47.4.10. Loot continua apenas em preparação.
Recipes agora possuem preparação read-only completa (serializers reais,
conditions, índices, dependências e delta); commit/sync de recipes continuam
não implementados.

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
/partialreload transaction
/partialreload rollback functions
/partialreload active functions
```

`reload` continua bloqueado. `apply prepared` aceita somente
`PreparedFunctions`; candidatos de loot continuam rejeitados.
Quando KubeJS não está presente na versão alvo, `prepared` informa
explicitamente `KubeJS integration: not loaded` e mantém apenas o baseline
vanilla/Forge.
KubeJS recipe preparation status: `KUBEJS_RECIPE_PREPARATION_BLOCKED`. Nenhum
handler foi executado e nenhuma aceitação com runtime alvo foi realizada.

Tags gerais possuem preparação read-only; binding, sincronização e commit ainda
não são implementados.
Quando tags e recipes são preparadas juntas, `PreparedTagsAndRecipes` é
atômico e `apply prepared` permanece recusado.

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
python scripts/run-dedicated-kubejs-recipe-acceptance.py
python scripts/run-all-acceptance.py
```

O teste dedicado usa RCON temporário em `127.0.0.1`, com senha/porta efêmeras,
backup e restauração de `run/server.properties`. O servidor chegou a `Done`,
publicou a geração B, voltou à geração A por rollback e encerrou normalmente.
O relatório também cobre deltas de tick, schedules por ID/tag, target removido
e fingerprints dos managers laterais em ambiente userdev.
Os harnesses controlam o PID do wrapper Gradle e sua árvore (`taskkill /PID /T`)
somente em caso de timeout, aguardam a thread de captura e removem apenas locks
stale do mundo userdev quando nenhum processo próprio está vivo. O runner
consolidado executa as suítes sequencialmente e grava
`build/reports/all-acceptance.json`.

O projeto segue Spec-Driven Development. Leia `AGENTS.md`,
`docs/specs/010-loot-data-prepare.md` e as ADRs antes de alterar
comportamento.
