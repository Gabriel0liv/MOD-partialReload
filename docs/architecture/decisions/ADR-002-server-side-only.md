# ADR-002 — Server-side only

Status: Aceito — 2026-07-24

## Contexto

O Partial Reload administra o servidor; exigir mod cliente impediria adoção e não é necessário para a fase 1.

## Decisão

Não registrar conteúdo, não importar classes cliente e usar `displayTest="IGNORE_SERVER_VERSION"`. Reutilizar packets vanilla/Forge/mods. Se uma integração futura necessitar extensão cliente do Partial Reload, isso exige nova ADR e mudança explícita de produto.

## Consequências

Clientes vanilla/Forge sem o mod podem conectar. Integrações como Silent Gear ainda podem exigir o próprio mod alvo no cliente.

## Alternativas rejeitadas

Canal cliente próprio desde a fundação: não há caso de uso aprovado.
