# Plano de tarefas — Fase 3A

## Gate SDD

- [x] preservar e auditar worktree;
- [x] ler docs, código e testes;
- [x] investigar source JAR exato;
- [x] aprovar ADR-008–011 e Spec 011 antes do código.

## Implementação

- [x] adicionar AT e self-check versionado;
- [x] construir library candidata real;
- [x] criar modelos/journal/coordenador;
- [x] integrar safe point, verify e load suppression;
- [x] promover/restaurar baseline FUNCTIONS;
- [x] integrar rollback automático/manual e DEGRADED;
- [x] adicionar config e comandos.

## Verificação

- [ ] unit tests e fault injection;
- [x] GameTests de commit/rollback/non-mutation;
- [x] `clean build`;
- [x] `runGameTestServer`;
- [x] dedicated com comandos reais via RCON temporário;
- [x] auditorias e `git diff --check`;
- [x] atualizar documentação apenas com resultados executados.

O harness reutilizável é `scripts/run-dedicated-function-acceptance.py` (ou o
wrapper PowerShell). Ele instala fixtures A/B em `run/world/datapacks`, captura
relatório JSON/texto em `build/reports/`, restaura fixtures e `server.properties`
e nunca registra a senha RCON.

O relatório aprovado também contém assertions individuais para ticks,
schedules e fingerprints laterais; `debug manager_fingerprints` não é uma API
de produção.
