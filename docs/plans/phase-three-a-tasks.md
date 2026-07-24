# Plano de tarefas — Fase 3A

## Gate SDD

- [x] preservar e auditar worktree;
- [x] ler docs, código e testes;
- [x] investigar source JAR exato;
- [x] aprovar ADR-008–011 e Spec 011 antes do código.

## Implementação

- [ ] adicionar AT e self-check versionado;
- [ ] construir library candidata real;
- [ ] criar modelos/journal/coordenador;
- [ ] integrar safe point, verify e load suppression;
- [ ] promover/restaurar baseline FUNCTIONS;
- [ ] integrar rollback automático/manual e DEGRADED;
- [ ] adicionar config e comandos.

## Verificação

- [ ] unit tests e fault injection;
- [ ] GameTests de commit/rollback/non-mutation/chains/schedules;
- [ ] `clean build`;
- [ ] `runGameTestServer`;
- [ ] dedicated com comandos reais;
- [ ] auditorias e `git diff --check`;
- [ ] atualizar documentação apenas com resultados executados.

