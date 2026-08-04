# Plano — Fase 4H: Global Loot Modifiers

Status: implementação concluída; promoção bloqueada no gate consolidado final.

1. Confirmar bytecode e mappings do Forge 47.4.10.
2. Endurecer a guarda TOCTOU da transação 4G por igualdade exata.
3. Adicionar categoria/scanning e preparação ordenada GLM.
4. Implementar bridge mínima com Access Transformer exato.
5. Implementar geração, commit e rollback GLM.
6. Detectar mudanças simultâneas e exigir artefato/transação conjunta.
7. Implementar rollback conjunto e fault injection fail-closed.
8. Integrar comandos, status e aliases.
9. Cobrir unit tests, GameTests e acceptances dedicadas.
10. Executar gates, inspecionar JAR e promover documentação somente com todos os resultados aprovados.

Evidência: 78 testes Java e 84/84 GameTests (24 da 4H), acceptances GLM e
conjunta aprovadas e uma execução do runner com 11 suítes aprovada. Depois da
tentativa de promoção, a repetição final parou na acceptance 4G por
`identity_mismatches=[29860]`, apesar de cleanup global sem resíduos. O contrato
fail-closed mantém a promoção pendente. O smoke Arclight não foi executado por
ausência de runtime configurado.

Não fazem parte do plano live sync, LootJS, KubeJS loot, reload global ou regeneração retroativa.
