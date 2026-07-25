# Partial Reload — regras para agentes

Este repositório usa Spec-Driven Development (SDD). Leia `docs/specs/` e os ADRs relacionados antes de alterar comportamento. Nenhuma feature pode ser implementada sem requisitos, invariantes, erros, critérios de aceitação e cenários de teste documentados.

## Limites permanentes

- Minecraft 1.20.1, Forge 47.4.10, Java 17 e mappings oficiais.
- O mod é server-side only: não referencie classes cliente em código comum e não registre conteúdo de gameplay.
- Nunca implemente partial reload chamando `MinecraftServer.reloadResources`, executando `/reload`, ou disparando um listener isolado sem contrato comprovado.
- Java, JARs, Mixins, serializers, tipos registrados por código, registries estáticos, `startup_scripts`, worldgen arbitrário, dimensões e biomas são `RESTART_REQUIRED` até prova e spec em contrário.
- Categoria pública, provider, recurso e transação são níveis distintos.
- Integrações opcionais devem ser tipadas e versionadas quando possível. Reflection, Mixin ou Access Transformer exigem classe/versão exatas, ADR, teste de compatibilidade e fallback.
- Não modifique MineDev nem checkouts externos. Não faça commit ou push sem pedido explícito.

## Fluxo obrigatório

1. Confirmar a spec aplicável e as decisões arquiteturais.
2. Atualizar ou criar a spec antes do código quando o comportamento mudar.
3. Implementar apenas o escopo aprovado.
4. Adicionar testes proporcionais ao risco.
5. Executar os testes e registrar apenas resultados reais.
6. Atualizar a documentação quando a implementação divergir do plano.

## Qualidade

- Prefira tipos imutáveis, erros tipados e dependências injetáveis.
- Não capture `MinecraftServer` em objetos longevos sem necessidade.
- IO pesado não roda na server thread; ownership de threads deve estar documentado.
- Não engula exceções. Logs precisam indicar operação, categoria e provider quando aplicável.
- Preserve a diferença entre snapshot de referência ativo, último scan e plano. Scan não é commit.
- Em `0.x`, APIs são experimentais e não devem ser apresentadas como estáveis.

## Fase atual

A fase 3A implementa commit transacional somente de functions vanilla conforme
`docs/specs/011-functions-transactional-commit.md`. O bridge usa Access
Transformer com nomes SRG exatos do Forge 47.4.10, publica apenas no safe point
`ServerTickEvent.END` e aplica `DO_NOT_RUN` para load functions. Uma geração
anterior é retida em memória para rollback único.

A fase 3B continua somente preparação conjunta e passiva de predicates, item
modifiers e loot tables; `VanillaLootDataProvider` nunca publica no
`LootDataManager`. GLM permanece separado conforme ADR-007.

`reload` e qualquer apply de loot devem falhar de modo explícito e seguro. Não
escreva os maps privados do `LootDataManager`.

A Fase 4A prepara recipes com serializers reais, mas continua PREPARE_ONLY:
não trocar `RecipeManager`, sincronizar clientes ou executar KubeJS.

## Aceitação dedicada

O console stdin do `runServer` via wrapper Gradle não é um transporte confiável.
Use `python scripts/run-dedicated-function-acceptance.py` (ou o wrapper
PowerShell), que configura RCON efêmero em loopback, restaura
`run/server.properties` e as fixtures em `run/world/datapacks` e grava os
relatórios em `build/reports/`. Nunca registrar a senha RCON. A aceitação
aprovada deve observar `Done`, `SUCCESS`, `ROLLED_BACK` e shutdown normal;
falhas devem preservar o log para diagnóstico.
`partialreload debug manager_fingerprints` é userdev-only, read-only e serve
somente para aceitação; não criar equivalente de produção sem nova spec.
