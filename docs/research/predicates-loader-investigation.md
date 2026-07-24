# Investigação do loader de predicates

## Fonte exata

Análise do source mapeado oficial Forge/Minecraft
`1.20.1-47.4.10`.

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

Na fase 2:

- predicates continuam detectados e planejados;
- preparação retorna blocker `PREDICATES_COUPLED_TO_LOOT`;
- não existe `PreparedPredicates`;
- o roadmap move a preparação para a futura fase conjunta de loot.
