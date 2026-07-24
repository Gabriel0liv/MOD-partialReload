# ADR-009 — Barreira de execução de functions

Status: Aceito — 2026-07-24

## Contexto

Trocar a library dentro da `ExecutionContext` criaria uma chain híbrida e faria
o resultado depender de entries já resolvidas.

## Decisão

Apply apenas cria uma transação pendente e entra `QUIESCING`. O swap ocorre no
primeiro `ServerTickEvent.END` elegível, prioridade `LOWEST`, na própria server
thread, depois de `tickChildren`, com `context == null`.

Apply vindo de function é recusado com
`FUNCTION_APPLY_FROM_ACTIVE_CHAIN_REJECTED`. Console, jogador e command block
podem solicitar, mas nunca publicam inline. Timeout cancela antes da mutação.

## Consequências

Chains antigas terminam integralmente antes do swap; chamadas futuras usam a
nova geração. Tick novo começa no tick seguinte. Não há cancelamento de chain,
sleep ou espera ocupada.

## Alternativas rejeitadas

Swap direto no comando; início do tick antes das functions; polling em worker;
reescrita de queued entries.

