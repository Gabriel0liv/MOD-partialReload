# Matriz de compatibilidade inicial

| Domínio | Detecção | Preparação fase 2 | Status | Dependência/risco principal |
|---|---|---|---|---|
| functions | Sim | Sim, sem publicação | `PREPARE_SUPPORTED` | geração candidata compilada com dispatcher real; commit/tick/load permanecem bloqueados |
| advancements | Sim | Não | `SUPPORTED_READ_ONLY` | estado por jogador e packets |
| predicates | Sim | Sim, no candidato conjunto | `PREPARE_SUPPORTED` | resolver/validator compartilhado; commit bloqueado |
| recipes | Sim | Sim, isolada e conjunta com tags candidatas | `PREPARE_SUPPORTED` | serializers/conditions desconhecidos, sync/addons |
| loot tables | Sim | Sim, no candidato conjunto | `PREPARE_SUPPORTED` | GLM separado; hooks externos reportados |
| item modifiers | Sim | Sim, no candidato conjunto | `PREPARE_SUPPORTED` | resolver/validator compartilhado |
| Forge GLM | Detecção própria | Não | `PLANNED` | ADR-007: listener/codec/estado ativo separados |
| tags | Sim | Sim, isolada e conjunta com recipes | `PREPARE_SUPPORTED` | bind de registries, caches, packets/events |
| Origins/Apoli | Sim | Não | `PLANNED` | Calio registries + migração/sync |
| KubeJS | Scripts fora de ResourceManager não entram no scanner vanilla; recursos data entram como `UNKNOWN`/categoria estrutural | Não | `KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE` | runtime Forge 2001.6.5-build.26 auditado; singleton, listeners e bindings globais impedem staging isolado |
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
9. preparação read-only de tags gerais (Spec 014);
10. Silent Gear;
11. advancements completos.

Functions e o grafo conjunto de loot foram aprovados somente para preparação
passiva. GLM permanece separado. Nenhum manager ativo é substituído. A coluna
“preparação” descreve elegibilidade técnica do candidato, não suporte a
`apply`/commit.

RecipeManager 1.20.1: `RecipeManager.fromJson` e serializers reais foram
validados; a aceitação dedicada preparou 1175 recipes, 23 tipos e 7 serializers,
recusou apply e preservou o manager ativo. KubeJS permanece separado.

KubeJS: o único artefato local é `kubejs-neoforge 2101.7.2-build.277` com
Rhino `2101.2.7-build.77`, exigindo NeoForge 21.1.199+. É incompatível com
Forge 47.4.10/Minecraft 1.20.1; integração KubeJS permanece `RESEARCH_ONLY`.

## Atualização da aceitação dedicada

No Forge 47.4.10/Minecraft 1.20.1, o commit vanilla de functions foi validado
com ticks, schedules por ID/tag e fingerprints dos managers laterais. Schedules
resolvem os IDs/tags no disparo; targets removidos são ignorados sem crash.
Loot, recipes e advancements permaneceram sem mutação.

## Fase 4E — commit conjunto server-only

`Registry.bindTags`, `RecipeManager.replaceRecipes`, `Ingredient.invalidateAll`
e `TagsUpdatedEvent` foram exercitados no Forge 47.4.10 sem players. O harness
`run-dedicated-tags-recipes-commit-acceptance.py` observou a tag A como
`minecraft:stone`, publicou B como `minecraft:dirt` em `SUCCESS`, preservou as
identidades de LootDataManager, RecipeManager e AdvancementManager e restaurou
A em `ROLLED_BACK`. Registries fora da allowlist estática permanecem fora da
publicação e alterações custom nesses registries falham fechado. Client sync,
menus e players continuam não suportados.

O self-check inclui versão MC/Forge, identidades de RecipeManager,
RegistryAccess e registries allowlisted. O safe point repete esse preflight e
recaptura hashes de tags/recipes para detectar TOCTOU sem depender de novo scan.
A aceitação dedicada básica passou, mas fault injection, race de player e
restauração de registry inicialmente vazio ainda são cobertura pendente; o
estado permanece `PENDING_SAFETY_HARDENING`.

Consequentemente, a Fase 4F não pode iniciar sobre esta base: não há ainda
aceitação de `DEGRADED`, player race ou cliente Forge real.
