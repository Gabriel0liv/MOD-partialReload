# ADR-025 — segurança de serializers

Serializers vanilla/Forge conhecidos podem ser preparados quando o parse não
materializa holders. Serializers modded desconhecidos ou que consultem membros
ativos geram blocker `RECIPE_SERIALIZER_CANDIDATE_TAGS_UNSUPPORTED`.
