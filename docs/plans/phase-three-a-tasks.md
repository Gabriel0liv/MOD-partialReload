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
- [ ] dedicated com comandos reais;
- [ ] auditorias e `git diff --check`;
- [ ] atualizar documentação apenas com resultados executados.
