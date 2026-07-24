# Spec 000 — Escopo do produto

## 1. Contexto

Servidores Forge grandes usam datapacks e loaders de mods acoplados ao `/reload` global.

## 2. Problema

O pipeline global é caro e pode deixar indisponibilidade ou falhas; operadores não conseguem inspecionar impacto por categoria antes de aplicar.

## 3. Objetivos

- oferecer scan, diff, validação, planejamento e futuramente commit transacional;
- apresentar categorias amplas e compreensíveis;
- preservar segurança de dedicated server e operação server-side only.

## 4. Não objetivos

Hot reload de código, JARs, Mixins, serializers, registries estáticos, conteúdo registrado, startup scripts, worldgen arbitrário ou GUI.

## 5. Terminologia

Categoria é a visão pública; provider implementa um loader; recurso é um arquivo/ID; transação agrega mudanças consistentes. `RESTART_REQUIRED` indica alteração deliberadamente não aplicável.

## 6. Requisitos funcionais

- RF-000-1: disponibilizar comandos administrativos de inspeção.
- RF-000-2: providers opcionais devem contribuir para categorias sem criar comandos por loader.
- RF-000-3: cada plano deve declarar suporte, risco, blockers e dependências.
- RF-000-4: fase 1 é read-only e deve rejeitar apply/reload/rollback.

## 7. Requisitos não funcionais

Java 17, Forge 47.4.10, imutabilidade adequada, erros tipados, logs contextuais, ausência de dependência cliente.

## 8. Invariantes

- Nunca chamar `MinecraftServer.reloadResources` nem `/reload`.
- Scan não é commit.
- Ausência de integração opcional não impede startup.
- API 0.x não é estável.

## 9. Modelo de erros

Erros são classificados em input inválido, incompatibilidade, limite/timeout, falha de IO/scan, validação e estado inválido. A fase 1 termina em `FAILED_SAFE` sem mutação de gameplay.

## 10. Riscos

Contratos ocultos entre listeners; packs que mudam durante scan; addons; falsa promessa de rollback; dados persistentes fora dos managers.

## 11. Critérios de aceitação

Identidade real, startup dedicado, comandos read-only, scanner/fingerprint/diff/plano, perigos como restart, testes e documentação alinhada.

## 12. Cenários de teste

Template removido; mod ausente no cliente; arquivo adicionado/modificado/removido; worldgen bloqueado; comando apply recusado.

## 13. Decisões pendentes

Contrato de commit da fase 2, journal persistente, permission API e empacotamento de integrações.

## 14. Relação com outras specs

Spec raiz; detalhada por 001–008 e ADR-001–005.
