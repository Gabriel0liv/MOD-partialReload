# Fase 4E-S — matriz de encerramento do safety gate

| Cenário | Fixture A | Fixture B | Fault point | Mutações esperadas | Status | Rollback | Journal | Evidência | Tipo |
|---|---|---|---|---|---|---|---|---|---|
| pré-mutation | tags/recipe A | tags/recipe B | BEFORE_FIRST_TAG_BIND | nenhuma | passed | não mutante | FAILED_SAFE | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| bind parcial | items+blocks A | items+blocks B | AFTER_FIRST_TAG_BIND | primeiro registry | passed | primeiro registry | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| segundo bind | items+blocks A | items+blocks B | BEFORE_SECOND_TAG_BIND | primeiro registry | passed | primeiro registry | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| recipes | recipe count 1 | recipe count 2 | AFTER_RECIPE_PUBLICATION | tags+recipes | passed | completa | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| ingredient | tags/recipe A | tags/recipe B | AFTER_INGREDIENT_INVALIDATION | tags+recipes+cache | passed | completa | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| evento | tags/recipe A | tags/recipe B | AFTER_TAGS_UPDATED_EVENT | tags+recipes+evento | passed | completa | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| rollback impossível | geração A | geração B | DURING_ROLLBACK | parcial | passed | DEGRADED | DEGRADED | dedicated-tags-recipes-safety-acceptance.json + transcript | dedicated |
| rollback verification fault | geração A | geração B | BEFORE_ROLLBACK_VERIFICATION | restaurada antes da verificação | pending | DEGRADED | FAILURE + DEGRADED | harness atualizado, execução pendente | dedicated |
| player request | sem player | com player | — | nenhuma | pendente | não aplicável | FAILED_SAFE | race não automatizada | GameTest |
| player safe point | 0 players | player entre fases | — | nenhuma | pendente | não aplicável | FAILED_SAFE | probe ausente | GameTest |
| tag ausente | missing | dirt | — | tag nova | pendente | missing | ROLLED_BACK | fixture ausente | dedicated |
| tag removida | stone | removida | — | tag removida | pendente | stone | ROLLED_BACK | fixture ausente | dedicated |
| registry não suportado | sem mudança | biome/damage_type | — | nenhuma | pendente | não aplicável | FAILED_SAFE | matriz ausente | unit/dedicated |

O harness `run-dedicated-tags-recipes-safety-acceptance.py` executou os nove
faults recoverable e o cenário isolado `DEGRADED` com RCON, incluindo
restauração de properties e shutdown limpo. Permanecem sem evidência nesta
rodada os cenários de player race, lifecycle de tags ausente/removida,
registries não suportados e GameTests específicos. A promoção
para `JOINT_TAG_RECIPE_TRANSACTIONAL_COMMIT_IMPLEMENTED_SERVER_ONLY` é proibida
até cada linha possuir evidência direta.

Atualização posterior: `ActiveRecipeSnapshot` passou a participar da
verificação estrutural de rollback e o índice de recipes usa
`RecipeManager.getAllRecipesFor(RecipeType)`. Os eventos de tags/recipes usam
tipos explícitos nos call sites de produção. Os cenários de player, lifecycle
de tags ausente/vazia/removida, registries não suportados e GameTests dedicados
continuam pendentes e não foram marcados como aceitos.

Nesta rodada, a verificação por tipo passou a consultar diretamente
`RecipeManager.getAllRecipesFor`, foi introduzido `TagSnapshotUniverse` para
capturar IDs A ∪ B, e foi adicionada a matriz unitária de registries não
suportados. Os grupos dedicados de lifecycle de tags, registries não
suportados e os GameTests end-to-end continuam bloqueados até execução real;
nenhum deles é marcado como passed por inferência.

Execução filtrada de `tag-lifecycle` após recompilação: `EMPTY` passou com
estado candidato `RESOLVED[dirt]` e restauração `EMPTY`. Os cenários de tag
`MISSING` e remoção falharam na verificação direta: em 1.20.1, o binding
vanilla preserva a associação nomeada no mapa de tags quando a entrada não é
fornecida, produzindo `missing tag restored`. Esse comportamento é uma
pendência funcional real, não foi mascarado pelo harness e impede a promoção
do gate. A ordenação dos membros do snapshot foi corrigida antes dessa
execução.
