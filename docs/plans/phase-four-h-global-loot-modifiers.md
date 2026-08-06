# Plano — Fase 4H: Global Loot Modifiers

Status: concluída e promovida.

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

Evidência final: 79 testes Java, 161 testes Python e 84/84 GameTests, com 24/24
da 4G e 24/24 da 4H. A acceptance 4G foi repetida isoladamente após o hardening
de PID reuse e passou sem mismatch ou residual. O runner consolidado aprovou
as 11 suítes com `ALL_ACCEPTANCE_PASSED`, seguido de clean build e inspeção do
JAR. O provider GLM foi então promovido para `COMMIT_SUPPORTED`. O smoke
Arclight não foi executado por ausência de runtime configurado.

Não fazem parte do plano live sync, LootJS, KubeJS loot, reload global ou regeneração retroativa.
