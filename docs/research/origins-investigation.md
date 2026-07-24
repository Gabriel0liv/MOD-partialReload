# Investigação Origins Forge e Apoli

## Fontes

- [`EdwinMindcraft/origins-architectury`](https://github.com/EdwinMindcraft/origins-architectury), branch `1.20.x/forge`, commit `a35268e6a6a27612217fb6077bb9982c10be3313`;
- [`EdwinMindcraft/apoli`](https://github.com/EdwinMindcraft/apoli), branch `1.20.x/forge`, commit `f1c8f409327f23c59aeeeb058fd381fd506b125a`;
- [`EdwinMindcraft/calio`](https://github.com/EdwinMindcraft/calio), branch `1.20.x/forge`, commit `d54743e3a4f7b0318b0e7d8035084bd53365804d`.

O `mods.toml` de Origins exige Apoli `1.20.1-2.9+` e Calio `1.20.1-1.11+`. Releases 1.20.1 observadas incluem Origins `1.10.0.x` e Apoli `2.9.0.8`. O comportamento pesquisado é do port Forge, não foi inferido do Fabric.

## Definições e loaders

Origins registra com `CalioDynamicRegistryManager`:

- `origins` via `OriginLoader`, validado contra configured powers;
- `origin_layers` via `LayerLoader`, validado contra origins.

Apoli registra:

- `powers` via `PowerLoader`;
- `global_powers` via `GlobalPowerSetLoader`;
- validações cruzadas entre configured powers e global sets.

Calio implementa um único `PreparableReloadListener` para os registries: coleta JSON, recarrega registries candidatos, valida referências entre registries, troca registries e emite eventos. Ele possui packets próprios para sincronizar registries dinâmicos. Isolar apenas `OriginLoader` ou `PowerLoader` quebraria essa unidade.

## Estado vivo dos jogadores

Origins usa Forge capability `IOriginContainer`/`OriginContainer` para mapear layer → origin e persistir NBT. Apoli usa `IPowerContainer`/`PowerContainer`, que associa configured powers a sources e serializa estado por factory.

Powers `multiple` produzem subpowers; sources determinam concessão/remoção. Implementações de cooldown, resource e toggle armazenam valores mutáveis. Timers e outros powers também têm estado dependente do tipo/factory. Migrar por ID sem considerar factory antiga/nova, subpowers e source pode duplicar ou perder estado.

## Reload e sincronização normal

Após evento de registry dinâmico, `OriginContainer.onReload` revisita jogadores. Origins fornece `S2CSynchronizeOrigin`; Apoli fornece `S2CSynchronizePowerContainer`; Calio sincroniza os registries antes/ao redor do estado. Há acknowledgements e telas que aguardam powers.

Uma transação futura precisa ordenar:

1. powers/global sets/origins/layers candidatos;
2. validação cruzada;
3. política de migração por tipo e source;
4. troca de registries;
5. migração atômica dos containers;
6. packets de registry, origins e powers;
7. acknowledgement/verificação.

## Decisão

Na fase 1 esses arquivos são apenas classificados como `ORIGINS`, com suporte `PLANNED`. Não há dependency, Mixin, chamada a Calio ou migração.
