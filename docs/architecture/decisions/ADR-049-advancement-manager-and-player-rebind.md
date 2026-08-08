# ADR-049 — publicação do manager de advancements e rebind de jogadores

## Estado

Aceita e implementada.

## Contexto confirmado

No runtime alvo, `ServerAdvancementManager.apply` constrói uma
`AdvancementList` completa e substitui apenas o campo privado `advancements`.
A API pública não expõe publicação. `PlayerAdvancements.reload` relê o arquivo,
recalcula progresso contra a definição nova, chama triggers automáticos e
registra listeners. `flushDirty` e `setSelectedTab` implementam a sincronização
vanilla.

## Decisão

Usar Access Transformer mínimo para o campo exato do manager e para o estado
estritamente necessário de `PlayerAdvancements`, encapsulado em bridges
tipadas. Para campos Forge-patched que não são transformados de forma confiável
no classpath do `javac`, o bridge usa somente reflexão sobre o contrato público
criado pelo AT (`getField`), com owner e tipo exatos. Não usa `setAccessible`,
Unsafe, Mixin ou troca da instância do manager.

A preparação usa `Advancement.Builder.fromJson` e o mesmo LootDataManager e
contexto Forge da instância ativa. O safe point salva cada jogador pelo caminho
vanilla, captura bytes e estado lógico, para listeners, publica a lista completa,
recarrega, remapeia a aba e força reset vanilla.

Advancements sem critérios e com rewards são bloqueados porque
`checkForAutomaticTriggers` concede a reward durante todo reload, o que não é
rollback-safe. Falha posterior à publicação restaura manager e jogadores;
rollback manual é rejeitado porque apagaria progresso legítimo posterior.

## Consequências

- jogadores permanecem conectados e clientes sem Partial Reload funcionam;
- IDs removidos somem e listeners antigos são retirados;
- progresso compatível e datas persistidas são preservados;
- exatamente os packets vanilla de advancement são usados;
- mudanças concorrentes nas dependências exigem novo scan/preparo sequencial;
- a decisão é específica para Minecraft 1.20.1/Forge 47.4.10.

## Evidência final

O gate final aprovou 116/116 GameTests (32/32 da 4I), acceptance dedicada de
commit e rollback com clientes vanilla, runner consolidado com 12 suítes e
clean build. O provider foi promovido para `COMMIT_SUPPORTED` somente depois
dessas evidências.
