# Fase 4D — tarefas

- [x] investigação de Ingredient, RecipeManager, conditions e serializers;
- [x] Spec 015 e ADR-023–026 antes do código;
- [x] view candidata e artefato composto;
- [x] pipeline conjunto no serviço e comandos;
- [x] grafo/delta cross-provider;
- [x] testes unitários e aceitação dedicada;
- [x] documentar limitações e preservar preparação isolada.
- [x] fechar impacto exato de recipes por tags alteradas, incluindo dependências
  aninhadas e hash JSON inalterado;
- [x] propagar recipes invalidadas e delta cross-provider sem artefato fictício;
- [x] usar classificação explícita/conservadora de segurança de serializers e
  conditions;
- [x] expor diagnóstico read-only de views candidata/ativa e comprovar conteúdo
  no harness dedicado;
- [x] executar GameTest com providers reais e verificar atomicidade da falha.

## Evidência dedicada

`python scripts/run-dedicated-tags-recipes-acceptance.py` alcançou `Done`,
preparou 735 tags e 1.175 recipes com snapshot compartilhado, revalidou 240
recipes por mudanças de tags, manteve fingerprints ativos, recusou apply e
encerrou via RCON com shutdown normal. O relatório está em
`build/reports/dedicated-tags-recipes-acceptance.json`.
