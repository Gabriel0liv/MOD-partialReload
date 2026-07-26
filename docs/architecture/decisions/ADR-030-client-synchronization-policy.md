# ADR-030 — sincronização de clientes

Fase 4E usa `SERVER_ONLY_NO_PLAYERS`. Com qualquer player conectado o commit é
recusado antes da mutação. Packets vanilla (`ClientboundUpdateTagsPacket` e
`ClientboundUpdateRecipesPacket`) serão investigados para a Fase 4F; nenhuma
sincronização é anunciada nesta fase.
