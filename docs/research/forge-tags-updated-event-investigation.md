# TagsUpdatedEvent

Forge 47.4.10 define `TagsUpdatedEvent(RegistryAccess, boolean fromClient,
boolean integratedServerConnection)` e o event bus `MinecraftForge.EVENT_BUS`.
O reload normal publica o evento depois de atualizar registries. Fase 4E usa
`new TagsUpdatedEvent(server.registryAccess(), false, false)` depois de bind e
no rollback; falha de dispatch é rollbackável apenas no núcleo server-side.
