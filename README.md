# Partial Reload

Framework estritamente server-side para reloads parciais seguros, categorizados
e transacionais em servidores Forge 1.20.1.

Versão atual: `0.1.0-SNAPSHOT` — commit de functions vanilla suportado no alvo
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

## Não implementado

- commit de loot/predicates/item modifiers;
- políticas de load diferentes de `DO_NOT_RUN`;
- rollback após restart ou histórico de gerações;
- Global Loot Modifiers (provider separado, planejado);
- integrações KubeJS, Origins e Silent Gear.

Isso ainda não é hot reload funcional. Uma preparação válida apenas demonstra
que a geração candidata compilou e foi validada; o servidor continua usando a
geração ativa anterior.

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

## Desenvolvimento

Requisitos: Java 17 e PowerShell/Gradle Wrapper.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runGameTestServer
.\gradlew.bat runServer
```

O projeto segue Spec-Driven Development. Leia `AGENTS.md`,
`docs/specs/010-loot-data-prepare.md` e as ADRs antes de alterar
comportamento.
