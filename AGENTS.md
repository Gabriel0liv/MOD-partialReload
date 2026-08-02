# Partial Reload — regras para agentes

Este repositório usa Spec-Driven Development (SDD). Leia `docs/specs/` e os ADRs relacionados antes de alterar comportamento. Nenhuma feature pode ser implementada sem requisitos, invariantes, erros, critérios de aceitação e cenários de teste documentados.

## Limites permanentes

- Minecraft 1.20.1, Forge 47.4.10, Java 17 e mappings oficiais.
- O núcleo é server-authoritative e o protocolo comum pode existir em ambos os lados; um módulo client-only opcional permanece isolado. O servidor funciona sem o mod no cliente, clientes sem canal compatível entram normalmente, e classes `net.minecraft.client` nunca podem ser carregadas no dedicated server.
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

A Fase 4F-A foi promovida: a fundação opcional de protocolo e handshake está aceita por evidência funcional. Clientes sem canal compatível entram normalmente, clientes com mod entram em servidor Forge sem Partial Reload, reconnects e SILENT foram validados. A Fase 4F-R acrescenta o opt-in `SERVER_COMMIT_DEFERRED_CLIENT_REFRESH`: o servidor publica tags + recipes imediatamente com jogadores conectados, fecha todos os menus antes da primeira mutação e marca aquelas sessões stale até relog. O comando normal continua `SERVER_ONLY_NO_PLAYERS`. As fases 4F-B/C/D permanecem fora do escopo: nenhum payload, recipe-book live refresh, ACK transacional, quiescência ou rollback compensatório é permitido.

A Fase 4E implementa o caminho server-only padrão de commit conjunto de tags + recipes
em servidor dedicado sem jogadores conectados. O safe point usa o fim
do tick da server thread; bindings usam `Registry.bindTags`, recipes usam
`RecipeManager.replaceRecipes`, `Ingredient.invalidateAll()` é chamado e
`TagsUpdatedEvent` é emitido. Uma geração conjunta anterior fica retida em
memória para rollback único. O safety gate 4E-S foi aprovado após cobertura de
preflight stale, fault injection, player race, registries vazios/não suportados
e regressão dedicada completa.

A Fase 4A prepara recipes vanilla/Forge read-only. A Fase 4B possui apenas
pesquisa e snapshot/classificação segura de KubeJS até existir um runtime Forge
1.20.1 exato e uma API de staging isolada; nunca execute `ServerScriptManager`
ou `RecipesKubeEvent` no runtime ativo.
O estado oficial da integração é `KUBEJS_RECIPE_PREPARATION_BLOCKED`, não
pending acceptance.

A Fase 4C prepara tags gerais em `PreparedTags` read-only. `tags/functions` são
delegadas ao provider de functions; nunca chame `Registry.bindTags`, eventos de
tags, packets ou sincronização de clientes.

`reload` e qualquer apply de loot devem falhar de modo explícito e seguro. Não
escreva os maps privados do `LootDataManager`.

A Fase 4D prepara `PreparedTagsAndRecipes` somente quando tags e recipes são
solicitadas juntas. O artefato usa um snapshot compartilhado e
`PreparedTagsResolutionView`; a publicação é limitada ao contrato server-only
da Fase 4E. Serializers ou conditions que dependam de holders ativos falham
fechado.

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

Os harnesses dedicados possuem ownership explícito do wrapper Gradle iniciado:
usam RCON, registram o PID, aguardam shutdown gracioso e, somente em timeout,
encerram a árvore própria com `taskkill /PID <pid> /T`. Nunca encerrar `java.exe`
globalmente. Locks stale só podem ser removidos no mundo descartável de userdev
depois de confirmar que nenhum processo da aceitação está vivo. O runner
consolidado é `python scripts/run-all-acceptance.py`.

Atualização 2026-07-27: o safety gate server-side da Fase 4E-S foi aceito com
GameTests completos, safety dedicated completa e runner consolidado aprovado.
A fundação opcional 4F-A de protocolo/handshake foi aceita por evidência funcional sem payload de tags/recipes. A validação funcional começa no primeiro marker observável do protocolo Partial Reload; aborts Forge userdev antes dessa observação, sem sinais de produto e com cleanup físico aprovado, são infraestrutura transitória. 4F-B/C/D permanecem fora do escopo.

Atualização 2026-08-02: a Fase 4F-R foi aceita com 36/36 GameTests, acceptance
dedicada com cliente Forge real sem o mod principal e runner consolidado com
`ALL_ACCEPTANCE_PASSED`. No modo deferred, o servidor muda imediatamente e o
cliente conectado permanece visualmente stale até o relog; não existe packet
novo, live sync, ACK ou rollback distribuído.
