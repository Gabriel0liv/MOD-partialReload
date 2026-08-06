# ADR-048 — publicação de Global Loot Modifiers

Status: aceita e promovida

## Contexto confirmado

No Forge 47.4.10, `ForgeInternalHandler.onResourceReload` cria e guarda uma
instância estática de `LootModifierManager`. O getter é package-private. O
manager mantém um `Map<ResourceLocation, IGlobalLootModifier>
registeredLootModifiers`; `getAllLootMods()` retorna os valores desse mapa.

O reload Forge combina a stack de
`forge:loot_modifiers/global_loot_modifiers.json`, aplica `replace`, remove e
reinsere IDs repetidos, e decodifica cada JSON via
`IGlobalLootModifier.DIRECT_CODEC.parse(JsonOps.INSTANCE, json)`. Não existe
campo de registry ops no manager dessa versão.

## Decisão

Usar Access Transformer mínimo para tornar públicos o getter estático exato e o
campo exato, encapsulados por `LootModifierManagerBridge`. Não usar reflection,
`setAccessible`, Unsafe, Mixin nem substituir a instância.

A geração ativa é um mapa ordenado imutável. A identidade estrutural é provada
por IDs na mesma ordem e referências idênticas por ID; digest é só diagnóstico.

Mudança GLM isolada pode ser publicada isoladamente. Mudança simultânea em loot
data e GLM exige transação conjunta, publicando loot antes de GLM e revertendo
GLM antes de loot.

## Consequências

- jogadores conectados são permitidos e não há client sync;
- ordem de GLMs é estado funcional;
- o manager Forge preserva identidade;
- uma geração anterior é retida;
- incompatibilidade de layout falha antes da mutação;
- referências externas antigas não são atualizadas genericamente;
- a decisão é específica para Forge 47.4.10.

## Validação

O runtime confirmou preservação das identidades dos dois managers, ordem
funcional dos modifiers, commit/rollback isolado e rollback conjunto após fault
entre loot e GLM. Foram aprovados 84/84 GameTests, as acceptances GLM e conjunta
e a repetição isolada da acceptance 4G. O runner final aprovou 11 suítes com
`ALL_ACCEPTANCE_PASSED`; o clean build passou com 79 testes Java. A reutilização
de PID no Windows só deixa de ser mismatch quando o harness comprova exit
anterior, criação posterior e ausência de ownership, TCP, threads e artefatos
residuais. Com esses gates, o provider foi promovido para `COMMIT_SUPPORTED`.
