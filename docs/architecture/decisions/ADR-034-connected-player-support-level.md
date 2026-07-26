# ADR-034 — nível de players conectados

O nível desta fase é `SERVER_ONLY_NO_PLAYERS`. A presença de qualquer player
conectado produz `TAG_RECIPE_COMMIT_PLAYERS_CONNECTED`. Não há suporte a sync,
recipe book, menus ou rollback de packets até aceitação com cliente real.
