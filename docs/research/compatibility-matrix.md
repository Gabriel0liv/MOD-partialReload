# Matriz de compatibilidade inicial

| Domínio | Detecção | Preparação fase 2 | Status | Dependência/risco principal |
|---|---|---|---|---|
| functions | Sim | Sim, sem publicação | `PREPARE_SUPPORTED` | geração candidata compilada com dispatcher real; commit/tick/load permanecem bloqueados |
| advancements | Sim | Não | `SUPPORTED_READ_ONLY` | estado por jogador e packets |
| predicates | Sim | Sim, no candidato conjunto | `PREPARE_SUPPORTED` | resolver/validator compartilhado; commit bloqueado |
| recipes | Sim | Não | `SUPPORTED_READ_ONLY` | tags/conditions/sync/addons |
| loot tables | Sim | Sim, no candidato conjunto | `PREPARE_SUPPORTED` | GLM separado; hooks externos reportados |
| item modifiers | Sim | Sim, no candidato conjunto | `PREPARE_SUPPORTED` | resolver/validator compartilhado |
| Forge GLM | Detecção própria | Não | `PLANNED` | ADR-007: listener/codec/estado ativo separados |
| tags | Sim | Não | `SUPPORTED_READ_ONLY` | bind de registries, caches, packets/events |
| Origins/Apoli | Sim | Não | `PLANNED` | Calio registries + migração/sync |
| KubeJS | Scripts fora de ResourceManager não entram no scanner vanilla; recursos data entram como `UNKNOWN`/categoria estrutural | Não | `PLANNED` | lifecycle completo, Mixins, addons |
| Silent Gear | Sim para materials/traits | Não | `PLANNED` | managers, login packets, NBT/caches |
| worldgen/damage type | Sim | Não | `RESTART_REQUIRED` | registries dinâmicos e mundo existente |
| desconhecido | Sim, quando encontrado dentro dos roots observados | Não | `UNKNOWN` | contrato inexistente; descoberta de novos roots será parte da SPI futura |
| Java/JAR/Mixin/startup scripts | Fora do scanner datapack | Não | `RESTART_REQUIRED` | código/registries estáticos |

## Estratégias futuras de integração opcional

1. `compileOnly` + detecção de mod/runtime opcional: preferida quando há API binária pública e estável por versão.
2. Módulos separados: preferidos quando loaders e tipos diferem materialmente por mod/versão.
3. Integração reflexiva ou Mixin versionada: último recurso, exigindo alvo exato, ADR, teste e fallback seguro.

Não haverá reflection genérica. A ausência de provider é válida e deve aparecer em status, sem impedir a fundação vanilla read-only.

## Roadmap preliminar

1. core read-only, scanner, diff e planos;
2. preparação passiva de functions (Spec 009);
3. preparação conjunta passiva de loot/predicates/item modifiers (Spec 010);
4. recipes vanilla e sincronização;
5. KubeJS recipes/server scripts;
6. commit transacional de functions, loot data ou preparação de recipes, conforme nova spec;
7. Origins powers e migração de estado;
8. origins, layers e global power sets;
9. tags;
10. Silent Gear;
11. advancements completos.

Functions e o grafo conjunto de loot foram aprovados somente para preparação
passiva. GLM permanece separado. Nenhum manager ativo é substituído. A coluna
“preparação” descreve elegibilidade técnica do candidato, não suporte a
`apply`/commit.

## Atualização da aceitação dedicada

No Forge 47.4.10/Minecraft 1.20.1, o commit vanilla de functions foi validado
com ticks, schedules por ID/tag e fingerprints dos managers laterais. Schedules
resolvem os IDs/tags no disparo; targets removidos são ignorados sem crash.
Loot, recipes e advancements permaneceram sem mutação.
