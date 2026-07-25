# Spec 014 — preparação de tags

## 1. Contexto
Tags gerais são carregadas por datapacks e vinculadas aos registries durante reload. Function tags já pertencem ao pipeline de functions.

## 2. Problema
O `/reload` muta holders, dispara sincronização e reconstrói managers. Tags precisam de candidato completo sem binding ativo.

## 3. Objetivos
Reconstruir tags por registry, respeitar pilha de packs, `replace`, entries obrigatórias/opcionais, nested tags, remoções representadas, grafo, ciclos, validação, delta, TOCTOU e artefato imutável.

## 4. Não objetivos
Não bindar `Holder`, chamar `Registry.bindTags`, disparar `TagsUpdatedEvent`, sincronizar clientes, atualizar recipes/loot/advancements, usar `/reload`, Mixin, AT novo ou reflection.

## 5. Terminologia
`registryPath`, `PreparedTag`, `PreparedRegistryTags`, entry, nested tag, contribuição de pack, candidato e binding ativo.

## 6. Requisitos funcionais
RF-014-1 enumerar `tags/**/*.json`; RF-014-2 excluir `tags/functions`; RF-014-3 agrupar registry; RF-014-4 mesclar stacks; RF-014-5 aplicar replace/values/remove; RF-014-6 resolver referências e ciclos; RF-014-7 validar registries/elementos; RF-014-8 gerar grafo/delta; RF-014-9 detectar TOCTOU; RF-014-10 recusar apply.

## 7. Requisitos não funcionais
Java 17, server-side, async para leitura/parsing, limites configuráveis, SHA-256, estruturas imutáveis e nenhuma chamada de binding.

## 8. Invariantes
Bindings ativos nunca mudam; function tags nunca entram; candidato depende do snapshot; pack ordering é preservado; required missing/ciclos inválidos tornam o artefato inaplicável; optional missing é informativo; managers laterais permanecem iguais.

## 9. Modelo de erros
`TAG_JSON_SYNTAX_ERROR`, `TAG_ENTRY_INVALID`, `TAG_ELEMENT_REFERENCE_MISSING`, `TAG_REFERENCE_CYCLE`, `TAG_REGISTRY_UNKNOWN`, `TAG_REGISTRY_UNSUPPORTED`, `TAG_FUNCTION_DOMAIN_DELEGATED`, `TAG_LIMIT_EXCEEDED`, `TAG_PREPARATION_TIMEOUT` e `TAG_BINDING_NOT_PERFORMED`.

## 10. Riscos
Formato Forge `remove` pode variar; registries modded podem não existir no `RegistryAccess`; `ResourceManager` não expõe binding candidato; tags dinâmicas dependem de lifecycle próprio.

## 11. Critérios de aceitação
Item/block/fluid/entity tags válidas são agrupadas; function tags são delegadas; replace e stack são preservados; nested/ciclos são diagnosticados; required missing invalida; artefato é imutável; apply é recusado; active registries/holders não mudam.

## 12. Cenários de teste
Tag simples, nested, empty, optional/required, replace true/false, pack inferior, ciclo, registry desconhecido, dynamic registry, delta, timeout, limite, concorrência, discard e apply recusado.

## 13. Decisões pendentes
Validar semântica Forge `remove` em source da versão alvo e definir composição conjunta tags+recipes.

## 14. Relação com outras specs
Evolui 003, 004, 006, 007 e 012; preserva 009–013; prepara futura composição tags+recipes.

