# ADR-036 — risco de listeners externos

`TagsUpdatedEvent` é emitido porque é parte do contrato vanilla/Forge, mas o
mod não promete reverter efeitos arbitrários de listeners de terceiros. A
Fase 4E declara o estado como `SERVER_ONLY_NO_PLAYERS`, verifica apenas o core
state (bindings, recipes, Ingredient e managers) e mantém side effects
externos como risco documentado. Suporte amplo exige uma fase de compatibilidade
por mod e testes próprios.
