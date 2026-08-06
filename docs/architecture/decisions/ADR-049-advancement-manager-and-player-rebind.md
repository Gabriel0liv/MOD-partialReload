# ADR-049 — publicação do manager de advancements e rebind de jogadores

## Estado

Proposta, implementação em validação.

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
tipadas. Não usar reflexão, `setAccessible`, Unsafe, Mixin ou troca da instância
do manager.

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
