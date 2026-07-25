# Serializers modded e tags

Os serializers efetivamente observados no ambiente Forge userdev são os
serializers vanilla/Forge usados pelas 1.175 recipes da aceitação anterior.
Eles foram classificados como `TAG_INDEPENDENT_DURING_PARSE` ou
`STORES_TAG_KEY_ONLY` quando o JSON contém ingredientes de tag. Não há
evidência suficiente para declarar qualquer serializer arbitrário de mod como
seguro; serializer desconhecido que dependa de tag candidata gera
`RECIPE_SERIALIZER_CANDIDATE_TAGS_UNSUPPORTED`.
