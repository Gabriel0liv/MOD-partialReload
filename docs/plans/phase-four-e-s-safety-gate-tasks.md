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
rodada os cenários de player race e GameTests específicos. A promoção
para `JOINT_TAG_RECIPE_TRANSACTIONAL_COMMIT_IMPLEMENTED_SERVER_ONLY` é proibida
até cada linha possuir evidência direta.

Atualização posterior: `ActiveRecipeSnapshot` passou a participar da
verificação estrutural de rollback e o índice de recipes usa
`RecipeManager.getAllRecipesFor(RecipeType)`. Os eventos de tags/recipes usam
tipos explícitos nos call sites de produção. Os cenários de player e os
GameTests dedicados continuam pendentes e não foram marcados como aceitos.

Nesta rodada, a verificação por tipo passou a consultar diretamente
`RecipeManager.getAllRecipesFor`, foi introduzido `TagSnapshotUniverse` para
capturar IDs A ∪ B, e foi adicionada a matriz unitária de registries não
suportados. O grupo dedicado `tag-lifecycle` passou com observação direta de
`MISSING→RESOLVED→MISSING`, `EMPTY→RESOLVED→EMPTY` e
`RESOLVED→MISSING→RESOLVED`. O grupo `unsupported` passou para biome ADD,
damage_type MODIFY e damage_type REMOVE, cada um bloqueado no safe point com
`TAG_REGISTRY_COMMIT_UNSUPPORTED` e sem mutação. Player race e GameTests
end-to-end continuam pendentes; nenhum resultado é inferido.

Foi criado `MappedRegistryTagBridge` com dois caminhos explícitos. A inspeção do
Forge 47.4.10 confirmou que `NamespacedWrapper` estende `MappedRegistry`, mas
declara um campo `tags` próprio; `getTags`, `getTag`, `getOrCreateTag`,
`bindTags` e `resetTags` leem esse campo sombreado. Portanto
`MappedRegistry.tags != NamespacedWrapper.tags` para items/blocks Forge.
O bridge v2 acessa `NamespacedWrapper.tags` no caminho Forge e
`MappedRegistry.tags` somente no caminho vanilla, instala um mapa-semente exato
antes de chamar `bindTags`, esvazia referências `HolderSet.Named` removidas e
verifica o lookup público. O AT correspondente foi processado e o JAR contém
`META-INF/accesstransformer.cfg`.
exposto no Access Transformer. O bridge limpa referências `HolderSet.Named`
omitidas, remove as chaves do índice e usa o mesmo caminho para commit e
rollback. A validação final continua no serviço, incluindo membros e estados.
Após a correção, o grupo `tag-lifecycle` foi executado novamente e passou; o
relatório filtrado registra os estados candidatos e restaurados diretamente.
O primeiro relatório anterior à correção permanece apenas como diagnóstico
histórico da falha no campo sombreado, não como evidência de aceitação.
