# ADR-034 — nível de players conectados

## Decisão

`SERVER_ONLY_NO_PLAYERS` permanece o nível padrão. A presença de qualquer player
no comando normal produz `TAG_RECIPE_COMMIT_PLAYERS_CONNECTED`.

A Fase 4F-R acrescenta `SERVER_COMMIT_DEFERRED_CLIENT_REFRESH`, disponível
somente por opt-in explícito. Nesse nível o safe point recaptura os players,
fecha e valida todos os menus antes da primeira mutação, publica a geração no
servidor e, somente após `SUCCESS`, marca aquelas sessões
`STALE_UNTIL_RELOGIN`. Login e logout removem o marker.

## Consequências

O servidor passa a aplicar imediatamente a lógica nova. Clientes presentes
continuam online, mas recipe book, JEI/REI e informação visual podem permanecer
antigos até relog. O modo independe do mod e do handshake no cliente.

Não há packet novo, live sync, ACK, atomicidade distribuída, rollback client-side
ou atualização instantânea de viewers. Rollback manual com players permanece
bloqueado. Falha ou rollback automático bem-sucedido não marca clientes stale.
