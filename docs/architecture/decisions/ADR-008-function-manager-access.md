# ADR-008 — Acesso ao manager de functions

Status: Aceito — 2026-07-24

## Contexto

`replaceLibrary` é público, mas captura da library anterior, detecção de chain,
supressão de load e preenchimento da candidata não são.

## Decisão

Escolher `ACCESS_TRANSFORMER`, estritamente estrutural e versionado para
Minecraft 1.20.1/Forge 47.4.10. São transformados somente os sete alvos listados
na investigação. Um bridge tipado concentra todo acesso. Não há wildcard,
reflection, Mixin, código cliente ou alteração do corpo de métodos.

O alvo foi conferido no `build/createSrgToMcp/output.srg` produzido pelos
mappings oficiais: os nomes são `library`, `context`, `ticking`, `postReload`,
`ServerFunctionManager$ExecutionContext`, `functions` e `tags`. O
`build.gradle` registra explicitamente
`accessTransformer = file('src/main/resources/META-INF/accesstransformer.cfg')`;
o mesmo caminho está presente no JAR final.

O self-check confirma versões, bridge, library, dispatcher e capacidade de
observação. Falha resulta em
`FUNCTION_COMMIT_DISABLED_INCOMPATIBLE_TARGET`; prepare continua funcional.

## Consequências

É possível construir uma `ServerFunctionLibrary` real, preservar a anterior e
suprimir `postReload`. O JAR fica acoplado ao alvo exato e precisa de novo teste
de compatibilidade para outra versão.

## Alternativas rejeitadas

`PUBLIC_API_ONLY` não captura/restaura estado. Mixin accessor adicionaria mais
infraestrutura sem benefício. Mixin comportamental é desnecessário. Reflection
genérica é proibida. `UNSAFE_TO_IMPLEMENT` não se aplica ao alvo comprovado.
