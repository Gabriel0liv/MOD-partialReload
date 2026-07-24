# Spec 010 — Preparação conjunta de loot data

## 1. Contexto

As fases anteriores implementaram observação, planejamento e preparação passiva
de functions. A ADR-006 adiou predicates porque Minecraft 1.20.1 os prepara no
mesmo `LootDataManager` que item modifiers e loot tables. A pesquisa desta fase
confirmou esse grafo conjunto e separou Forge GLM pela ADR-007.

## 2. Problema

Validar apenas o arquivo alterado, ou uma das três categorias isoladamente,
misturaria gerações e deixaria referências removidas, overrides revelados e
contexts sem prova. É necessário reconstruir o candidato completo sem tocar no
manager ativo e sem gerar loot.

## 3. Objetivos

- preparar conjuntamente `PREDICATES`, `ITEM_MODIFIERS` e `LOOT`;
- manter as três categorias públicas separadas e expandir escopo internamente;
- reconstruir todos os vencedores da stack lógica, nunca patch do map ativo;
- usar parsers, serializers, registries e validator reais;
- resolver referências cruzadas e produzir grafo explicável;
- produzir snapshot, deltas e `RESTORED_FROM_LOWER_PACK`;
- gerar artefato imutável, UUID, descartável e sem API de execução/publicação;
- preservar erros estruturados com tipo, recurso, pack, path JSON e causa;
- invalidar parse, referência, contexto, TOCTOU, timeout e limites;
- garantir exclusão global com preparação de functions;
- preservar manager, instâncias e comportamento ativos.

## 4. Não objetivos

Commit, swap do manager/`ReloadableServerResources`, rollback, sync, journal,
geração/regeneração de loot, alteração de containers/entidades/Lootr, execução
de GLM, integração completa KubeJS/LootJS/Silent Gear/Origins/Starcatcher,
recipes, tags, advancements, functions, Mixin, Access Transformer, reflection,
classes cliente, `/reload`, `reloadResources` ou listener ativo isolado.

## 5. Terminologia

`PreparedLootData` é o candidato passivo único. `requestedCategories` preserva
a intenção do administrador; `expandedCategories` é sempre o conjunto
`PREDICATES`, `ITEM_MODIFIERS`, `LOOT`. `LootDataKind` identifica os três tipos
internos sem criar categoria pública. `LootDependencyGraph` contém arestas
`LOOT_TABLE_REFERENCE`, `PREDICATE_REFERENCE`,
`ITEM_MODIFIER_REFERENCE`, `NESTED_CONDITION`, `NESTED_FUNCTION`,
`COMPOSITE_ENTRY` e `DYNAMIC_DROP_REFERENCE`. “Tecnicamente aplicável” significa
sem error/blocker para um commit futuro que ainda não existe.

## 6. Requisitos funcionais

- RF-010-1: `VanillaLootDataProvider` declara as três categorias e produz um
  único candidato conjunto ao selecionar qualquer uma.
- RF-010-2: toda preparação emite INFO `LOOT_CATEGORY_SCOPE_EXPANDED`.
- RF-010-3: descobrir vencedores pelas mesmas regras de diretório/ID do loader e
  registrar ID, logical path, pack, hash, tipo e stack.
- RF-010-4: reconstruir todos os três maps; nunca copiar o manager ativo.
- RF-010-5: predicates/modifiers aceitam objeto ou array composto e tabelas usam
  o deserializer Forge real.
- RF-010-6: construir resolver temporário unificado, adicionar
  `minecraft:empty` e executar `LootDataType.runValidation` em todo elemento.
- RF-010-7: qualquer falha de JSON, deserialização, referência, registry,
  contexto ou validação torna o artefato não aplicável.
- RF-010-8: wrappers preparados não expõem avaliação, aplicação nem geração.
- RF-010-9: grafo lista dependências, dependentes, missing, impacto, ciclos e
  caminhos; o validator real decide a severidade semântica de ciclos.
- RF-010-10: delta por tipo contém added, modified, removed e
  restored-from-lower-pack; diferenças adicionais são diagnósticas, não diff
  semântico completo de pools.
- RF-010-11: snapshot é recapturado antes da publicação; divergência em qualquer
  stack/hash gera `LOOT_RESOURCE_CHANGED_DURING_PREPARATION`.
- RF-010-12: timeout e limites configuráveis abrangem quantidade por tipo, bytes
  JSON totais e arestas.
- RF-010-13: somente uma preparação global; conflito retorna
  `PREPARATION_ALREADY_RUNNING`.
- RF-010-14: falha de conteúdo conclui em READY com artefato inválido; falha de
  infraestrutura/timeout conclui em FAILED_SAFE sem candidato parcial.
- RF-010-15: falha descarta o artefato anterior para impedir confusão entre
  pedido atual e candidato antigo; `discard` remove apenas o artefato.
- RF-010-16: comandos `prepare predicates`, `prepare item_modifiers`,
  `prepare loot`, `prepare changed`, `prepared` e `discard` exibem escopo,
  contagens, issues e não mutação.
- RF-010-17: `prepare changed` escolhe o candidato loot quando qualquer das três
  categorias mudou; se functions e loot mudaram juntos, rejeita a ambiguidade e
  exige seleção explícita.
- RF-010-18: GLM é reportado como `GLM_NOT_INCLUDED` conforme ADR-007.
- RF-010-19: padrões externos comprovados são reportados por
  `LOOT_EXTERNAL_PROVIDER_UNSUPPORTED`; não são declarados integrados.
- RF-010-20: `apply`, `reload` e `rollback` continuam recusando mutação.

## 7. Requisitos não funcionais

Java 17; API experimental 0.x; coleções defensivas; UUID/Clock/deadline
injetáveis quando útil; sem server retido em artefato; IO, hashing,
desserialização e validação no worker conforme o contrato executado por
`LootDataManager.reload`; serializers/callbacks modded que não respeitem esse
contrato são externos/não suportados; conclusão na server thread; timeout
cooperativo; logs contextualizados; mensagens administrativas paginadas/resumidas.

Configuração:

```toml
[preparation.loot]
prepare_timeout_seconds = 60
max_predicates = 100000
max_item_modifiers = 100000
max_loot_tables = 100000
max_total_json_bytes = 268435456
max_dependency_edges = 1000000
```

Todos os valores são positivos e limitados a ranges documentados.

## 8. Invariantes

O manager e seus maps nunca são escritos. O candidato nunca é registrado em
`ReloadableServerResources`, passado a `LootContext` de produção, exposto por API
de geração ou usado para modificar mundo/inventários. A preparação não executa
GLM. O snapshot é de uma única captura consistente. As categorias públicas não
ganham `LOOT_DATA`. Nenhum resultado parcial é tecnicamente aplicável.

## 9. Modelo de erros

`ValidationIssue` deve suportar severity, code, category, provider, data type,
resource ID, pack ID, logical path, JSON path, linha, coluna, mensagem, causa e
dependency path quando disponíveis.

Códigos: `LOOT_CATEGORY_SCOPE_EXPANDED`, `LOOT_JSON_SYNTAX_ERROR`,
`LOOT_DESERIALIZATION_ERROR`, `LOOT_UNKNOWN_ENTRY_TYPE`,
`LOOT_UNKNOWN_CONDITION_TYPE`, `LOOT_UNKNOWN_FUNCTION_TYPE`,
`LOOT_REGISTRY_REFERENCE_MISSING`, `LOOT_TABLE_REFERENCE_MISSING`,
`PREDICATE_REFERENCE_MISSING`, `ITEM_MODIFIER_REFERENCE_MISSING`,
`LOOT_RECURSIVE_REFERENCE`, `LOOT_CONTEXT_INCOMPATIBLE`,
`LOOT_RANDOM_SEQUENCE_INVALID`, `LOOT_VALIDATION_ERROR`,
`LOOT_EXTERNAL_PROVIDER_UNSUPPORTED`,
`LOOT_RESOURCE_CHANGED_DURING_PREPARATION`, `LOOT_PREPARATION_TIMEOUT`,
`LOOT_LIMIT_EXCEEDED`, `GLM_NOT_INCLUDED`,
`PREPARATION_ALREADY_RUNNING`.

Expansão é INFO. GLM omitido é WARNING. Loader externo sem contrato é BLOCKER.
Erros de conteúdo são ERROR. TOCTOU, limite e falha estrutural da operação são
BLOCKER.

## 10. Riscos

Callbacks de `LootTableLoadEvent` podem ter side effects próprios; serializers
modded podem não ser thread-safe; o parser vanilla aceita fallback de param set
em alguns casos; mensagens do validator são strings; stacks podem mudar durante
captura; mods podem reter instâncias de tabelas; GLM é outro estado ativo.

Mitigações: contexto controlado, pré-validação estruturada, validator real,
snapshot duplo, wrappers opacos, blocker para loaders externos, nenhuma
publicação e testes de identidade/não mutação.

## 11. Critérios de aceitação

1. research, ADR-007 e esta spec existem antes do código;
2. qualquer categoria expande para as três;
3. parsers/registries reais preparam os três tipos;
4. stack/overrides e restauração inferior são representados;
5. resolver/validator conjunto detecta referências e contexts inválidos;
6. grafo/delta/snapshot são imutáveis e explicáveis;
7. TOCTOU, timeout, limites e concorrência falham com segurança;
8. GLM/loaders externos são explicitamente reportados;
9. comandos exibem candidato e recusam mutações;
10. manager/instâncias/comportamento e outros managers permanecem inalterados;
11. nenhum `/reload`, `reloadResources`, cliente, Mixin, AT ou reflection é
   introduzido;
12. unit tests, GameTests, dedicated startup e clean build passam;
13. documentação final corresponde apenas ao executado.

## 12. Cenários de teste

Expansão por cada categoria; artefato/coleções/snapshot imutáveis; override de
pack e restauração inferior; predicate simples/composto/referenciado e inválido;
modifier simples/sequência/referência e inválido; tabela chest/entity/aninhada,
condition/modifier, recursive, missing, serializer/registry inválido, context e
random sequence; grafo/delta; TOCTOU; timeout/limites; concorrência; descarte;
falha segura; GLM separado; loader externo bloqueado.

GameTests com recursos reais comprovam candidato conjunto, erros por tipo,
referências, expansão, manager e instâncias ativos idênticos, candidato não
publicado, nenhum container/entidade/scoreboard/schedule/outro manager alterado,
comportamento ativo representativo igual e discard restrito ao artefato.

## 13. Decisões pendentes

Commit futuro deve escolher a referência a trocar, barreira para geração de loot
em andamento, destino de contexts já criados, política para containers ainda não
abertos, mods que retêm instâncias, sincronização, atomicidade com GLM, rollback
e eventual necessidade de AT/Mixin. Preparação GLM terá spec/provider próprios.
Integrações Silent Gear, Starcatcher, KubeJS/LootJS permanecem não comprovadas.

## 14. Relação com outras specs

Implementa a consequência da ADR-006, segue ADR-002/003/004/005 e ADR-007,
evolui Specs 001/002/005/006/007/008 e preserva Spec 009. O plano executável está
em `docs/plans/phase-three-b-tasks.md`.
