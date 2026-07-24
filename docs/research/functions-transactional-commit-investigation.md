# Investigação — commit transacional de functions

## Auditoria de implementação (HEAD 89aa7b0)

Esta investigação descreve o alvo técnico aprovado; ela não deve ser lida como
evidência de que o commit já está habilitado. A auditoria do worktree no HEAD
`89aa7b0` encontrou o projeto ainda em `PREPARE_ONLY`:

- `FunctionLibraryBridge` mantém todos os seis pontos de acesso como stubs que
  lançam `UnsupportedOperationException`;
- `FunctionCommitCompatibility.inspect` retorna sempre
  `FUNCTION_COMMIT_DISABLED_UNVERIFIED` para o alvo correto;
- `PartialReloadService` não possui coordenador/janela de safe point, captura
  de geração ou promoção de baseline;
- `PartialReloadStateMachine` ainda não autoriza `QUIESCING`, `COMMITTING`,
  `VERIFYING`, `SUCCESS`, `ROLLED_BACK` ou `DEGRADED`;
- `PartialReloadCommand` registra `apply`, `reload` e `rollback` como recusas
  genéricas; não há comandos `transaction`, `active functions` ou rollback de
  functions;
- o Access Transformer está presente em `src/main/resources` e o `minecraft`
  block declara `accessTransformer = file('src/main/resources/META-INF/accesstransformer.cfg')`;
  a verificação do artefato `build/libs/partialreload-0.1.0-SNAPSHOT.jar`
  encontrou `META-INF/accesstransformer.cfg`.

Consequentemente, as seções abaixo são contrato e evidência de pesquisa para a
Fase 3A, não critérios de conclusão. A implementação só poderá declarar
`FUNCTION_TRANSACTIONAL_COMMIT_IMPLEMENTED` depois que bridge, safe point,
verificação, rollback e testes de mutação real forem executados no alvo exato.

Alvo comprovado: Minecraft 1.20.1, mappings oficiais e Forge 47.4.10. Fonte
primária: `forge-1.20.1-47.4.10_mapped_official_1.20.1-sources.jar`.

## Verificação dos nomes e descritores

O arquivo gerado `build/createSrgToMcp/output.srg` foi consultado para evitar
confundir nomes oficiais com nomes SRG. Ele confirma os campos usados pelo
bridge: `ServerFunctionManager.library`, `context`, `ticking` e `postReload`,
além da classe aninhada `ServerFunctionManager$ExecutionContext`; e
`ServerFunctionLibrary.functions` e `tags`. Os descritores públicos relevantes
são `ServerFunctionManager.replaceLibrary(ServerFunctionLibrary)V`, o construtor
`ServerFunctionLibrary(int, CommandDispatcher)` e
`ServerFunctionLibrary.getFunctions()Ljava/util/Map;`.

O AT deliberadamente não transforma `tagsLoader`, `dispatcher` ou constantes:
eles não são escritos/observados pelo bridge. Não há wildcard nem descriptor de
cliente. A propriedade ForgeGradle 6 `accessTransformer` é necessária para o
ambiente de desenvolvimento; em produção o loader procura o caminho exato
`META-INF/accesstransformer.cfg`, que é preservado pelo `processResources`/JAR.

## Estado ativo e publicação

`ServerFunctionManager` contém `library`, `ticking`, `postReload` e o
`ExecutionContext` interno `context`. `replaceLibrary` é público e executa,
sincronamente:

1. `library = candidate`;
2. copia `candidate.getTag(minecraft:tick)` para `ticking`;
3. define `postReload = true`.

No início do próximo `ServerFunctionManager.tick`, `postReload` é zerado e a
tag `minecraft:load` da library então ativa é executada; depois `ticking` é
executado. Logo, `DO_NOT_RUN` exige zerar `postReload` no mesmo callback que
chama `replaceLibrary`, antes de devolver controle ao loop.

`ServerFunctionLibrary` tem construtor público com permission level e dispatcher,
mas seus maps `functions` e `tags` são privados e voláteis. A fase 2 já possui
os `CommandFunction` completos e tags resolvidas em ordem de inserção. Uma
library real pode ser criada sem tocar a ativa e receber cópias imutáveis desses
maps. O Forge preserva ordem de tags por `LinkedHashSet` e materializa
`List.copyOf`; a candidata deve fazer o mesmo.

## Chains

Em 1.20.1 não existem as classes top-level `ExecutionContext` e
`ExecutionCommandSource` das versões posteriores. O executor é
`ServerFunctionManager.ExecutionContext`, criado no início de `execute` e
zerado em `finally`.

Uma chain copia as entries da function inicial para `commandQueue`. Chamadas
aninhadas usam `FunctionEntry` com `CacheableFunction(CommandFunction)`, portanto
guardam a instância já resolvida. Uma chain iniciada antes da troca termina com
suas entries/instâncias antigas. Não há chain suspensa entre ticks: o loop é
síncrono e termina ou atinge `maxCommandChainLength` antes de `execute` retornar.

O comando apply dentro de uma function pode ser detectado porque `context !=
null`; ele deve ser recusado. Solicitações normais apenas enfileiram a transação.

## Safe point Forge

`MinecraftServer.tickServer` chama `ForgeEventFactory.onPreServerTick`, incrementa
o tick, executa `tickChildren`, status/autosave/métricas e finalmente
`onPostServerTick`. `tickChildren` executa `getFunctions().tick()` antes dos
levels. O commit usa `ServerTickEvent` fase `END`, prioridade `LOWEST`, na server
thread, e verifica novamente `context == null`. Assim:

- nunca troca no stack do comando solicitante;
- nenhuma chain é interrompida;
- tick functions candidatas começam somente no tick seguinte;
- não há sleep, busy wait ou worker publicando estado.

Tasks futuras podem executar depois do tick, mas não representam uma
`ExecutionContext` ativa. Elas resolverão a library vigente quando executarem.

## Functions, tags e schedules

`function` resolve o argumento ao executar o comando. Dentro de uma chain, a
resolução já compilada fica na entry antiga. Após o safe point, novas execuções
resolvem a candidata.

`ScheduleCommand` persiste `FunctionCallback(functionId)` ou
`FunctionTagCallback(tagId)`. Ao disparar, ambos consultam
`server.getFunctions()`; schedules anteriores usam a geração ativa naquele
momento. Function/tag removida segue ausência vanilla. A fila não precisa e não
deve ser migrada.

## Acesso e compatibilidade

API pública permite swap e consultas, mas não permite capturar a library,
detectar chain, suprimir load nem preencher uma candidata. O conjunto mínimo é
um Access Transformer estrutural para:

- `ServerFunctionManager.library`;
- `ServerFunctionManager.context`;
- `ServerFunctionManager.ticking`;
- `ServerFunctionManager.postReload`;
- classe interna `ServerFunctionManager$ExecutionContext`;
- `ServerFunctionLibrary.functions`;
- `ServerFunctionLibrary.tags`.

Não há reflection nem alteração comportamental. O bridge verifica versão
Minecraft/Forge, referências não nulas, dispatcher e acesso antes de habilitar
commit. Alvo divergente desabilita commit; preparação permanece disponível.

## Atomicidade e rollback

No safe point, a troca e a supressão são operações síncronas sem yield. A library
anterior e o baseline anterior são retidos antes da mutação. Verificação compara
identidade da library, functions, tags, `ticking`, `postReload`, schedules e
identidades dos managers laterais. Falha pós-swap restaura a library anterior
por `replaceLibrary`, suprime load novamente e verifica. Falha nessa restauração
é `DEGRADED`.

Atomicidade é garantida em relação ao manager vanilla e à server thread, não a
referências privadas que mods terceiros tenham armazenado para `CommandFunction`.
Essas integrações exigem contrato próprio.

## Execução dedicada headless

Em 24/07/2026, `runServer` foi iniciado por processo filho e alcançou
`Done (8.152s)! For help, type "help"`. O wrapper Gradle/Forge em execução
headless não encaminhou os comandos escritos em `stdin` ao console do
`DedicatedServer`: `partialreload status` não produziu uma linha de comando no
log e a sequência não pôde prosseguir. Não há evidência válida de apply/rollback
interativos nessa instância. O GameTestServer continua sendo a evidência
automatizada do commit e rollback reais. Os scripts
`scripts/run-dedicated-function-acceptance.py` e
`scripts/run-dedicated-function-acceptance.ps1` registram a tentativa e devem
ser executados num terminal com stdin de console Forge disponível.
