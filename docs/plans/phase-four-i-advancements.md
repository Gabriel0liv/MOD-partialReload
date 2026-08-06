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
