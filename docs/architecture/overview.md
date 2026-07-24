# Visão de arquitetura

```text
commands
   |
PartialReloadService ---- PartialReloadStateMachine
   |          |
   |          +---- activeReference / latestScan / lastChangeSet / lastPlan
   |
ProviderRegistry
   |
ReloadProvider (fase 1: VanillaDatapackProvider)
   |
ResourceScanner -> ResourceSnapshot -> ChangeDetector -> ReloadPlanner
```

## Fronteiras

- `api`: categorias, provider SPI, compatibilidade e contextos experimentais;
- `resource`: descriptors, fingerprints, snapshots e leitura;
- `change`: diff puro;
- `plan`: agregação read-only e blockers;
- `validation`: issues/reports compartilháveis;
- `core`: registry, estado e orquestração;
- `command`: adaptação Brigadier;
- `config`: validação ForgeConfigSpec.

Categoria, provider, recurso e transação/plano não são intercambiáveis. O único boundary Minecraft da fase 1 é o `ResourceManager` entregue no `ScanContext`.

## Fluxo fase 1

1. comando obtém `ResourceManager` atual sem retê-lo;
2. serviço entra SCANNING e delega scan no executor de background;
3. resultado completo retorna à server thread;
4. primeiro resultado estabelece `activeReference`; todo resultado atualiza `latestScan`;
5. diff é sempre `activeReference` versus `latestScan`;
6. planning agrega contribuições conservadoras e entra READY;
7. nenhuma API de commit é exposta.

## Extensão futura

Providers futuros poderão adicionar contratos `PreparedReload`, quiesce, commit, sync, verify e rollback apenas quando as respectivas specs existirem. Esses métodos não pertencem à SPI inicial para não prometer capacidade inexistente.
