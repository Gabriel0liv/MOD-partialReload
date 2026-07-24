# Spec 008 — Fundação da fase 1

## 1. Contexto

As specs 000–007 aprovam uma primeira entrega observacional que não toca managers de gameplay.

## 2. Problema

O MDK não possui identidade, domínio, scanner, comandos ou testes.

## 3. Objetivos

Entregar mod server-side only `0.1.0-SNAPSHOT`, config, provider estrutural, scanner, diff, planos e comandos read-only.

## 4. Não objetivos

Trocar managers; reload de qualquer categoria; KubeJS/Origins/Silent Gear runtime; migração/sync/rollback/journal; Mixin/AT; worldgen; GUI.

## 5. Terminologia

`VanillaDatapackProvider` é provider estrutural read-only; “compatible” nesta fase significa que pode escanear/planejar, não aplicar.

## 6. Requisitos funcionais

- RF-008-1: identidade `partialreload`, `Partial Reload`, grupo/pacote `com.gabriel0liv.partialreload`, autor Gabriel0liv e descrição aprovada.
- RF-008-2: classe principal mínima registra config e eventos, sem gameplay/cliente.
- RF-008-3: `displayTest="IGNORE_SERVER_VERSION"`.
- RF-008-4: packages e tipos definidos em 001–007.
- RF-008-5: config comum com permission=4, details=false, max=100000, timeout=60, unknown=true; ranges validados.
- RF-008-6: registry e provider não dependem de mods opcionais.
- RF-008-7: scan usa ResourceManager server-data, SHA-256, pack source e UNKNOWN.
- RF-008-8: baseline/last scan separados; primeiro scan estabelece baseline.
- RF-008-9: planos sempre `APPLY_NOT_IMPLEMENTED`.
- RF-008-10: comandos/stubs conforme 007.

## 7. Requisitos não funcionais

Java 17; build reproduzível; IO do scan em executor background fornecido pelo servidor; publicação do resultado na server thread; limites configuráveis; tipos imutáveis.

## 8. Invariantes

Zero imports client-side no código comum; zero registros de conteúdo; zero chamadas `reloadResources`, `/reload`, Mixins ou AT; nenhum scan é commit.

## 9. Modelo de erros

Hierarquia `PartialReloadException` cobre scan/limite/timeout; registry e state têm erros específicos; serviço preserva último resultado válido e entra `FAILED_SAFE`.

## 10. Riscos

JUnit com classes Minecraft; GameTest sem template; servidor de teste não encerrar. Mitigar com testes puros, template `empty`, logs/timeout e relato honesto.

## 11. Critérios de aceitação

1. template removido e identidade correta;
2. nenhum código cliente/gameplay;
3. JAR server-only e dedicated inicia;
4. comandos read-only registrados;
5. scanner classifica paths, gera SHA-256, reporta UNKNOWN;
6. diff e plano funcionam;
7. dynamic registries são restart;
8. operações mutáveis são recusadas;
9. unit tests e `clean build` passam;
10. GameTest/dedicated são executados quando viáveis e resultados registrados;
11. docs refletem implementação real.

Resultado em 2026-07-24: os onze critérios passaram. Foram executados 13 testes unitários e 3 GameTests sem falhas; `clean build` passou; o servidor dedicado Forge 47.4.10 chegou a `Done`. A execução inicial do GameTest detectou enumeração inválida com prefixo vazio, corrigida e revalidada com scan de 5.158 recursos.

## 12. Cenários de teste

Registry/duplicata; todos os paths; fingerprint; add/modify/remove/unchanged; agrupamento; blockers/restart; state machine; command tree/status/scan; startup dedicado.

## 13. Decisões pendentes

GameTest pode revelar limitações de harness; persistência não será adicionada sem nova decisão. Fase 2 precisa de spec própria.

## 14. Relação com outras specs

Implementa 000–007 e ADR-001–005. O plano executável está em `docs/plans/phase-one-tasks.md`.
