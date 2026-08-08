# Plano — Fase 4J: recipes KubeJS 2001

## Resultado

Stop gate acionado: `KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`.

## Gates executados

1. auditar documentação anterior e invariantes SDD;
2. obter POM, Gradle metadata, binários e sources dos builds `.16`, `.24` e `.26`;
3. obter Rhino Forge `2001.2.2-build.17` e Architectury Forge `9.1.12`;
4. comparar as classes do pipeline de scripts/recipes;
5. verificar estratégias A (API oficial) e B (clone tipado);
6. documentar o blocker e manter produção sem integração opcional carregada;
7. executar regressões proporcionais, sem simular acceptance de commit.

## Gate bloqueante

Não há registry local de eventos nem sandbox para bindings. O manager
secundário continua registrando no `ServerEvents` global por meio de
`ScriptType.SERVER`; `reload` limpa estado ativo. Nenhuma mutação de produção foi
autorizada após esse ponto.

## Trabalho não executado

Adapter, `PreparedKubeJsRecipes`, comandos, candidate final, commit, rollback,
GameTests, acceptance real, runner promotion e suporte a addons. Esses itens
dependem de uma seam upstream comprovada.
