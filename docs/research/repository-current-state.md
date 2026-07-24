# Estado inicial do repositório

## Escopo e evidência

Inspeção realizada em 2026-07-24 no commit `badf3ad`, antes de qualquer edição.

## Resultado

O repositório era o Forge MDK quase intacto:

- `gradle.properties`: Minecraft `1.20.1`, Forge `47.4.10`, mappings `official` `1.20.1`;
- toolchain de compilação Java 17;
- `mod_id=examplemod`, `mod_name=Example Mod`, versão `1.0.0`, grupo/pacote `com.example.examplemod`;
- `ExampleMod` registrava bloco, `BlockItem`, item com comida e creative tab;
- havia listeners de setup comum, creative tab e server start apenas para logs de exemplo;
- uma classe interna cliente referenciava `net.minecraft.client.Minecraft` e `FMLClientSetupEvent`;
- `Config.java` era a configuração demonstrativa do MDK;
- `mods.toml` usava o display test padrão e continha comentários/template;
- não havia `docs/`, `AGENTS.md`, testes, providers, scanner, comandos ou integração;
- worktree inicialmente limpo.

## Problemas para o produto

O template violava server-side only, registrava gameplay sem relação com o produto, não possuía contratos SDD e não distinguia scan, diff, plano e commit. Nenhuma chamada de partial reload existia.

## Decisão

Remover integralmente o exemplo e substituir por uma entrada mínima dedicada ao registro de config e comandos. A identidade alvo é `partialreload` `0.1.0-SNAPSHOT`, grupo/pacote `com.gabriel0liv.partialreload`.
