# Spec 017 — sincronização transacional com clientes

## 1. Contexto

Tags e recipes possuem commit server-only na Fase 4E. Esta spec define o
protocolo futuro para clientes conectados.

## 2. Problema

Packets vanilla não fornecem ACK transacional, quiescência de menus nem
confirmação de rollback.

## 3. Objetivos

Definir handshake versionado, capabilities, pre-encoding, digests, ACK,
quiescência, recipe-book sync e rollback compensatório.

## 4. Não objetivos

Não inclui KubeJS, viewers não testados, loot, resource reload, texturas,
Mixins amplos ou suporte sem cliente compatível.

## 5. Terminologia

`generationId`, `ClientCapabilities`, `ClientDigest`, `ClientSyncTransaction`,
`quiescence` e `compensating rollback`.

## 6. Requisitos funcionais

1. Cliente compatível deve completar handshake antes da mutação.
2. Packets candidatos devem ser pré-codificados.
3. Tags devem preceder recipes e recipe-book state.
4. Cada cliente deve enviar ACK com transaction/generation/digests.
5. Falha de ACK deve iniciar rollback compensatório ou desconectar o cliente.

## 7. Requisitos não funcionais

Código client-only deve ser side-safe; nenhum bloqueio da server thread; limite
de payload; protocolo versionado; sem `/reload`.

## 8. Invariantes

Nenhum cliente conectado pode permanecer em geração desconhecida; ACK antigo,
duplicado ou de outro jogador nunca conclui transação; server-only continua
funcionando quando não há jogadores.

## 9. Modelo de erros

`TAG_RECIPE_CLIENT_CAPABILITY_MISSING`, `TAG_RECIPE_CLIENT_ACK_INVALID`,
`TAG_RECIPE_CLIENT_DIGEST_MISMATCH`, `TAG_RECIPE_CLIENT_READY_TIMEOUT`,
`TAG_RECIPE_CLIENT_ROLLBACK_SYNC_FAILED`.

## 10. Riscos

Serializers ausentes no cliente, recipe viewers, menus abertos, desconexões e
efeitos externos de listeners podem impedir atomicidade distribuída.

## 11. Critérios de aceitação

Somente após o gate server-side da Fase 4E, cliente Forge real deve observar
B, recipe book e menus sem relog, confirmar ACK e observar A após rollback.

## 12. Cenários de teste

Handshake incompatível, ACK válido/inválido, timeout, digest mismatch,
join/disconnect race, menu aberto, recipe removida, rollback e dois clientes.

## 13. Decisões pendentes

Implementação permanece bloqueada até fault injection, player race, registry
vazio, registries não suportados e `DEGRADED` terem aceitação dedicada real.

## 14. Relação com outras specs

Depende de 015 e 016; preserva 009–014; antecede adapters de viewers.

## Estado da spec

`CLIENT_SYNC_READY_FOR_IMPLEMENTATION` — nenhum código de networking foi
introduzido nesta tarefa; a implementação permanece fora do escopo desta
rodada.

## Pronta para implementação

O safety gate server-side da Fase 4E-S foi promovido em 2026-07-27 após
GameTests, safety acceptance completa e runner consolidado aprovados. Esta
spec está pronta para implementação; networking, packets e sincronização
client-side continuam fora desta rodada.
