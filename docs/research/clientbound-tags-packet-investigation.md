# Clientbound tags packet — investigação

Em 1.20.1 o fluxo vanilla usa `ClientboundUpdateTagsPacket` construído a partir
de `TagNetworkSerialization`. O handler aplica tags no cliente na main thread.
Não há ACK transacional; por isso o packet isolado não satisfaz a Fase 4F.
