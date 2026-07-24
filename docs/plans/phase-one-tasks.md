# Plano de tarefas — Fase 1

## Documentação

- [x] confirmar estado inicial;
- [x] inventariar MineDev;
- [x] pesquisar Forge 47.4.10/Minecraft 1.20.1;
- [x] pesquisar KubeJS 2001.6.5;
- [x] pesquisar Origins/Apoli/Calio Forge 1.20.1;
- [x] pesquisar Silent Gear 1.20.x;
- [x] criar specs 000–008 e ADR-001–005.

## Implementação

- [x] trocar identidade/metadata e remover MDK;
- [x] criar config validada;
- [x] criar domínio imutável e erros;
- [x] criar provider SPI/registry/provider estrutural;
- [x] criar classifier/scanner/fingerprint/snapshot;
- [x] criar diff e baseline separado;
- [x] criar planner conservador;
- [x] criar state machine/service;
- [x] registrar comandos e stubs seguros;
- [x] adicionar testes unitários e GameTests.

## Verificação

- [x] `gradlew test`;
- [x] `gradlew clean build`;
- [x] `gradlew runGameTestServer`;
- [x] `gradlew runServer` até startup dedicado;
- [x] procurar imports cliente, registros de gameplay e chamadas proibidas;
- [x] atualizar checklist/spec com resultados reais.

## Evidência da execução

- `gradlew clean build`: sucesso em 2026-07-24;
- testes unitários: 13 testes, zero failures/errors/skips;
- `runGameTestServer`: 3 testes obrigatórios passaram; o scan assíncrono enumerou 5.158 recursos em 2 namespaces;
- `runServer`: servidor dedicado Minecraft 1.20.1/Forge 47.4.10 chegou a `Done (7.280s)`;
- auditoria `rg`: nenhuma referência client-side, registro de gameplay, chamada `reloadResources`, Mixin ou Access Transformer em `src/main/java`.

O primeiro GameTest revelou que o prefixo vazio de `ResourceManager.listResources` é inválido em 1.20.1. O scanner foi corrigido para enumerar roots válidos explícitos e a Spec 003 registra a limitação de descoberta fora desses roots.

## Definition of done

Todos os critérios da Spec 008 passam ou uma limitação concreta fica documentada como não concluída. Build ou servidor não testado nunca é descrito como sucesso.

## Próxima spec candidata

`009-functions-and-predicates-prepare.md`: investigar candidato imutável de `ServerFunctionLibrary`, dependência do dispatcher/tags, política de load functions e preparação conjunta dos tipos de loot usados por predicates. Commit continua fora até sincronização/verificação estarem especificadas.
