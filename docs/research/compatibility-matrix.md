# Matriz de compatibilidade inicial

| Domínio | Detecção fase 1 | Apply fase 1 | Status | Dependência/risco principal |
|---|---|---|---|---|
| functions | Sim | Não | `SUPPORTED_READ_ONLY` | compilação, tags tick/load, dispatcher |
| advancements | Sim | Não | `SUPPORTED_READ_ONLY` | estado por jogador e packets |
| predicates | Sim | Não | `SUPPORTED_READ_ONLY` | LootDataManager compartilhado |
| recipes | Sim | Não | `SUPPORTED_READ_ONLY` | tags/conditions/sync/addons |
| loot tables | Sim | Não | `SUPPORTED_READ_ONLY` | tipos de loot, validação, GLM/hooks |
| item modifiers | Sim | Não | `SUPPORTED_READ_ONLY` | LootDataManager compartilhado |
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
2. functions e predicates;
3. recipes vanilla e sincronização;
4. KubeJS recipes/server scripts;
5. loot e item modifiers;
6. Origins powers e migração de estado;
7. origins, layers e global power sets;
8. tags;
9. Silent Gear;
10. advancements completos.

Functions e predicates são a próxima investigação recomendada: têm superfície menor que recipes/tags e permitem provar prepare/validate sem prometer commit global. A ordem pode mudar por evidência em specs futuras.
