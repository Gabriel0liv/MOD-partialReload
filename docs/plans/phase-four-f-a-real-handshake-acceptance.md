# Fase 4F-A — acceptance real do handshake opcional

## Escopo

Esta rodada valida somente o canal opcional e o handshake challenge-response
da fundação 4F-A. Nenhum payload de tags/recipes, ACK transacional, quiescência
ou integração com o commit da Spec 016 é exercitado.

## Cenários

1. `compatible`: cliente Forge com o mod responde `HANDSHAKE_V1`.
2. `reconnect`: a segunda conexão do mesmo nome recebe nova identidade e novo
   challenge; o estado client-side é resetado no logout.
3. `silent_timeout`: cliente com o helper em modo SILENT permanece PENDING e o
   servidor marca `TIMED_OUT` após o prazo.
4. `absent_client_allowed`: cliente Forge sem Partial Reload entra e é marcado
   `ABSENT`, sem handshake.
5. `connected_commit_still_blocked`: uma sessão COMPATIBLE não altera a regra
   server-authoritative da Spec 016; jogadores conectados continuam recusando
   o commit.

## Marcadores

O servidor registra `CLIENT_HANDSHAKE_SERVER_ABSENT`, `PENDING`, `COMPATIBLE`,
`INCOMPATIBLE`, `TIMED_OUT` e `DISCONNECTED`. O cliente registra os eventos de
recepção, envio, aceite, compatibilidade e reset. Cada linha contém somente
identidade do jogador, identidade da conexão, challenge, protocolo,
capabilities e erro tipado quando aplicável.

## Timeouts e ownership

O harness escolhe portas livres de servidor e RCON, usa somente loopback e
mantém um PID para cada processo Gradle iniciado. Em timeout, somente a árvore
descendente desse PID é encerrada. Não há enumeração ou encerramento global de
processos Java. O prazo do protocolo é configurado em 40 ticks para a prova.

## Cleanup

Clientes são encerrados e o logout é observado antes do `stop` via RCON. O
servidor é aguardado até shutdown normal; apenas a árvore owned é terminada em
timeout. Reader threads são fechadas, as portas são verificadas livres e o
diretório descartável `run/handshake-acceptance` é removido somente após a
árvore owned terminar. `server.properties` e configuração do mod são
restaurados byte a byte quando existirem.

## Promoção

Promover 4F-A exige os cinco cenários aprovados, correlação de challenge e
connection identity no cliente compatível e no reconnect, timeout SILENT,
cliente sem canal permitido, commit da Spec 016 ainda bloqueado, JAR principal
sem o helper client-only e regressão 4E-S sem falhas. 4F-B/C/D permanecem fora
do escopo até esta evidência.

## Resultado final

A Fase 4F-A foi encerrada por evidência funcional. A acceptance final usa duas sub-runs independentes: servidor com Partial Reload para compatible, reconnect, silent_timeout, absent_client_allowed e connected_commit_still_blocked; servidor Forge independente sem Partial Reload para server_absent_client_mod_allowed e server_absent_client_mod_reconnect. A validação funcional começa no primeiro marker observável do protocolo Partial Reload. Aborts de Forge userdev antes dessa observação, sem channel rejection, unknown custom packet ou marker de erro do produto, e com cleanup físico aprovado, são classificados como infraestrutura transitória e não autorizam comportamento funcional.

Gates finais aprovados: quota compatível 5 válidos em até 10 launches, reconnect ausente, reconnect compatível, cliente com mod em servidor sem mod, reconnect em servidor sem mod, SILENT, commit conectado ainda bloqueado, composite final, Java tests, 24/24 GameTests, runner consolidado, clean build e inspeção do JAR. 4F-B/C/D permanecem fora do escopo: sem payloads de tags/recipes, pre-encoding, digests, ACK, recipe book, quiescência ou rollback distribuído.
