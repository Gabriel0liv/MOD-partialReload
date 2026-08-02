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

## 4. Fases de implementação

### 4F-A — foundation e handshake (escopo atual)

Canal opcional versionado, capabilities mínimas (`HANDSHAKE_V1`), handshake
challenge-response, registry server-authoritative de sessões, timeout,
logout cleanup e codecs defensivos. Não publica tags ou recipes e não integra
com o commit da Spec 016.

### 4F-B — pré-codificação e quiescência

Fase futura para preparar payloads e definir quiescência de menus.

### 4F-C — publicação cliente e ACK

Fase futura para publicação de tags/recipes e ACK de geração.

### 4F-D — rollback compensatório e acceptance multi-client

Fase futura para falhas distribuídas, rollback e clientes reais.

Somente 4F-A está autorizada nesta rodada.

### 4F-R — commit server-side com refresh adiado

Extensão ortogonal e opt-in da Spec 016. O servidor pode confirmar tags +
recipes com players conectados, fechar seus menus e marcá-los stale até relog.
Não usa o canal 4F-A para dados, não exige handshake compatível e não implementa
payloads, ACK, recipe-book sync, quiescência distribuída ou rollback client-side.

## 5. Não objetivos

Não inclui KubeJS, viewers não testados, loot, resource reload, texturas,
Mixins amplos ou suporte sem cliente compatível.

## 6. Terminologia

`generationId`, `ClientCapabilities`, `ClientDigest`, `ClientSyncTransaction`,
`quiescence` e `compensating rollback`.

## 7. Requisitos funcionais

1. Cliente compatível deve completar handshake antes da mutação.
2. Packets candidatos devem ser pré-codificados.
3. Tags devem preceder recipes e recipe-book state.
4. Cada cliente deve enviar ACK com transaction/generation/digests.
5. Falha de ACK deve iniciar rollback compensatório ou desconectar o cliente.

## 8. Requisitos não funcionais

Código client-only deve ser side-safe; nenhum bloqueio da server thread; limite
de payload; protocolo versionado; sem `/reload`.

## 9. Invariantes

Nos futuros modos live-sync, nenhum cliente conectado pode permanecer em geração desconhecida; ACK antigo,
duplicado ou de outro jogador nunca conclui transação; server-only continua
funcionando quando não há jogadores.

O modo 4F-R é uma exceção explicitamente nomeada a essa futura garantia: clientes
presentes podem permanecer visualmente stale até relog, enquanto o servidor usa
exclusivamente a nova geração. Ele não pode ser apresentado como live sync.

## 10. Modelo de erros

`TAG_RECIPE_CLIENT_PROTOCOL_MISMATCH`, `TAG_RECIPE_CLIENT_HANDSHAKE_INVALID`,
`TAG_RECIPE_CLIENT_CAPABILITY_MISSING`, `TAG_RECIPE_CLIENT_READY_TIMEOUT`,
`TAG_RECIPE_CLIENT_ACK_INVALID`, `TAG_RECIPE_CLIENT_DIGEST_MISMATCH`,
`TAG_RECIPE_CLIENT_ROLLBACK_SYNC_FAILED`.

## 11. Riscos

Serializers ausentes no cliente, recipe viewers, menus abertos, desconexões e
efeitos externos de listeners podem impedir atomicidade distribuída.

## 12. Critérios de aceitação

Somente após o gate server-side da Fase 4E, cliente Forge real deve observar
B, recipe book e menus sem relog, confirmar ACK e observar A após rollback.

## 13. Cenários de teste

Handshake incompatível, ACK válido/inválido, timeout, digest mismatch,
join/disconnect race, menu aberto, recipe removida, rollback e dois clientes.

## 14. Decisões pendentes

A fundação 4F-A foi implementada e promovida por evidência funcional: canal client-optional, presence iniciada pelo cliente, discovery lazy/idempotente, nonce por conexão, cliente sem mod permitido, cliente com mod validado contra servidor sem mod, reconnects e timeout SILENT aprovados. As fases 4F-B/C/D permanecem bloqueadas até nova spec e acceptance.

## 15. Relação com outras specs

Depende de 015 e 016; preserva 009–014; antecede adapters de viewers.

## Estado da spec

`CLIENT_SYNC_PROTOCOL_FOUNDATION_IMPLEMENTED_REAL_CLIENT_HANDSHAKE_ACCEPTED`
— 4F-A foi implementada com canal opcional, handshake e sessões; payloads,
publicação e ACK transacional permanecem fora do escopo.

## Pronta para implementação

O safety gate server-side da Fase 4E-S foi promovido em 2026-07-27 após
GameTests, safety acceptance completa e runner consolidado aprovados. A
fundação 4F-A foi validada por testes de codecs, sessões, servidor de controlo Forge independente, quota funcional de cliente compatível, reconnects, SILENT, cliente ausente e acceptance composta. Fingerprints e assinaturas causais permanecem diagnostics; a validação funcional começa quando o protocolo Partial Reload produz o primeiro marker observável. Instabilidades Forge userdev que encerram a sessão antes dessa observação, sem sinais do produto e com cleanup físico aprovado, são infraestrutura transitória. 4F-B/C/D continuam bloqueadas até nova autorização.

A Fase 4F-R foi validada separadamente com cliente real: ela não usa este canal
para sincronizar dados. O servidor publica a geração nova, clientes presentes
ficam stale até relog e o login vanilla/Forge entrega o estado atual. O canal
continua com exatamente quatro mensagens e nenhum payload de tags/recipes ou
ACK foi adicionado.
