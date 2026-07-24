# Investigação do loader de predicates

## Fonte exata

Análise do source mapeado oficial Forge/Minecraft
`1.20.1-47.4.10`.

As assinaturas públicas foram conferidas também no índice [mappings.dev 1.20.1
— LootDataManager](https://mappings.dev/1.20.1/net/minecraft/world/level/storage/loot/LootDataManager.html).

## Loader e armazenamento

Predicates não possuem manager independente. `LootDataManager` prepara em uma
única operação os três `LootDataType`:

- `PREDICATE`, diretório `predicates`;
- `MODIFIER`, diretório `item_modifiers`;
- `TABLE`, diretório `loot_tables`.

Todos são publicados no único map privado `elements`, indexado por
`LootDataId<?>`, e no multimap `typeKeys`.

`LootDataType.PREDICATE` usa o Gson criado por
`Deserializers.createConditionSerializer()` e aceita objeto único ou array
composto. Serializers adicionais registrados por Forge/mods fazem parte desse
Gson.

### Fluxo de métodos observado

No source mapeado, `LootDataManager` implementa o listener de reload e o método
`reload(PreparationBarrier, ResourceManager, ProfilerFiller, ProfilerFiller,
Executor, Executor)` constrói mapas temporários por `LootDataType`. A fase de
aplicação chama `apply(Map<LootDataId<?>, ?>, ...)`, cria `LootDataResolver` e
`ValidationContext`, valida cada elemento e somente então atribui os campos
`elements` e `typeKeys` da instância ativa. `LootDataType.PREDICATE` fornece o
parser/validador de `LootItemCondition`; `LootDataType.MODIFIER` e `TABLE`
usam os serializers correspondentes no mesmo ciclo. Assim, não existe método
vanilla que carregue predicates em um manager independente.

## Validação e referências

Após desserializar todos os tipos, `LootDataManager.apply` cria um resolver sobre
o map candidato completo. Só então valida cada predicate, modifier e table.
`ConditionReference` resolve outro `LootDataId` do tipo predicate e detecta
referências ausentes/recursivas.

O acoplamento relevante não termina em predicate → predicate. Loot tables e item
modifiers incorporam conditions e são validados com seus próprios
`LootContextParamSet`. Preparar apenas predicates com `ALL_PARAMS` não prova que
uma definição alterada continua válida nos contextos específicos das tabelas e
modifiers que a usam.

Em runtime, `execute if predicate` obtém a definição pelo
`LootDataManager` ativo. `LootContext` também usa o resolver do manager ativo.

## Decisão

Resultado: `PREDICATES_COUPLED_TO_LOOT`.

É tecnicamente possível desserializar JSON isolado com
`LootDataType.PREDICATE`, mas isso não produz um candidato semanticamente
equivalente ao grafo validado pelo reload normal. Uma preparação confiável deve
reconstruir conjuntamente predicates, item modifiers e loot tables, incluindo
serializers Forge/modded e validação cruzada.

Na fase 3B (Spec 010):

- predicates continuam detectados e planejados;
- a preparação conjunta reconstrói predicates, item modifiers e loot tables no
  candidato, usando o resolver e serializers reais;
- não existe `PreparedPredicates` independente: predicates aparecem como parte
  de `PreparedLootData`;
- nenhum elemento do `LootDataManager` ativo é substituído.

Essa decisão não aprova commit. A publicação do candidato exige uma spec futura
que trate barreiras de thread, sincronização e rollback do manager ativo.
