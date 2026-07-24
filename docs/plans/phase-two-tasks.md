# Plano de tarefas — Fase 2

## Pesquisa e especificação

- [x] confirmar fase 1 no código e documentos;
- [x] investigar classes mapeadas exatas de functions;
- [x] investigar `LootDataManager`/predicates;
- [x] criar research, Spec 009 e ADR-006;
- [x] decidir `PREDICATES_COUPLED_TO_LOOT`.

## Implementação

- [x] evoluir validação estruturada;
- [x] criar contrato/artefato imutável;
- [x] implementar loader/compilador/tag resolver/grafo;
- [x] implementar provider específico;
- [x] integrar PREPARING/VALIDATING/concorrência/descarte;
- [x] adicionar config e comandos;
- [x] adicionar fixtures, unit tests e GameTests.

## Verificação

- [x] `gradlew clean build` — 24 testes unitários, sem falhas;
- [x] `gradlew runGameTestServer` — 5 GameTests obrigatórios passaram;
- [x] `gradlew runServer` e comandos dedicados — geração válida e inválida
  exercitadas via RCON local temporário; servidor encerrou limpo;
- [x] auditoria automatizada de não mutação e chamadas proibidas;
- [x] atualizar specs/arquitetura/README/AGENTS com resultados reais.

## Definition of done

Todos os critérios da Spec 009 passam ou ficam explicitamente registrados como
não concluídos. Preparação válida nunca será descrita como hot reload.

## Recomendação

Próxima spec recomendada: **Fase 3B — preparação conjunta de
loot/predicates/item modifiers**. Ela preserva o padrão passivo já provado e
resolve a ADR-006. A Fase 3A ainda carece de acesso seguro à library anterior
para rollback, barreira contra `ExecutionContext` ativo, política explícita de
load e testes de troca durante filas/schedules. `replaceLibrary` é público, mas
a referência anterior é privada e não possui getter público.
