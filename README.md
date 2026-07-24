# Partial Reload

Framework estritamente server-side para reloads parciais seguros, categorizados
e transacionais em servidores Forge 1.20.1.

Versão atual: `0.1.0-SNAPSHOT` — modo `PREPARE_ONLY`.

## Implementado

- scan read-only de recursos de datapacks;
- fingerprints SHA-256, snapshots e diff;
- classificação por categorias públicas e planejamento;
- preparação e validação de functions com o dispatcher real do servidor;
- merge e resolução de function tags, incluindo `minecraft:tick` e
  `minecraft:load`;
- grafo de dependências e artefato preparado imutável;
- detecção de timeout, limites, concorrência e mudança durante preparação.

## Não implementado

- commit ou troca do manager/library ativo;
- execução de functions preparadas ou de load;
- alteração da lista ativa de tick;
- sincronização;
- rollback;
- preparação isolada de predicates (acoplados a loot);
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
/partialreload prepared
/partialreload discard
```

`apply`, `reload` e `rollback` recusam a operação sem executar fallback.

## Desenvolvimento

Requisitos: Java 17 e PowerShell/Gradle Wrapper.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runGameTestServer
.\gradlew.bat runServer
```

O projeto segue Spec-Driven Development. Leia `AGENTS.md`,
`docs/specs/009-functions-and-predicates-prepare.md` e as ADRs antes de alterar
comportamento.
