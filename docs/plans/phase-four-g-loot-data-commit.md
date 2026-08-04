# Plano — Fase 4G: commit transacional de loot data

## Objetivo

Promover o bundle conjunto de predicates, item modifiers e loot tables de
prepare-only para publicação transacional server-side.

## Etapas

1. Confirmar o layout real do `LootDataManager` 1.20.1/Forge 47.4.10.
2. Criar bridge tipada, geração imutável e fingerprint determinístico.
3. Integrar preflight, safe point, publicação, verificação e rollback ao serviço.
4. Expor apply, rollback, status e fault injection administrativa.
5. Cobrir unidade, GameTests e acceptance dedicada comportamental.
6. Integrar o runner e promover compatibilidade somente após todos os gates.

## Restrições

Não trocar o manager, publicar parcialmente, tocar em GLMs, sincronizar cliente,
executar reload global ou regenerar conteúdo anterior.

## Resultado

Concluído em 2026-08-04. O provider foi promovido a `COMMIT_SUPPORTED` somente
depois de 60/60 GameTests, acceptance dedicada e runner consolidado aprovados.
O smoke Arclight não foi executado por ausência de runtime configurado.
