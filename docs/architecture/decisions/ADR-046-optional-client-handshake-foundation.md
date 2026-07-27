# ADR-046 — fundação opcional de handshake client-side

## Status

Aceito para a Fase 4F-A.

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
