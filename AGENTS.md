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

A fase 3B implementa preparação conjunta e passiva de predicates, item
modifiers e loot tables conforme `docs/specs/010-loot-data-prepare.md`.
`VanillaLootDataProvider` reconstrói o grafo completo com parsers/registries
reais, mas nunca publica o candidato no `LootDataManager`. GLM permanece num
provider separado planejado conforme ADR-007. A preparação de functions da
Spec 009 continua disponível.

`apply`, `reload` e `rollback` devem continuar falhando de modo explícito e
seguro. Não exponha candidatos por API executável, não chame
`ServerFunctionManager.replaceLibrary` e não escreva os maps privados do
`LootDataManager` antes de specs de commit.
