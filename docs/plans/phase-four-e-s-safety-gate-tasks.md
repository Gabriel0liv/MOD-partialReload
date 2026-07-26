# Fase 4E-S — matriz de encerramento do safety gate

| Cenário | Fixture A | Fixture B | Fault point | Mutações esperadas | Status | Rollback | Journal | Evidência | Tipo |
|---|---|---|---|---|---|---|---|---|---|
| pré-mutation | tags/recipe A | tags/recipe B | BEFORE_FIRST_TAG_BIND | nenhuma | passed | não mutante | FAILED_SAFE | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| bind parcial | items+blocks A | items+blocks B | AFTER_FIRST_TAG_BIND | primeiro registry | passed | primeiro registry | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| segundo bind | items+blocks A | items+blocks B | BEFORE_SECOND_TAG_BIND | primeiro registry | passed | primeiro registry | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| recipes | recipe count 1 | recipe count 2 | AFTER_RECIPE_PUBLICATION | tags+recipes | passed | completa | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| ingredient | tags/recipe A | tags/recipe B | AFTER_INGREDIENT_INVALIDATION | tags+recipes+cache | passed | completa | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| evento | tags/recipe A | tags/recipe B | AFTER_TAGS_UPDATED_EVENT | tags+recipes+evento | passed | completa | ROLLED_BACK | dedicated-tags-recipes-safety-acceptance.json | dedicated |
| rollback impossível | geração A | geração B | DURING_ROLLBACK | parcial | pending | DEGRADED | DEGRADED | sequência adicionada, execução pendente | dedicated |
| player request | sem player | com player | — | nenhuma | pendente | não aplicável | FAILED_SAFE | race não automatizada | GameTest |
| player safe point | 0 players | player entre fases | — | nenhuma | pendente | não aplicável | FAILED_SAFE | probe ausente | GameTest |
| tag ausente | missing | dirt | — | tag nova | pendente | missing | ROLLED_BACK | fixture ausente | dedicated |
| tag removida | stone | removida | — | tag removida | pendente | stone | ROLLED_BACK | fixture ausente | dedicated |
| registry não suportado | sem mudança | biome/damage_type | — | nenhuma | pendente | não aplicável | FAILED_SAFE | matriz ausente | unit/dedicated |

O harness `run-dedicated-tags-recipes-safety-acceptance.py` falha fechado e
produz relatório `blocked` até que a execução real seja implementada. Os hooks
existem e são userdev-only, mas a matriz não está fechada. A promoção
para `JOINT_TAG_RECIPE_TRANSACTIONAL_COMMIT_IMPLEMENTED_SERVER_ONLY` é proibida
até cada linha possuir evidência direta.
