# Plano — Fase 4I advancements

1. Confirmar bytecode, mappings, ordem vanilla, persistência, rewards e packets.
2. Congelar o contrato na Spec 020 e ADR-049.
3. Implementar stack completa, parser real, validação, árvore e delta.
4. Implementar bridge mínima e geração ativa com guarda por referência.
5. Implementar snapshots dos jogadores, rebind, sync vanilla e rollback.
6. Integrar comandos, status, lifecycle e fault injection.
7. Cobrir o risco com testes unitários e pelo menos 32 GameTests novos.
8. Executar acceptance real, rollback, runner, clean build e inspeção do JAR.
9. Somente após todos os gates promover o provider e atualizar a documentação.

Gates são fail-closed e sequenciais. Não há rollback manual, packet próprio ou
transação conjunta com dependências nesta fase.

## Encerramento

Concluído em 2026-08-08. A regressão 4E-S `AFTER_FIRST_TAG_BIND` foi atribuída
a uma colisão de porta RCON depois de `Done`, antes de qualquer marker
transacional. O harness passou a observar bootstrap, `Done`, RCON, exit e
timeout em paralelo e permite no máximo três tentativas somente para
`INFRA_TRANSIENT` com cleanup integral. A acceptance 4E-S, 92 testes Java, 172
testes Python, 116/116 GameTests, a acceptance 4I e o runner de 12 suítes
passaram. O provider está `COMMIT_SUPPORTED`.
