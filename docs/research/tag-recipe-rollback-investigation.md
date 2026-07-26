# Rollback de tags + recipes

Rollback seguro exige restaurar a coleção completa de recipes, mapas completos
de bindings, invalidar Ingredients e redisparar `TagsUpdatedEvent`. A geração
anterior não pode ser reconstruída de JSON porque serializers e holders podem
ter estado runtime.
