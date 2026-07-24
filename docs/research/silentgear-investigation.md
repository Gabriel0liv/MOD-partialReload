# Investigação Silent Gear

## Fonte e versão

Fonte [`SilentChaos512/Silent-Gear`](https://github.com/SilentChaos512/Silent-Gear), branch `1.20.x`, commit `8af569e03ad29e0f8aeeea6bef616bd29d1f4a97`. O branch é a fonte 1.20.x; distribuições 1.20.1 conhecidas pertencem à série 3.6.x (por exemplo 3.6.3). O MineDev não fixa um JAR/versão, portanto nenhuma compatibilidade binária específica é afirmada.

## Loaders

- `MaterialManager` implementa `ResourceManagerReloadListener`, lê `silentgear_materials/**/*.json`, limpa um mapa estático sincronizado e desserializa material/pack;
- `TraitManager` faz o mesmo para `silentgear_traits/**/*.json`;
- parts e outros dados possuem managers adicionais;
- as classes são listeners diretos (aplicação no executor de reload), sem fase candidata/commit exposta.

O MineDev contém dados sob namespaces `silentgear`, `silentcompat`, `silentgems` e vários namespaces de mods. Materiais podem referenciar traits fornecidos por Silent Gear e addons. A validade depende de serializers registrados por código e do conjunto de addons.

## Loot injection

`SgLoot` mapeia tabelas vanilla para `silentgear:inject/<namespace>/<path>` e, em `LootTableLoadEvent`, injeta referências aos pools. Isso combina loader vanilla de loot e hook Forge do Silent Gear; recarregar apenas os JSON de injeção não prova que tabelas já montadas serão atualizadas.

## Sincronização e cliente

Silent Gear registra `SyncMaterialsPacket`, `SyncTraitsPacket` e packets de crafting/parts no canal próprio. Os dois primeiros são login packets; o código observado não oferece um broadcast público de hot reload equivalente. O cliente retém dados antigos por ID ao receber novas definições.

Ao contrário do Partial Reload, Silent Gear é client+server; uma futura integração de seus dados provavelmente exige clientes com Silent Gear e seus packets, embora não exija o Partial Reload no cliente.

## Itens existentes e caches

`GearData` persiste construção, traits e estatísticas em `SGear_Data` NBT. Estatísticas são recalculadas explicitamente e podem estar locked. Alterar material/trait não reescreve automaticamente todos os itens existentes; inventários, entidades, containers e chunks descarregados tornam varredura global insegura. Caches de render/material existem também no cliente.

## Decisão

Fase 1 apenas classifica materiais/traits como `SILENTGEAR` e mantém suporte `PLANNED`. Nenhum manager, packet, cache ou item é tocado. Uma spec futura deve fixar versões de Silent Gear/Silent Lib/SilentCompat/SilentGems, definir política para NBT existente e provar sincronização.
