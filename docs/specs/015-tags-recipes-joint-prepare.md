# Spec 015 — preparação conjunta de tags e recipes

## 1. Contexto
Tags gerais e recipes são categorias públicas separadas, mas recipes podem
referenciar tags de itens. Esta fase cria somente um candidato conjunto.

## 2. Problema
O provider isolado de recipes bloqueia quando uma tag relevante muda, pois
validar contra bindings ativos produziria uma geração incoerente.

## 3. Objetivos
Compartilhar snapshot, preparar tags antes de recipes, expor resolução
imutável candidata, validar dependências cruzadas e produzir artefato atômico.

## 4. Não objetivos
Binding, commit, sincronização, recipe book, menus, KubeJS, loot, `/reload`,
`reloadResources`, Mixin, Access Transformer novo ou código cliente.

## 5. Terminologia
`CandidateTagResolutionView`, artefato composto, recipe revalidada, serializer
seguro, dependência cross-provider e snapshot compartilhado.

## 6. Requisitos funcionais
RF-015-1 preparar tags e recipes do mesmo snapshot; RF-015-2 resolver tags
candidatas; RF-015-3 revalidar recipes cujo JSON não mudou; RF-015-4 bloquear
serializers/conditions inseguros; RF-015-5 gerar grafo e delta combinados;
RF-015-6 recusar apply preservando o artefato.

## 7. Requisitos não funcionais
Java 17, server-side, imutabilidade, preparação assíncrona, limites
configuráveis, TOCTOU conjunto e nenhuma mutação de manager ou holder.

## 8. Invariantes
Tags precedem recipes; ambos usam exatamente o mesmo `ResourceSnapshot`;
nenhuma recipe usa tags ativas como substituto silencioso; falha em qualquer
subprovider invalida o composto; nenhum subartefato parcial é publicado.

## 9. Modelo de erros
Inclui `JOINT_PREPARATION_SNAPSHOT_MISMATCH`, `RECIPE_CANDIDATE_TAG_MISSING`,
`RECIPE_CANDIDATE_TAG_MEMBER_MISSING`, `RECIPE_SERIALIZER_CANDIDATE_TAGS_UNSUPPORTED`,
`RECIPE_CONDITION_CANDIDATE_TAGS_UNSUPPORTED` e `JOINT_PREPARATION_TIMEOUT`.

## 10. Riscos
Serializers modded podem consultar holders ativos; conditions customizadas
podem capturar registries; Ingredient materializa membros somente em operações
posteriores ao parse.

## 11. Critérios de aceitação
Tag B é visível somente na view candidata; recipe sem mudança de JSON é
revalidada; membro adicionado/removido e nested tags são diagnosticados;
required missing, serializer desconhecido ou condition insegura invalidam o
composto; tags e recipes ativas permanecem iguais; apply é recusado.

## 12. Cenários de teste
Snapshot compartilhado, tag vazia, nested tag, required missing, recipe
revalidada, serializer seguro/inseguro, TOCTOU, atomicidade, discard e apply.

## 13. Decisões pendentes
Classificação detalhada de serializers de mods adicionais e eventual contexto
Forge oficial para conditions dependentes de tags ficam para pesquisa/runtime.

## 14. Relação com outras specs
Evolui 012 e 014; preserva 009–011 e 013; prepara eventual Fase 4E.

## Hardening da aceitação

`recipesImpactedByTagChanges` usa o fechamento transitivo de tags alteradas;
`revalidatedDueToTagChange` exige hash JSON inalterado. `invalidatedByTagChange`
preserva blockers de tag, serializer e condition. Serializers usam tabela
explícita e fallback desconhecido; conditions afetadas são diagnosticadas como
`CONDITION_TAG_BEHAVIOR_UNKNOWN`. Falha de tags termina em `FAILED_SAFE` sem
artefato fictício.
