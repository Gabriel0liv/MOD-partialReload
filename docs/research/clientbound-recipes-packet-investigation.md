# Clientbound recipes packet — investigação

`ClientboundUpdateRecipesPacket` carrega recipes serializadas por serializer/type.
Um serializer ausente no cliente é uma falha de decodificação; o protocolo
vanilla não retorna ACK de aplicação.
