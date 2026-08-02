# Plano — Fase 4F-R: deferred client refresh

## Objetivo

Adicionar um modo opt-in de commit conjunto de tags + recipes com players
conectados, mantendo autoridade imediata do servidor e adiando a atualização
visual dos clientes até o relog.

## Invariantes

- `REJECT` e `/partialreload apply prepared` preservam o bloqueio atual.
- `DEFER_CLIENT_REFRESH_UNTIL_RELOGIN` só é selecionado pelo subcomando
  `deferred` e via API explícita.
- O pipeline transacional continua único e repete o preflight no safe point.
- A snapshot de players é capturada no safe point, após fechar/validar menus e
  antes da primeira mutação.
- Stale e geração só mudam depois de verificação e `SUCCESS`.
- Falha pre-mutation, rollback automático ou `DEGRADED` não produzem sucesso
  deferred nem aviso de relog.
- Login/logout removem stale; joins posteriores não são marcados.
- Nenhum packet, capability ou discriminator é adicionado.

## Entregas

1. Política imutável na transação e preflight mode-aware.
2. Fechamento fail-closed de menus no safe point.
3. `DeferredClientRefreshTracker` server-side e listeners de lifecycle.
4. Comando, feedback e status explícitos.
5. Testes unitários, GameTests e acceptance dedicada com relog.
6. Integração no runner somente após a acceptance isolada passar.

## Fora de escopo

Payloads, pre-encoding, chunks, digests, ACK, recipe-book/viewer live refresh,
quiescência distribuída, rollback de clientes e Fases 4F-B/C/D.

## Resultado

Concluído em 02/08/2026. Testes unitários Java e 155 testes Python passaram;
36/36 GameTests preservaram os 24 cenários anteriores e cobriram o modo
deferred, inclusive races de join/leave no safe point. A acceptance dedicada
com cliente real sem o mod principal comprovou menu fechado, autoridade
imediata do servidor, stale durante a sessão e atualização após relog. O runner
consolidado com oito suítes emitiu `ALL_ACCEPTANCE_PASSED` e o clean build foi
aprovado.
