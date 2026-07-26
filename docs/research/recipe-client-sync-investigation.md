# Sync de recipes

`ClientboundUpdateRecipesPacket(Collection<Recipe<?>>)` é usado pelo
`PlayerList` no login/reload normal. Como o protocolo não oferece transação
distribuída nem ACK de rollback, Fase 4E recusa players conectados. A Fase 4F
deverá testar packet completo e recipe book com cliente real.
