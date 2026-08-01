# ADR-046 — fundação opcional de handshake client-side

## Status

Aceito e promovido para a Fase 4F-A.

## Decisão

Adotar um canal Forge versionado e opcional (`partialreload:client_sync`) com
protocolo inteiro explícito. O login não depende da presença do canal: clientes
vanilla, não-Forge ou Forge sem o mod podem entrar. Quando o canal está presente,
o servidor abre uma sessão server-authoritative com challenge UUID e deadline;
o cliente responde somente com protocolo e capabilities explicitamente
suportadas. Apenas `HANDSHAKE_V1` é anunciada.

Sessões armazenam UUID do jogador, identidade da conexão, challenge, protocolo,
capabilities, estado e ticks; não retêm `MinecraftServer`, `ServerPlayer` ou
`Connection`. Challenge, conexão e jogador são validados em conjunto. Timeout,
logout e shutdown removem ou encerram sessões.

Codecs têm limites fixos, IDs conhecidos e rejeitam duplicatas, valores
desconhecidos e strings excessivas. Handlers usam `enqueueWork`; referências a
`net.minecraft.client` ficam exclusivamente no módulo client-only e são
despachadas side-safe.

## Consequências

O servidor permanece funcional sem o mod cliente e a Spec 016 continua
recusando qualquer jogador conectado. Esta decisão não implementa payload de
tags/recipes, recipe-book, ACK transacional, quiescência, digest, rollback ou
qualquer integração com `PartialReloadService`.

## Evidência de promoção

A promoção da 4F-A foi decidida por evidência funcional, não por reprodução causal exaustiva de instabilidades userdev. O servidor nunca envia o primeiro packet para cliente ausente; clientes sem canal chegam a ABSENT; clientes compatíveis completam PRESENCE_SENT/PRESENCE_RECEIVED/PENDING/HELLO/COMPATIBLE; clientes com mod entram em servidor Forge sem Partial Reload usando presença remota ausente; reconnects criam nova identidade, nonce e challenge. partialreload apply prepared continua bloqueado com jogadores conectados.

Fingerprints schema 2, authorization scope schema 1 e assinaturas causais permanecem ferramentas diagnósticas. Eles não são gate de promoção quando a sessão termina antes do primeiro marker funcional do protocolo Partial Reload, sem sinais de produto e com cleanup físico aprovado. 4F-B/C/D não foram implementadas.
